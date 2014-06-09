 /*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.provider.Telephony.Sms;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.common.contacts.DataUsageStatUpdater;
import com.android.common.userhappiness.UserHappinessSignals;
import com.android.mms.ContentRestrictionException;
import com.android.mms.ExceedMessageSizeException;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.ResolutionException;
import com.android.mms.UnsupportContentTypeException;
import com.android.mms.model.ImageModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.TextModel;
import com.android.mms.transaction.MessageSender;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.transaction.SmsMessageSender;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.android.mms.ui.SlideshowEditor;
import com.android.mms.util.DraftCache;
import com.android.mms.util.Recycler;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.widget.MmsWidgetProvider;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendReq;

/**
 * Contains all state related to a message being edited by the user.
 */
public class WorkingMessage {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;

    // Public intents
    public static final String ACTION_SENDING_SMS = "android.intent.action.SENDING_SMS";

    // Intent extras
    public static final String EXTRA_SMS_MESSAGE = "android.mms.extra.MESSAGE";
    public static final String EXTRA_SMS_RECIPIENTS = "android.mms.extra.RECIPIENTS";
    public static final String EXTRA_SMS_THREAD_ID = "android.mms.extra.THREAD_ID";

    // Database access stuff
    private final Activity mActivity;
    private final ContentResolver mContentResolver;

    // States that can require us to save or send a message as MMS.
    private static final int RECIPIENTS_REQUIRE_MMS = (1 << 0);     // 1
    private static final int HAS_SUBJECT = (1 << 1);                // 2
    private static final int HAS_ATTACHMENT = (1 << 2);             // 4
    private static final int LENGTH_REQUIRES_MMS = (1 << 3);        // 8
    private static final int FORCE_MMS = (1 << 4);                  // 16
    private static final int MULTIPLE_RECIPIENTS = (1 << 5);        // 32

    // A bitmap of the above indicating different properties of the message;
    // any bit set will require the message to be sent via MMS.
    private int mMmsState;

    // Errors from setAttachment()
    public static final int OK = 0;
    public static final int UNKNOWN_ERROR = -1;
    public static final int MESSAGE_SIZE_EXCEEDED = -2;
    public static final int UNSUPPORTED_TYPE = -3;
    public static final int IMAGE_TOO_LARGE = -4;

    // Attachment types
    public static final int TEXT = 0;
    public static final int IMAGE = 1;
    public static final int VIDEO = 2;
    public static final int AUDIO = 3;
    public static final int SLIDESHOW = 4;

    // Current attachment type of the message; one of the above values.
    private int mAttachmentType;

    // Conversation this message is targeting.
    private Conversation mConversation;

    // Text of the message.
    private CharSequence mText;
    // Slideshow for this message, if applicable.  If it's a simple attachment,
    // i.e. not SLIDESHOW, it will contain only one slide.
    private SlideshowModel mSlideshow;
    // Data URI of an MMS message if we have had to save it.
    private Uri mMessageUri;
    // MMS subject line for this message
    private CharSequence mSubject;

    // Set to true if this message has been discarded.
    private boolean mDiscarded = false;

    // Track whether we have drafts
    private volatile boolean mHasMmsDraft;
    private volatile boolean mHasSmsDraft;

    // Cached value of mms enabled flag
    private static boolean sMmsEnabled = MmsConfig.getMmsEnabled();

    // Our callback interface
    private final MessageStatusListener mStatusListener;
    private List<String> mWorkingRecipients;

    // Message sizes in Outbox
    private static final String[] MMS_OUTBOX_PROJECTION = {
        Mms._ID,            // 0
        Mms.MESSAGE_SIZE    // 1
    };

    private static final int MMS_MESSAGE_SIZE_INDEX  = 1;

    /**
     * Callback interface for communicating important state changes back to
     * ComposeMessageActivity.
     */
    public interface MessageStatusListener {
        /**
         * Called when the protocol for sending the message changes from SMS
         * to MMS, and vice versa.
         *
         * @param mms If true, it changed to MMS.  If false, to SMS.
         */
        void onProtocolChanged(boolean mms);

        /**
         * Called when an attachment on the message has changed.
         */
        void onAttachmentChanged();

        /**
         * Called just before the process of sending a message.
         */
        void onPreMessageSent();

        /**
         * Called once the process of sending a message, triggered by
         * {@link send} has completed. This doesn't mean the send succeeded,
         * just that it has been dispatched to the network.
         */
        void onMessageSent();

        /**
         * Called if there are too many unsent messages in the queue and we're not allowing
         * any more Mms's to be sent.
         */
        void onMaxPendingMessagesReached();

        /**
         * Called if there's an attachment error while resizing the images just before sending.
         */
        void onAttachmentError(int error);
    }

    private WorkingMessage(ComposeMessageActivity activity) {
        mActivity = activity;
        mContentResolver = mActivity.getContentResolver();
        mStatusListener = activity;
        mAttachmentType = TEXT;
        mText = "";
    }

    /**
     * Creates a new working message.
     */
    public static WorkingMessage createEmpty(ComposeMessageActivity activity) {
        // Make a new empty working message.
        WorkingMessage msg = new WorkingMessage(activity);
        return msg;
    }

    /**
     * Create a new WorkingMessage from the specified data URI, which typically
     * contains an MMS message.
     */
    public static WorkingMessage load(ComposeMessageActivity activity, Uri uri) {
        // If the message is not already in the draft box, move it there.
        if (!uri.toString().startsWith(Mms.Draft.CONTENT_URI.toString())) {
            PduPersister persister = PduPersister.getPduPersister(activity);
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                LogTag.debug("load: moving %s to drafts", uri);
            }
            try {
                uri = persister.move(uri, Mms.Draft.CONTENT_URI);
            } catch (MmsException e) {
                LogTag.error("Can't move %s to drafts", uri);
                return null;
            }
        }

        WorkingMessage msg = new WorkingMessage(activity);
        if (msg.loadFromUri(uri)) {
            msg.mHasMmsDraft = true;
            return msg;
        }

        return null;
    }

    private void correctAttachmentState(boolean showToast) {
        int slideCount = mSlideshow.size();

        // If we get an empty slideshow, tear down all MMS
        // state and discard the unnecessary message Uri.
        if (slideCount == 0) {
            removeAttachment(false);
        } else if (slideCount > 1) {
            mAttachmentType = SLIDESHOW;
        } else {
            SlideModel slide = mSlideshow.get(0);
            if (slide.hasImage()) {
                mAttachmentType = IMAGE;
            } else if (slide.hasVideo()) {
                mAttachmentType = VIDEO;
            } else if (slide.hasAudio()) {
                mAttachmentType = AUDIO;
            }
        }

        updateState(HAS_ATTACHMENT, hasAttachment(), showToast);
    }

    private boolean loadFromUri(Uri uri) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) LogTag.debug("loadFromUri %s", uri);
        try {
            mSlideshow = SlideshowModel.createFromMessageUri(mActivity, uri);
        } catch (MmsException e) {
            LogTag.error("Couldn't load URI %s", uri);
            return false;
        }

        mMessageUri = uri;

        // Make sure all our state is as expected.
        syncTextFromSlideshow();
        correctAttachmentState(false);

        return true;
    }

    /**
     * Load the draft message for the specified conversation, or a new empty message if
     * none exists.
     */
    public static WorkingMessage loadDraft(ComposeMessageActivity activity,
                                           final Conversation conv,
                                           final Runnable onDraftLoaded) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) LogTag.debug("loadDraft %s", conv);

        final WorkingMessage msg = createEmpty(activity);
        if (conv.getThreadId() <= 0) {
            if (onDraftLoaded != null) {
                onDraftLoaded.run();
            }
            return msg;
        }

        new AsyncTask<Void, Void, Pair<String, String>>() {

            // Return a Pair where:
            //    first - non-empty String representing the text of an SMS draft
            //    second - non-null String representing the text of an MMS subject
            @Override
            protected Pair<String, String> doInBackground(Void... none) {
                // Look for an SMS draft first.
                String draftText = msg.readDraftSmsMessage(conv);
                String subject = null;

                if (TextUtils.isEmpty(draftText)) {
                    // No SMS draft so look for an MMS draft.
                    StringBuilder sb = new StringBuilder();
                    Uri uri = readDraftMmsMessage(msg.mActivity, conv, sb);
                    if (uri != null) {
                        if (msg.loadFromUri(uri)) {
                            // If there was an MMS message, readDraftMmsMessage
                            // will put the subject in our supplied StringBuilder.
                            subject = sb.toString();
                        }
                    }
                }
                Pair<String, String> result = new Pair<String, String>(draftText, subject);
                return result;
            }

            @Override
            protected void onPostExecute(Pair<String, String> result) {
                if (!TextUtils.isEmpty(result.first)) {
                    msg.mHasSmsDraft = true;
                    msg.setText(result.first);
                }
                if (result.second != null) {
                    msg.mHasMmsDraft = true;
                    if (!TextUtils.isEmpty(result.second)) {
                        msg.setSubject(result.second, false);
                    }
                }
                if (onDraftLoaded != null) {
                    onDraftLoaded.run();
                }
            }
        }.execute();

        return msg;
    }

    /**
     * Sets the text of the message to the specified CharSequence.
     */
    public void setText(CharSequence s) {
        mText = s;
    }

    /**
     * Returns the current message text.
     */
    public CharSequence getText() {
        return mText;
    }

    /**
     * @return True if the message has any text. A message with just whitespace is not considered
     * to have text.
     */
    public boolean hasText() {
        return mText != null && TextUtils.getTrimmedLength(mText) > 0;
    }

    public void removeAttachment(boolean notify) {
        removeThumbnailsFromCache(mSlideshow);
        mAttachmentType = TEXT;
        mSlideshow = null;
        if (mMessageUri != null) {
            asyncDelete(mMessageUri, null, null);
            mMessageUri = null;
        }
        // mark this message as no longer having an attachment
        updateState(HAS_ATTACHMENT, false, notify);
        if (notify) {
            // Tell ComposeMessageActivity (or other listener) that the attachment has changed.
            // In the case of ComposeMessageActivity, it will remove its attachment panel because
            // this working message no longer has an attachment.
            mStatusListener.onAttachmentChanged();
        }
    }

    public static void removeThumbnailsFromCache(SlideshowModel slideshow) {
        if (slideshow != null) {
            ThumbnailManager thumbnailManager = MmsApp.getApplication().getThumbnailManager();
            boolean removedSomething = false;
            Iterator<SlideModel> iterator = slideshow.iterator();
            while (iterator.hasNext()) {
                SlideModel slideModel = iterator.next();
                if (slideModel.hasImage()) {
                    thumbnailManager.removeThumbnail(slideModel.getImage().getUri());
                    removedSomething = true;
                } else if (slideModel.hasVideo()) {
                    thumbnailManager.removeThumbnail(slideModel.getVideo().getUri());
                    removedSomething = true;
                }
            }
            if (removedSomething) {
                // HACK: the keys to the thumbnail cache are the part uris, such as mms/part/3
                // Because the part table doesn't have auto-increment ids, the part ids are reused
                // when a message or thread is deleted. For now, we're clearing the whole thumbnail
                // cache so we don't retrieve stale images when part ids are reused. This will be
                // fixed in the next release in the mms provider.
                MmsApp.getApplication().getThumbnailManager().clearBackingStore();
            }
        }
    }

    /**
     * Adds an attachment to the message, replacing an old one if it existed.
     * @param type Type of this attachment, such as {@link IMAGE}
     * @param dataUri Uri containing the attachment data (or null for {@link TEXT})
     * @param append true if we should add the attachment to a new slide
     * @return An error code such as {@link UNKNOWN_ERROR} or {@link OK} if successful
     */
    public int setAttachment(int type, Uri dataUri, boolean append) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("setAttachment type=%d uri %s", type, dataUri);
        }
        int result = OK;
        SlideshowEditor slideShowEditor = new SlideshowEditor(mActivity, mSlideshow);

        // Special case for deleting a slideshow. When ComposeMessageActivity gets told to
        // remove an attachment (search for AttachmentEditor.MSG_REMOVE_ATTACHMENT), it calls
        // this function setAttachment with a type of TEXT and a null uri. Basically, it's turning
        // the working message from an MMS back to a simple SMS. The various attachment types
        // use slide[0] as a special case. The call to ensureSlideshow below makes sure there's
        // a slide zero. In the case of an already attached slideshow, ensureSlideshow will do
        // nothing and the slideshow will remain such that if a user adds a slideshow again, they'll
        // see their old slideshow they previously deleted. Here we really delete the slideshow.
        if (type == TEXT && mAttachmentType == SLIDESHOW && mSlideshow != null && dataUri == null
                && !append) {
            slideShowEditor.removeAllSlides();
        }

        // Make sure mSlideshow is set up and has a slide.
        ensureSlideshow();      // mSlideshow can be null before this call, won't be afterwards
        slideShowEditor.setSlideshow(mSlideshow);

        // Change the attachment
        result = append ? appendMedia(type, dataUri, slideShowEditor)
                : changeMedia(type, dataUri, slideShowEditor);

        // If we were successful, update mAttachmentType and notify
        // the listener than there was a change.
        if (result == OK) {
            mAttachmentType = type;
        }
        correctAttachmentState(true);   // this can remove the slideshow if there are no attachments

        if (mSlideshow != null && type == IMAGE) {
            // Prime the image's cache; helps A LOT when the image is coming from the network
            // (e.g. Picasa album). See b/5445690.
            int numSlides = mSlideshow.size();
            if (numSlides > 0) {
                ImageModel imgModel = mSlideshow.get(numSlides - 1).getImage();
                if (imgModel != null) {
                    cancelThumbnailLoading();
                    imgModel.loadThumbnailBitmap(null);
                }
            }
        }

        mStatusListener.onAttachmentChanged();  // have to call whether succeeded or failed,
                                                // because a replace that fails, removes the slide

        if (!append && mAttachmentType == TEXT && type == TEXT) {
            int[] params = SmsMessage.calculateLength(getText(), false);
            /* SmsMessage.calculateLength returns an int[4] with:
             *   int[0] being the number of SMS's required,
             *   int[1] the number of code units used,
             *   int[2] is the number of code units remaining until the next message.
             *   int[3] is the encoding type that should be used for the message.
             */
            int smsSegmentCount = params[0];

            if (!MmsConfig.getMultipartSmsEnabled()) {
                // The provider doesn't support multi-part sms's so as soon as the user types
                // an sms longer than one segment, we have to turn the message into an mms.
                setLengthRequiresMms(smsSegmentCount > 1, false);
            } else {
                int threshold = MmsConfig.getSmsToMmsTextThreshold();
                setLengthRequiresMms(threshold > 0 && smsSegmentCount > threshold, false);
            }
        }
        return result;
    }

    /**
     * Returns true if this message contains anything worth saving.
     */
    public boolean isWorthSaving() {
        // If it actually contains anything, it's of course not empty.
        if (hasText() || hasSubject() || hasAttachment() || hasSlideshow()) {
            return true;
        }

        // When saveAsMms() has been called, we set FORCE_MMS to represent
        // sort of an "invisible attachment" so that the message isn't thrown
        // away when we are shipping it off to other activities.
        if (isFakeMmsForDraft()) {
            return true;
        }

        return false;
    }

    private void cancelThumbnailLoading() {
        int numSlides = mSlideshow != null ? mSlideshow.size() : 0;
        if (numSlides > 0) {
            ImageModel imgModel = mSlideshow.get(numSlides - 1).getImage();
            if (imgModel != null) {
                imgModel.cancelThumbnailLoading();
            }
        }
    }

    /**
     * Returns true if FORCE_MMS is set.
     * When saveAsMms() has been called, we set FORCE_MMS to represent
     * sort of an "invisible attachment" so that the message isn't thrown
     * away when we are shipping it off to other activities.
     */
    public boolean isFakeMmsForDraft() {
        return (mMmsState & FORCE_MMS) > 0;
    }

    /**
     * Makes sure mSlideshow is set up.
     */
    private void ensureSlideshow() {
        if (mSlideshow != null) {
            return;
        }

        SlideshowModel slideshow = SlideshowModel.createNew(mActivity);
        SlideModel slide = new SlideModel(slideshow);
        slideshow.add(slide);

        mSlideshow = slideshow;
    }

    /**
     * Change the message's attachment to the data in the specified Uri.
     * Used only for single-slide ("attachment mode") messages. If the attachment fails to
     * attach, restore the slide to its original state.
     */
    private int changeMedia(int type, Uri uri, SlideshowEditor slideShowEditor) {
        SlideModel originalSlide = mSlideshow.get(0);
        if (originalSlide != null) {
            slideShowEditor.removeSlide(0);     // remove the original slide
        }
        slideShowEditor.addNewSlide(0);
        SlideModel slide = mSlideshow.get(0);   // get the new empty slide
        int result = OK;

        if (slide == null) {
            Log.w(LogTag.TAG, "[WorkingMessage] changeMedia: no slides!");
            return result;
        }

        // Clear the attachment type since we removed all the attachments. If this isn't cleared
        // and the slide.add fails (for instance, a selected video could be too big), we'll be
        // left in a state where we think we have an attachment, but it's been removed from the
        // slide.
        mAttachmentType = TEXT;

        // If we're changing to text, just bail out.
        if (type == TEXT) {
            return result;
        }

        result = internalChangeMedia(type, uri, 0, slideShowEditor);
        if (result != OK) {
            slideShowEditor.removeSlide(0);             // remove the failed slide
            if (originalSlide != null) {
                slideShowEditor.addSlide(0, originalSlide); // restore the original slide.
            }
        }
        return result;
    }

    /**
     * Add the message's attachment to the data in the specified Uri to a new slide.
     */
    private int appendMedia(int type, Uri uri, SlideshowEditor slideShowEditor) {
        int result = OK;

        // If we're changing to text, just bail out.
        if (type == TEXT) {
            return result;
        }

        // The first time this method is called, mSlideshow.size() is going to be
        // one (a newly initialized slideshow has one empty slide). The first time we
        // attach the picture/video to that first empty slide. From then on when this
        // function is called, we've got to create a new slide and add the picture/video
        // to that new slide.
        boolean addNewSlide = true;
        if (mSlideshow.size() == 1 && !mSlideshow.isSimple()) {
            addNewSlide = false;
        }
        if (addNewSlide) {
            if (!slideShowEditor.addNewSlide()) {
                return result;
            }
        }
        int slideNum = mSlideshow.size() - 1;
        result = internalChangeMedia(type, uri, slideNum, slideShowEditor);
        if (result != OK) {
            // We added a new slide and what we attempted to insert on the slide failed.
            // Delete that slide, otherwise we could end up with a bunch of blank slides.
            // It's ok that we're removing the slide even if we didn't add it (because it was
            // the first default slide). If adding the first slide fails, we want to remove it.
            slideShowEditor.removeSlide(slideNum);
        }
        return result;
    }

    private int internalChangeMedia(int type, Uri uri, int slideNum,
            SlideshowEditor slideShowEditor) {
        int result = OK;
        try {
            if (type == IMAGE) {
                slideShowEditor.changeImage(slideNum, uri);
            } else if (type == VIDEO) {
                slideShowEditor.changeVideo(slideNum, uri);
            } else if (type == AUDIO) {
                slideShowEditor.changeAudio(slideNum, uri);
            } else {
                result = UNSUPPORTED_TYPE;
            }
        } catch (MmsException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = UNKNOWN_ERROR;
        } catch (UnsupportContentTypeException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = UNSUPPORTED_TYPE;
        } catch (ExceedMessageSizeException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = MESSAGE_SIZE_EXCEEDED;
        } catch (ResolutionException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = IMAGE_TOO_LARGE;
        }
        return result;
    }

    /**
     * Returns true if the message has an attachment (including slideshows).
     */
    public boolean hasAttachment() {
        return (mAttachmentType > TEXT);
    }

    /**
     * Returns the slideshow associated with this message.
     */
    public SlideshowModel getSlideshow() {
        return mSlideshow;
    }

    /**
     * Returns true if the message has a real slideshow, as opposed to just
     * one image attachment, for example.
     */
    public boolean hasSlideshow() {
        return (mAttachmentType == SLIDESHOW);
    }

    /**
     * Sets the MMS subject of the message.  Passing null indicates that there
     * is no subject.  Passing "" will result in an empty subject being added
     * to the message, possibly triggering a conversion to MMS.  This extra
     * bit of state is needed to support ComposeMessageActivity converting to
     * MMS when the user adds a subject.  An empty subject will be removed
     * before saving to disk or sending, however.
     */
    public void setSubject(CharSequence s, boolean notify) {
        mSubject = s;
        updateState(HAS_SUBJECT, (s != null), notify);
    }

    /**
     * Returns the MMS subject of the message.
     */
    public CharSequence getSubject() {
        return mSubject;
    }

    /**
     * Returns true if this message has an MMS subject. A subject has to be more than just
     * whitespace.
     * @return
     */
    public boolean hasSubject() {
        return mSubject != null && TextUtils.getTrimmedLength(mSubject) > 0;
    }

    /**
     * Moves the message text into the slideshow.  Should be called any time
     * the message is about to be sent or written to disk.
     */
    private void syncTextToSlideshow() {
        if (mSlideshow == null || mSlideshow.size() != 1)
            return;

        SlideModel slide = mSlideshow.get(0);
        TextModel text;
        if (!slide.hasText()) {
            // Add a TextModel to slide 0 if one doesn't already exist
            text = new TextModel(mActivity, ContentType.TEXT_PLAIN, "text_0.txt",
                                           mSlideshow.getLayout().getTextRegion());
            slide.add(text);
        } else {
            // Otherwise just reuse the existing one.
            text = slide.getText();
        }
        text.setText(mText);
    }

    /**
     * Sets the message text out of the slideshow.  Should be called any time
     * a slideshow is loaded from disk.
     */
    private void syncTextFromSlideshow() {
        // Don't sync text for real slideshows.
        if (mSlideshow.size() != 1) {
            return;
        }

        SlideModel slide = mSlideshow.get(0);
        if (slide == null || !slide.hasText()) {
            return;
        }

        mText = slide.getText().getText();
    }

    /**
     * Removes the subject if it is empty, possibly converting back to SMS.
     */
    private void removeSubjectIfEmpty(boolean notify) {
        if (!hasSubject()) {
            setSubject(null, notify);
        }
    }

    /**
     * Gets internal message state ready for storage.  Should be called any
     * time the message is about to be sent or written to disk.
     */
    private void prepareForSave(boolean notify) {
        // Make sure our working set of recipients is resolved
        // to first-class Contact objects before we save.
        syncWorkingRecipients();

        if (hasMmsContentToSave()) {
            ensureSlideshow();
            syncTextToSlideshow();
        }
    }

    /**
     * Resolve the temporary working set of recipients to a ContactList.
     */
    public void syncWorkingRecipients() {
        if (mWorkingRecipients != null) {
            ContactList recipients = ContactList.getByNumbers(mWorkingRecipients, false);
            mConversation.setRecipients(recipients);    // resets the threadId to zero
            setHasMultipleRecipients(recipients.size() > 1, true);
            mWorkingRecipients = null;
        }
    }

    public String getWorkingRecipients() {
        // this function is used for DEBUG only
        if (mWorkingRecipients == null) {
            return null;
        }
        ContactList recipients = ContactList.getByNumbers(mWorkingRecipients, false);
        return recipients.serialize();
    }

    // Call when we've returned from adding an attachment. We're no longer forcing the message
    // into a Mms message. At this point we either have the goods to make the message a Mms
    // or we don't. No longer fake it.
    public void removeFakeMmsForDraft() {
        updateState(FORCE_MMS, false, false);
    }

    /**
     * Force the message to be saved as MMS and return the Uri of the message.
     * Typically used when handing a message off to another activity.
     */
    public Uri saveAsMms(boolean notify) {
        if (DEBUG) LogTag.debug("saveAsMms mConversation=%s", mConversation);

        // If we have discarded the message, just bail out.
        if (mDiscarded) {
            LogTag.warn("saveAsMms mDiscarded: true mConversation: " + mConversation +
                    " returning NULL uri and bailing");
            return null;
        }

        // FORCE_MMS behaves as sort of an "invisible attachment", making
        // the message seem non-empty (and thus not discarded).  This bit
        // is sticky until the last other MMS bit is removed, at which
        // point the message will fall back to SMS.
        updateState(FORCE_MMS, true, notify);

        // Collect our state to be written to disk.
        prepareForSave(true /* notify */);

        try {
            // Make sure we are saving to the correct thread ID.
            DraftCache.getInstance().setSavingDraft(true);
            if (!mConversation.getRecipients().isEmpty()) {
                mConversation.ensureThreadId();
            }
            mConversation.setDraftState(true);

            PduPersister persister = PduPersister.getPduPersister(mActivity);
            SendReq sendReq = makeSendReq(mConversation, mSubject);

            // If we don't already have a Uri lying around, make a new one.  If we do
            // have one already, make sure it is synced to disk.
            if (mMessageUri == null) {
                mMessageUri = createDraftMmsMessage(persister, sendReq, mSlideshow, null,
                        mActivity, null);
            } else {
                updateDraftMmsMessage(mMessageUri, persister, mSlideshow, sendReq, null);
            }
            mHasMmsDraft = true;
        } finally {
            DraftCache.getInstance().setSavingDraft(false);
        }
        return mMessageUri;
    }

    /**
     * Save this message as a draft in the conversation previously specified
     * to {@link setConversation}.
     */
    public void saveDraft(final boolean isStopping) {
        // If we have discarded the message, just bail out.
        if (mDiscarded) {
            LogTag.warn("saveDraft mDiscarded: true mConversation: " + mConversation +
                " skipping saving draft and bailing");
            return;
        }

        // Make sure setConversation was called.
        if (mConversation == null) {
            throw new IllegalStateException("saveDraft() called with no conversation");
        }

        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("saveDraft for mConversation " + mConversation);
        }

        // Get ready to write to disk. But don't notify message status when saving draft
        prepareForSave(false /* notify */);

        if (requiresMms()) {
            if (hasMmsContentToSave()) {
                asyncUpdateDraftMmsMessage(mConversation, isStopping);
                mHasMmsDraft = true;
            }
        } else {
            String content = mText.toString();

            // bug 2169583: don't bother creating a thread id only to delete the thread
            // because the content is empty. When we delete the thread in updateDraftSmsMessage,
            // we didn't nullify conv.mThreadId, causing a temperary situation where conv
            // is holding onto a thread id that isn't in the database. If a new message arrives
            // and takes that thread id (because it's the next thread id to be assigned), the
            // new message will be merged with the draft message thread, causing confusion!
            if (!TextUtils.isEmpty(content)) {
                asyncUpdateDraftSmsMessage(mConversation, content, isStopping);
                mHasSmsDraft = true;
            } else {
                // When there's no associated text message, we have to handle the case where there
                // might have been a previous mms draft for this message. This can happen when a
                // user turns an mms back into a sms, such as creating an mms draft with a picture,
                // then removing the picture.
                asyncDeleteDraftMmsMessage(mConversation);
                mMessageUri = null;
            }
        }
    }

    synchronized public void discard() {
        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("[WorkingMessage] discard");
        }

        if (mDiscarded == true) {
            return;
        }

        // Mark this message as discarded in order to make saveDraft() no-op.
        mDiscarded = true;

        cancelThumbnailLoading();

        // Delete any associated drafts if there are any.
        if (mHasMmsDraft) {
            asyncDeleteDraftMmsMessage(mConversation);
        }
        if (mHasSmsDraft) {
            asyncDeleteDraftSmsMessage(mConversation);
        }
        clearConversation(mConversation, true);
    }

    public void unDiscard() {
        if (DEBUG) LogTag.debug("unDiscard");

        mDiscarded = false;
    }

    /**
     * Returns true if discard() has been called on this message.
     */
    public boolean isDiscarded() {
        return mDiscarded;
    }

    /**
     * To be called from our Activity's onSaveInstanceState() to give us a chance
     * to stow our state away for later retrieval.
     *
     * @param bundle The Bundle passed in to onSaveInstanceState
     */
    public void writeStateToBundle(Bundle bundle) {
        if (hasSubject()) {
            bundle.putString("subject", mSubject.toString());
        }

        if (mMessageUri != null) {
            bundle.putParcelable("msg_uri", mMessageUri);
        } else if (hasText()) {
            bundle.putString("sms_body", mText.toString());
        }
    }

    /**
     * To be called from our Activity's onCreate() if the activity manager
     * has given it a Bundle to reinflate
     * @param bundle The Bundle passed in to onCreate
     */
    public void readStateFromBundle(Bundle bundle) {
        if (bundle == null) {
            return;
        }

        String subject = bundle.getString("subject");
        setSubject(subject, false);

        Uri uri = (Uri)bundle.getParcelable("msg_uri");
        if (uri != null) {
            loadFromUri(uri);
            return;
        } else {
            String body = bundle.getString("sms_body");
            mText = body;
        }
    }

    /**
     * Update the temporary list of recipients, used when setting up a
     * new conversation.  Will be converted to a ContactList on any
     * save event (send, save draft, etc.)
     */
    public void setWorkingRecipients(List<String> numbers) {
        mWorkingRecipients = numbers;
        String s = null;
        if (numbers != null) {
            int size = numbers.size();
            switch (size) {
            case 1:
                s = numbers.get(0);
                break;
            case 0:
                s = "empty";
                break;
            default:
                s = "{...} len=" + size;
            }
        }
    }

    private void dumpWorkingRecipients() {
        Log.i(TAG, "-- mWorkingRecipients:");

        if (mWorkingRecipients != null) {
            int count = mWorkingRecipients.size();
            for (int i=0; i<count; i++) {
                Log.i(TAG, "   [" + i + "] " + mWorkingRecipients.get(i));
            }
            Log.i(TAG, "");
        }
    }

    public void dump() {
        Log.i(TAG, "WorkingMessage:");
        dumpWorkingRecipients();
        if (mConversation != null) {
            Log.i(TAG, "mConversation: " + mConversation.toString());
        }
    }

    /**
     * Set the conversation associated with this message.
     */
    public void setConversation(Conversation conv) {
        if (DEBUG) LogTag.debug("setConversation %s -> %s", mConversation, conv);

        mConversation = conv;

        // Convert to MMS if there are any email addresses in the recipient list.
        ContactList contactList = conv.getRecipients();
        setHasEmail(contactList.containsEmail(), false);
        setHasMultipleRecipients(contactList.size() > 1, false);
    }

    public Conversation getConversation() {
        return mConversation;
    }

    /**
     * Hint whether or not this message will be delivered to an
     * an email address.
     */
    public void setHasEmail(boolean hasEmail, boolean notify) {
        if (MmsConfig.getEmailGateway() != null) {
            updateState(RECIPIENTS_REQUIRE_MMS, false, notify);
        } else {
            updateState(RECIPIENTS_REQUIRE_MMS, hasEmail, notify);
        }
    }
    /**
     * Set whether this message will be sent to multiple recipients. This is a hint whether the
     * message needs to be sent as an mms or not. If MmsConfig.getGroupMmsEnabled is false, then
     * the fact that the message is sent to multiple recipients is not a factor in determining
     * whether the message is sent as an mms, but the other factors (such as, "has a picture
     * attachment") still hold true.
     */
    public void setHasMultipleRecipients(boolean hasMultipleRecipients, boolean notify) {
        updateState(MULTIPLE_RECIPIENTS,
                hasMultipleRecipients &&
                    MessagingPreferenceActivity.getIsGroupMmsEnabled(mActivity),
                notify);
    }

    /**
     * Returns true if this message would require MMS to send.
     */
    public boolean requiresMms() {
        return (mMmsState > 0);
    }

    /**
     * Returns true if this message has been turned into an mms because it has a subject or
     * an attachment, but not just because it has multiple recipients.
     */
    private boolean hasMmsContentToSave() {
        if (mMmsState == 0) {
            return false;
        }
        if (mMmsState == MULTIPLE_RECIPIENTS && !hasText()) {
            // If this message is only mms because of multiple recipients and there's no text
            // to save, don't bother saving.
            return false;
        }
        return true;
    }

    /**
     * Set whether or not we want to send this message via MMS in order to
     * avoid sending an excessive number of concatenated SMS messages.
     * @param: mmsRequired is the value for the LENGTH_REQUIRES_MMS bit.
     * @param: notify Whether or not to notify the user.
    */
    public void setLengthRequiresMms(boolean mmsRequired, boolean notify) {
        updateState(LENGTH_REQUIRES_MMS, mmsRequired, notify);
    }

    private static String stateString(int state) {
        if (state == 0)
            return "<none>";

        StringBuilder sb = new StringBuilder();
        if ((state & RECIPIENTS_REQUIRE_MMS) > 0)
            sb.append("RECIPIENTS_REQUIRE_MMS | ");
        if ((state & HAS_SUBJECT) > 0)
            sb.append("HAS_SUBJECT | ");
        if ((state & HAS_ATTACHMENT) > 0)
            sb.append("HAS_ATTACHMENT | ");
        if ((state & LENGTH_REQUIRES_MMS) > 0)
            sb.append("LENGTH_REQUIRES_MMS | ");
        if ((state & FORCE_MMS) > 0)
            sb.append("FORCE_MMS | ");
        if ((state & MULTIPLE_RECIPIENTS) > 0)
            sb.append("MULTIPLE_RECIPIENTS | ");

        sb.delete(sb.length() - 3, sb.length());
        return sb.toString();
    }

    /**
     * Sets the current state of our various "MMS required" bits.
     *
     * @param state The bit to change, such as {@link HAS_ATTACHMENT}
     * @param on If true, set it; if false, clear it
     * @param notify Whether or not to notify the user
     */
    private void updateState(int state, boolean on, boolean notify) {
        if (!sMmsEnabled) {
            // If Mms isn't enabled, the rest of the Messaging UI should not be using any
            // feature that would cause us to to turn on any Mms flag and show the
            // "Converting to multimedia..." message.
            return;
        }
        int oldState = mMmsState;
        if (on) {
            mMmsState |= state;
        } else {
            mMmsState &= ~state;
        }

        // If we are clearing the last bit that is not FORCE_MMS,
        // expire the FORCE_MMS bit.
        if (mMmsState == FORCE_MMS && ((oldState & ~FORCE_MMS) > 0)) {
            mMmsState = 0;
        }

        // Notify the listener if we are moving from SMS to MMS
        // or vice versa.
        if (notify) {
            if (oldState == 0 && mMmsState != 0) {
                mStatusListener.onProtocolChanged(true);
            } else if (oldState != 0 && mMmsState == 0) {
                mStatusListener.onProtocolChanged(false);
            }
        }

        if (oldState != mMmsState) {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) LogTag.debug("updateState: %s%s = %s",
                    on ? "+" : "-",
                    stateString(state), stateString(mMmsState));
        }
    }

    /**
     * Send this message over the network.  Will call back with onMessageSent() once
     * it has been dispatched to the telephony stack.  This WorkingMessage object is
     * no longer useful after this method has been called.
     *
     * @throws ContentRestrictionException if sending an MMS and uaProfUrl is not defined
     * in mms_config.xml.
     */
    public void send(final String recipientsInUI) {
        long origThreadId = mConversation.getThreadId();

        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            LogTag.debug("send origThreadId: " + origThreadId);
        }

        removeSubjectIfEmpty(true /* notify */);

        // Get ready to write to disk.
        prepareForSave(true /* notify */);

        // We need the recipient list for both SMS and MMS.
        final Conversation conv = mConversation;
        String msgTxt = mText.toString();

        if (requiresMms() || addressContainsEmailToMms(conv, msgTxt)) {
            // uaProfUrl setting in mms_config.xml must be present to send an MMS.
            // However, SMS service will still work in the absence of a uaProfUrl address.
            if (MmsConfig.getUaProfUrl() == null) {
                String err = "WorkingMessage.send MMS sending failure. mms_config.xml is " +
                        "missing uaProfUrl setting.  uaProfUrl is required for MMS service, " +
                        "but can be absent for SMS.";
                RuntimeException ex = new NullPointerException(err);
                Log.e(TAG, err, ex);
                // now, let's just crash.
                throw ex;
            }

            // Make local copies of the bits we need for sending a message,
            // because we will be doing it off of the main thread, which will
            // immediately continue on to resetting some of this state.
            final Uri mmsUri = mMessageUri;
            final PduPersister persister = PduPersister.getPduPersister(mActivity);

            final SlideshowModel slideshow = mSlideshow;
            final CharSequence subject = mSubject;
            final boolean textOnly = mAttachmentType == TEXT;

            if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
                LogTag.debug("Send mmsUri: " + mmsUri);
            }

            // Do the dirty work of sending the message off of the main UI thread.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final SendReq sendReq = makeSendReq(conv, subject);

                    // Make sure the text in slide 0 is no longer holding onto a reference to
                    // the text in the message text box.
                    slideshow.prepareForSend();
                    sendMmsWorker(conv, mmsUri, persister, slideshow, sendReq, textOnly);

                    updateSendStats(conv);
                }
            }, "WorkingMessage.send MMS").start();
        } else {
            // Same rules apply as above.
            final String msgText = mText.toString();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    preSendSmsWorker(conv, msgText, recipientsInUI);

                    updateSendStats(conv);
                }
            }, "WorkingMessage.send SMS").start();
        }

        // update the Recipient cache with the new to address, if it's different
        RecipientIdCache.updateNumbers(conv.getThreadId(), conv.getRecipients());

        // Mark the message as discarded because it is "off the market" after being sent.
        mDiscarded = true;
    }

    // Be sure to only call this on a background thread.
    private void updateSendStats(final Conversation conv) {
        String[] dests = conv.getRecipients().getNumbers();
        final ArrayList<String> phoneNumbers = new ArrayList<String>(Arrays.asList(dests));

        DataUsageStatUpdater updater = new DataUsageStatUpdater(mActivity);
        updater.updateWithPhoneNumber(phoneNumbers);
    }

    private boolean addressContainsEmailToMms(Conversation conv, String text) {
        if (MmsConfig.getEmailGateway() != null) {
            String[] dests = conv.getRecipients().getNumbers();
            int length = dests.length;
            for (int i = 0; i < length; i++) {
                if (Mms.isEmailAddress(dests[i]) || MessageUtils.isAlias(dests[i])) {
                    String mtext = dests[i] + " " + text;
                    int[] params = SmsMessage.calculateLength(mtext, false);
                    if (params[0] > 1) {
                        updateState(RECIPIENTS_REQUIRE_MMS, true, true);
                        ensureSlideshow();
                        syncTextToSlideshow();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Message sending stuff

    private void preSendSmsWorker(Conversation conv, String msgText, String recipientsInUI) {
        // If user tries to send the message, it's a signal the inputted text is what they wanted.
        UserHappinessSignals.userAcceptedImeText(mActivity);

        mStatusListener.onPreMessageSent();

        long origThreadId = conv.getThreadId();

        // Make sure we are still using the correct thread ID for our recipient set.
        long threadId = conv.ensureThreadId();

        String semiSepRecipients = conv.getRecipients().serialize();

        // recipientsInUI can be empty when the user types in a number and hits send
        if (LogTag.SEVERE_WARNING && ((origThreadId != 0 && origThreadId != threadId) ||
               (!semiSepRecipients.equals(recipientsInUI) && !TextUtils.isEmpty(recipientsInUI)))) {
            String msg = origThreadId != 0 && origThreadId != threadId ?
                    "WorkingMessage.preSendSmsWorker threadId changed or " +
                    "recipients changed. origThreadId: " +
                    origThreadId + " new threadId: " + threadId +
                    " also mConversation.getThreadId(): " +
                    mConversation.getThreadId()
                :
                    "Recipients in window: \"" +
                    recipientsInUI + "\" differ from recipients from conv: \"" +
                    semiSepRecipients + "\"";

            // Just interrupt the process of sending message if recipient mismatch
            LogTag.warnPossibleRecipientMismatch(msg, mActivity);
        }else {
            // just do a regular send. We're already on a non-ui thread so no need to fire
            // off another thread to do this work.
            sendSmsWorker(msgText, semiSepRecipients, threadId);

            // Be paranoid and clean any draft SMS up.
            deleteDraftSmsMessage(threadId);
        }
    }

    private void sendSmsWorker(String msgText, String semiSepRecipients, long threadId) {
        String[] dests = TextUtils.split(semiSepRecipients, ";");
        if (LogTag.VERBOSE || Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.d(LogTag.TRANSACTION, "sendSmsWorker sending message: recipients=" +
                    semiSepRecipients + ", threadId=" + threadId);
        }
        MessageSender sender = new SmsMessageSender(mActivity, dests, msgText, threadId);
        try {
            sender.sendMessage(threadId);

            // Make sure this thread isn't over the limits in message count
            Recycler.getSmsRecycler().deleteOldMessagesByThreadId(mActivity, threadId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS message, threadId=" + threadId, e);
        }

        mStatusListener.onMessageSent();
        MmsWidgetProvider.notifyDatasetChanged(mActivity);
    }

    private void sendMmsWorker(Conversation conv, Uri mmsUri, PduPersister persister,
            SlideshowModel slideshow, SendReq sendReq, boolean textOnly) {
        long threadId = 0;
        Cursor cursor = null;
        boolean newMessage = false;
        try {
            // Put a placeholder message in the database first
            DraftCache.getInstance().setSavingDraft(true);
            mStatusListener.onPreMessageSent();

            // Make sure we are still using the correct thread ID for our
            // recipient set.
            threadId = conv.ensureThreadId();

            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                LogTag.debug("sendMmsWorker: update draft MMS message " + mmsUri +
                        " threadId: " + threadId);
            }

            // One last check to verify the address of the recipient.
            String[] dests = conv.getRecipients().getNumbers(true /* scrub for MMS address */);
            if (dests.length == 1) {
                // verify the single address matches what's in the database. If we get a different
                // address back, jam the new value back into the SendReq.
                String newAddress =
                    Conversation.verifySingleRecipient(mActivity, conv.getThreadId(), dests[0]);

                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    LogTag.debug("sendMmsWorker: newAddress " + newAddress +
                            " dests[0]: " + dests[0]);
                }

                if (!newAddress.equals(dests[0])) {
                    dests[0] = newAddress;
                    EncodedStringValue[] encodedNumbers = EncodedStringValue.encodeStrings(dests);
                    if (encodedNumbers != null) {
                        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                            LogTag.debug("sendMmsWorker: REPLACING number!!!");
                        }
                        sendReq.setTo(encodedNumbers);
                    }
                }
            }
            newMessage = mmsUri == null;
            if (newMessage) {
                // Write something in the database so the new message will appear as sending
                ContentValues values = new ContentValues();
                values.put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_OUTBOX);
                values.put(Mms.THREAD_ID, threadId);
                values.put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ);
                if (textOnly) {
                    values.put(Mms.TEXT_ONLY, 1);
                }
                mmsUri = SqliteWrapper.insert(mActivity, mContentResolver, Mms.Outbox.CONTENT_URI,
                        values);
            }
            mStatusListener.onMessageSent();

            // If user tries to send the message, it's a signal the inputted text is
            // what they wanted.
            UserHappinessSignals.userAcceptedImeText(mActivity);

            // First make sure we don't have too many outstanding unsent message.
            cursor = SqliteWrapper.query(mActivity, mContentResolver,
                    Mms.Outbox.CONTENT_URI, MMS_OUTBOX_PROJECTION, null, null, null);
            if (cursor != null) {
                long maxMessageSize = MmsConfig.getMaxSizeScaleForPendingMmsAllowed() *
                MmsConfig.getMaxMessageSize();
                long totalPendingSize = 0;
                while (cursor.moveToNext()) {
                    totalPendingSize += cursor.getLong(MMS_MESSAGE_SIZE_INDEX);
                }
                if (totalPendingSize >= maxMessageSize) {
                    unDiscard();    // it wasn't successfully sent. Allow it to be saved as a draft.
                    mStatusListener.onMaxPendingMessagesReached();
                    markMmsMessageWithError(mmsUri);
                    return;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        try {
            if (newMessage) {
                // Create a new MMS message if one hasn't been made yet.
                mmsUri = createDraftMmsMessage(persister, sendReq, slideshow, mmsUri,
                        mActivity, null);
            } else {
                // Otherwise, sync the MMS message in progress to disk.
                updateDraftMmsMessage(mmsUri, persister, slideshow, sendReq, null);
            }

            // Be paranoid and clean any draft SMS up.
            deleteDraftSmsMessage(threadId);
        } finally {
            DraftCache.getInstance().setSavingDraft(false);
        }

        // Resize all the resizeable attachments (e.g. pictures) to fit
        // in the remaining space in the slideshow.
        int error = 0;
        try {
            slideshow.finalResize(mmsUri);
        } catch (ExceedMessageSizeException e1) {
            error = MESSAGE_SIZE_EXCEEDED;
        } catch (MmsException e1) {
            error = UNKNOWN_ERROR;
        }
        if (error != 0) {
            markMmsMessageWithError(mmsUri);
            mStatusListener.onAttachmentError(error);
            return;
        }
        MessageSender sender = new MmsMessageSender(mActivity, mmsUri,
                slideshow.getCurrentMessageSize());
        try {
            if (!sender.sendMessage(threadId)) {
                // The message was sent through SMS protocol, we should
                // delete the copy which was previously saved in MMS drafts.
                SqliteWrapper.delete(mActivity, mContentResolver, mmsUri, null, null);
            }

            // Make sure this thread isn't over the limits in message count
            Recycler.getMmsRecycler().deleteOldMessagesByThreadId(mActivity, threadId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send message: " + mmsUri + ", threadId=" + threadId, e);
        }
        MmsWidgetProvider.notifyDatasetChanged(mActivity);
    }

    private void markMmsMessageWithError(Uri mmsUri) {
        try {
            PduPersister p = PduPersister.getPduPersister(mActivity);
            // Move the message into MMS Outbox. A trigger will create an entry in
            // the "pending_msgs" table.
            p.move(mmsUri, Mms.Outbox.CONTENT_URI);

            // Now update the pending_msgs table with an error for that new item.
            ContentValues values = new ContentValues(1);
            values.put(PendingMessages.ERROR_TYPE, MmsSms.ERR_TYPE_GENERIC_PERMANENT);
            long msgId = ContentUris.parseId(mmsUri);
            SqliteWrapper.update(mActivity, mContentResolver,
                    PendingMessages.CONTENT_URI,
                    values, PendingMessages.MSG_ID + "=" + msgId, null);
        } catch (MmsException e) {
            // Not much we can do here. If the p.move throws an exception, we'll just
            // leave the message in the draft box.
            Log.e(TAG, "Failed to move message to outbox and mark as error: " + mmsUri, e);
        }
    }

    // Draft message stuff

    private static final String[] MMS_DRAFT_PROJECTION = {
        Mms._ID,                // 0
        Mms.SUBJECT,            // 1
        Mms.SUBJECT_CHARSET     // 2
    };

    private static final int MMS_ID_INDEX         = 0;
    private static final int MMS_SUBJECT_INDEX    = 1;
    private static final int MMS_SUBJECT_CS_INDEX = 2;

    private static Uri readDraftMmsMessage(Context context, Conversation conv, StringBuilder sb) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("readDraftMmsMessage conv: " + conv);
        }
        Cursor cursor;
        ContentResolver cr = context.getContentResolver();

        final String selection = Mms.THREAD_ID + " = " + conv.getThreadId();
        cursor = SqliteWrapper.query(context, cr,
                Mms.Draft.CONTENT_URI, MMS_DRAFT_PROJECTION,
                selection, null, null);
        if (cursor == null) {
            return null;
        }

        Uri uri;
        try {
            if (cursor.moveToFirst()) {
                uri = ContentUris.withAppendedId(Mms.Draft.CONTENT_URI,
                        cursor.getLong(MMS_ID_INDEX));
                String subject = MessageUtils.extractEncStrFromCursor(cursor, MMS_SUBJECT_INDEX,
                        MMS_SUBJECT_CS_INDEX);
                if (subject != null) {
                    sb.append(subject);
                }
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    LogTag.debug("readDraftMmsMessage uri: ", uri);
                }
                return uri;
            }
        } finally {
            cursor.close();
        }

        return null;
    }

    /**
     * makeSendReq should always return a non-null SendReq, whether the dest addresses are
     * valid or not.
     */
    private static SendReq makeSendReq(Conversation conv, CharSequence subject) {
        String[] dests = conv.getRecipients().getNumbers(true /* scrub for MMS address */);

        SendReq req = new SendReq();
        EncodedStringValue[] encodedNumbers = EncodedStringValue.encodeStrings(dests);
        if (encodedNumbers != null) {
            req.setTo(encodedNumbers);
        }

        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject.toString()));
        }

        req.setDate(System.currentTimeMillis() / 1000L);

        return req;
    }

    private static Uri createDraftMmsMessage(PduPersister persister, SendReq sendReq,
            SlideshowModel slideshow, Uri preUri, Context context,
            HashMap<Uri, InputStream> preOpenedFiles) {
        if (slideshow == null) {
            return null;
        }
        try {
            PduBody pb = slideshow.toPduBody();
            sendReq.setBody(pb);
            Uri res = persister.persist(sendReq, preUri == null ? Mms.Draft.CONTENT_URI : preUri,
                    true, MessagingPreferenceActivity.getIsGroupMmsEnabled(context),
                    preOpenedFiles);
            slideshow.sync(pb);
            return res;
        } catch (MmsException e) {
            return null;
        } catch (IllegalStateException e) {
            Log.e(TAG,"failed to create draft mms "+ e);
            return null;
        }
    }

    private void asyncUpdateDraftMmsMessage(final Conversation conv, final boolean isStopping) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("asyncUpdateDraftMmsMessage conv=%s mMessageUri=%s", conv, mMessageUri);
        }
        final HashMap<Uri, InputStream> preOpenedFiles =
                mSlideshow.openPartFiles(mContentResolver);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DraftCache.getInstance().setSavingDraft(true);

                    final PduPersister persister = PduPersister.getPduPersister(mActivity);
                    final SendReq sendReq = makeSendReq(conv, mSubject);

                    if (mMessageUri == null) {
                        mMessageUri = createDraftMmsMessage(persister, sendReq, mSlideshow, null,
                                mActivity, preOpenedFiles);
                    } else {
                        updateDraftMmsMessage(mMessageUri, persister, mSlideshow, sendReq,
                                preOpenedFiles);
                    }
                    ensureThreadIdIfNeeded(conv, isStopping);
                    conv.setDraftState(true);
                    if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                        LogTag.debug("asyncUpdateDraftMmsMessage conv: " + conv +
                                " uri: " + mMessageUri);
                    }

                    // Be paranoid and delete any SMS drafts that might be lying around. Must do
                    // this after ensureThreadId so conv has the correct thread id.
                    asyncDeleteDraftSmsMessage(conv);
                } finally {
                    DraftCache.getInstance().setSavingDraft(false);
                    closePreOpenedFiles(preOpenedFiles);
                }
            }
        }, "WorkingMessage.asyncUpdateDraftMmsMessage").start();
    }

    private static void updateDraftMmsMessage(Uri uri, PduPersister persister,
            SlideshowModel slideshow, SendReq sendReq, HashMap<Uri, InputStream> preOpenedFiles) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("updateDraftMmsMessage uri=%s", uri);
        }
        if (uri == null) {
            Log.e(TAG, "updateDraftMmsMessage null uri");
            return;
        }
        persister.updateHeaders(uri, sendReq);

        final PduBody pb = slideshow.toPduBody();

        try {
            persister.updateParts(uri, pb, preOpenedFiles);
        } catch (MmsException e) {
            Log.e(TAG, "updateDraftMmsMessage: cannot update message " + uri);
        }

        slideshow.sync(pb);
    }

    private static void closePreOpenedFiles(HashMap<Uri, InputStream> preOpenedFiles) {
        if (preOpenedFiles == null) {
            return;
        }
        Set<Uri> uris = preOpenedFiles.keySet();
        for (Uri uri : uris) {
            InputStream is = preOpenedFiles.get(uri);
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static final String SMS_DRAFT_WHERE = Sms.TYPE + "=" + Sms.MESSAGE_TYPE_DRAFT;
    private static final String[] SMS_BODY_PROJECTION = { Sms.BODY };
    private static final int SMS_BODY_INDEX = 0;

    /**
     * Reads a draft message for the given thread ID from the database,
     * if there is one, deletes it from the database, and returns it.
     * @return The draft message or an empty string.
     */
    private String readDraftSmsMessage(Conversation conv) {
        long thread_id = conv.getThreadId();
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.d(TAG, "readDraftSmsMessage conv: " + conv);
        }
        // If it's an invalid thread or we know there's no draft, don't bother.
        if (thread_id <= 0 || !conv.hasDraft()) {
            return "";
        }

        Uri thread_uri = ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, thread_id);
        String body = "";

        Cursor c = SqliteWrapper.query(mActivity, mContentResolver,
                        thread_uri, SMS_BODY_PROJECTION, SMS_DRAFT_WHERE, null, null);
        boolean haveDraft = false;
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    body = c.getString(SMS_BODY_INDEX);
                    haveDraft = true;
                }
            } finally {
                c.close();
            }
        }

        // We found a draft, and if there are no messages in the conversation,
        // that means we deleted the thread, too. Must reset the thread id
        // so we'll eventually create a new thread.
        if (haveDraft && conv.getMessageCount() == 0) {
            asyncDeleteDraftSmsMessage(conv);

            // Clean out drafts for this thread -- if the recipient set changes,
            // we will lose track of the original draft and be unable to delete
            // it later.  The message will be re-saved if necessary upon exit of
            // the activity.
            clearConversation(conv, true);
        }
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("readDraftSmsMessage haveDraft: ", !TextUtils.isEmpty(body));
        }

        return body;
    }

    public void clearConversation(final Conversation conv, boolean resetThreadId) {
        if (resetThreadId && conv.getMessageCount() == 0) {
            if (DEBUG) LogTag.debug("clearConversation calling clearThreadId");
            conv.clearThreadId();
        }

        conv.setDraftState(false);
    }

    private void asyncUpdateDraftSmsMessage(final Conversation conv, final String contents,
            final boolean isStopping) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DraftCache.getInstance().setSavingDraft(true);
                    if (conv.getRecipients().isEmpty()) {
                        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                            LogTag.debug("asyncUpdateDraftSmsMessage no recipients, not saving");
                        }
                        return;
                    }
                    ensureThreadIdIfNeeded(conv, isStopping);
                    conv.setDraftState(true);
                    updateDraftSmsMessage(conv, contents);
                } finally {
                    DraftCache.getInstance().setSavingDraft(false);
                }
            }
        }, "WorkingMessage.asyncUpdateDraftSmsMessage").start();
    }

    private void updateDraftSmsMessage(final Conversation conv, String contents) {
        final long threadId = conv.getThreadId();
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("updateDraftSmsMessage tid=%d, contents=\"%s\"", threadId, contents);
        }

        // If we don't have a valid thread, there's nothing to do.
        if (threadId <= 0) {
            return;
        }

        ContentValues values = new ContentValues(3);
        values.put(Sms.THREAD_ID, threadId);
        values.put(Sms.BODY, contents);
        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_DRAFT);
        SqliteWrapper.insert(mActivity, mContentResolver, Sms.CONTENT_URI, values);
        asyncDeleteDraftMmsMessage(conv);
        mMessageUri = null;
    }

    private void asyncDelete(final Uri uri, final String selection, final String[] selectionArgs) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("asyncDelete %s where %s", uri, selection);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                SqliteWrapper.delete(mActivity, mContentResolver, uri, selection, selectionArgs);
            }
        }, "WorkingMessage.asyncDelete").start();
    }

    public void asyncDeleteDraftSmsMessage(Conversation conv) {
        mHasSmsDraft = false;

        final long threadId = conv.getThreadId();
        if (threadId > 0) {
            asyncDelete(ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, threadId),
                SMS_DRAFT_WHERE, null);
        }
    }

    private void deleteDraftSmsMessage(long threadId) {
        SqliteWrapper.delete(mActivity, mContentResolver,
                ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, threadId),
                SMS_DRAFT_WHERE, null);
    }

    private void asyncDeleteDraftMmsMessage(Conversation conv) {
        mHasMmsDraft = false;

        final long threadId = conv.getThreadId();
        // If the thread id is < 1, then the thread_id in the pdu will be "" or NULL. We have
        // to clear those messages as well as ones with a valid thread id.
        final String where = Mms.THREAD_ID +  (threadId > 0 ? " = " + threadId : " IS NULL");
        asyncDelete(Mms.Draft.CONTENT_URI, where, null);
    }

    /**
     * Ensure the thread id in conversation if needed, when we try to save a draft with a orphaned
     * one.
     * @param conv The conversation we are in.
     * @param isStopping Whether we are saving the draft in CMA'a onStop
     */
    private void ensureThreadIdIfNeeded(final Conversation conv, final boolean isStopping) {
        if (isStopping && conv.getMessageCount() == 0) {
            // We need to save the drafts in an unorphaned thread id. When the user goes
            // back to ConversationList while we're saving a draft from CMA's.onStop,
            // ConversationList will delete all threads from the thread table that
            // don't have associated sms or pdu entries. In case our thread got deleted,
            // well call clearThreadId() so ensureThreadId will query the db for the new
            // thread.
            conv.clearThreadId();   // force us to get the updated thread id
        }
        if (!conv.getRecipients().isEmpty()) {
            conv.ensureThreadId();
        }
    }
}
