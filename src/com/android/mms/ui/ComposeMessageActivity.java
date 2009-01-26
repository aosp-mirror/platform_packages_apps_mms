/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.ui;

import static android.content.res.Configuration.KEYBOARDHIDDEN_NO;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_ABORT;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_COMPLETE;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_START;
import static com.android.mms.transaction.ProgressCallbackEntity.PROGRESS_STATUS_ACTION;
import static com.android.mms.ui.MessageListAdapter.COLUMN_ID;
import static com.android.mms.ui.MessageListAdapter.COLUMN_MSG_TYPE;
import static com.android.mms.ui.MessageListAdapter.PROJECTION;

import com.android.internal.telephony.GsmAlphabet;
import com.android.mms.ExceedMessageSizeException;
import com.android.mms.R;
import com.android.mms.ResolutionException;
import com.android.mms.UnsupportContentTypeException;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.TextModel;
import com.android.mms.transaction.MessageSender;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.transaction.SmsMessageSender;
import com.android.mms.ui.AttachmentEditor.OnAttachmentChangedListener;
import com.android.mms.ui.MessageUtils.ResizeImageResultCallback;
import com.android.mms.ui.RecipientList.Recipient;
import com.android.mms.ui.RecipientsEditor.RecipientContextMenuInfo;
import com.android.mms.util.SendingProgressTokenManager;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendReq;
import com.google.android.mms.util.SqliteWrapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Contacts.People;
import android.provider.Contacts.Intents.Insert;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.text.style.URLSpan;
import android.util.Config;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewStub;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;

/**
 * This is the main UI for:
 * 1. Composing a new message;
 * 2. Viewing/managing message history of a conversation.
 *
 * This activity can handle following parameters from the intent
 * by which it's launched.
 * thread_id long Identify the conversation to be viewed. When creating a
 *         new message, this parameter shouldn't be present.
 * msg_uri Uri The message which should be opened for editing in the editor.
 * address String The addresses of the recipients in current conversation.
 * compose_mode boolean Setting compose_mode to true will force the activity
 *         to show the recipients editor and the attachment editor but hide
 *         the message history. By default, this flag is set to false.
 * exit_on_sent boolean Exit this activity after the message is sent.
 */
public class ComposeMessageActivity extends Activity
        implements View.OnClickListener, OnAttachmentChangedListener {
    public static final int REQUEST_CODE_ATTACH_IMAGE     = 10;
    public static final int REQUEST_CODE_TAKE_PICTURE     = 11;
    public static final int REQUEST_CODE_ATTACH_VIDEO     = 12;
    public static final int REQUEST_CODE_ATTACH_SOUND     = 14;
    public static final int REQUEST_CODE_RECORD_SOUND     = 15;
    public static final int REQUEST_CODE_CREATE_SLIDESHOW = 16;

    private static final String TAG = "ComposeMessageActivity";
    private static final boolean DEBUG = false;
    private static final boolean TRACE = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    // Menu ID
    private static final int MENU_ADD_SUBJECT           = 0;
    private static final int MENU_DELETE_THREAD         = 1;
    private static final int MENU_ADD_ATTACHMENT        = 2;
    private static final int MENU_DISCARD               = 3;
    static final int         MENU_SEND                  = 4;
    private static final int MENU_COMPOSE_NEW           = 5;
    private static final int MENU_CONVERSATION_LIST     = 6;

    // Context menu ID
    private static final int MENU_VIEW_CONTACT          = 12;
    private static final int MENU_ADD_TO_CONTACTS       = 13;

    private static final int MENU_EDIT_MESSAGE          = 14;
    private static final int MENU_VIEW_PICTURE          = 15;
    private static final int MENU_VIEW_SLIDESHOW        = 16;
    private static final int MENU_VIEW_MESSAGE_DETAILS  = 17;
    private static final int MENU_DELETE_MESSAGE        = 18;
    private static final int MENU_SEARCH                = 19;
    private static final int MENU_DELIVERY_REPORT       = 20;
    private static final int MENU_FORWARD_MESSAGE       = 21;
    private static final int MENU_CALL_BACK             = 22;
    private static final int MENU_SEND_EMAIL            = 23;

    private static final int SUBJECT_MAX_LENGTH    =  40;
    private static final int RECIPIENTS_MAX_LENGTH = 312;

    private static final int MESSAGE_LIST_QUERY_TOKEN = 9527;
    private static final int THREAD_READ_QUERY_TOKEN = 9696;

    private static final int MMS_THRESHOLD = 4;

    private static final int CHARS_BEFORE_COUNTER_SHOWN = 130;

    private static final long NO_DATE_FOR_DIALOG = -1L;

    private ContentResolver mContentResolver;

    // The parameters/states of the activity.
    private long mThreadId;                 // Database key for the current conversation
    private String mExternalAddress;        // Serialized recipients in the current conversation
    private boolean mComposeMode;           // Should we show the recipients editor on startup?
    private boolean mExitOnSent;            // Should we finish() after sending a message?

    private View mTopPanel;                 // View containing the recipient and subject editors
    private View mBottomPanel;              // View containing the text editor, send button, ec.
    private EditText mTextEditor;           // Text editor to type your message into
    private TextView mTextCounter;          // Shows the number of characters used in text editor
    private Button mSendButton;             // Press to detonate

    private String mMsgText;                // Text of message

    private Cursor mMsgListCursor;          // Cursor for messages-in-thread query
    private final Object mMsgListCursorLock = new Object();
    private MsgListQueryHandler mMsgListQueryHandler;

    private ListView mMsgListView;               // ListView for messages in this conversation
    private MessageListAdapter mMsgListAdapter;  // and its corresponding ListAdapter

    private RecipientList mRecipientList;        // List of recipients for this conversation
    private RecipientsEditor mRecipientsEditor;  // UI control for editing recipients

    private boolean mIsKeyboardOpen;             // Whether the hardware keyboard is visible

    private boolean mPossiblePendingNotification;   // If the message list has changed, we may have
                                                    // a pending notification to deal with.

    private static final int RECIPIENTS_REQUIRE_MMS = (1 << 0);
    private static final int HAS_SUBJECT = (1 << 1);
    private static final int HAS_ATTACHMENT = (1 << 2);
    private static final int LENGTH_REQUIRES_MMS = (1 << 3);

    private int mMessageState;                  // A bitmap of the above indicating different
                                                // properties of the message -- any bit set
                                                // will require conversion to MMS.

    private int mSeptets;   // Number of septets required for the message.
    private int mMsgCount;  // Number of SMS messages required to send the current message.

    // These fields are only used in MMS compose mode (requiresMms() == true) and should
    // otherwise be null.
    private SlideshowModel mSlideshow;
    private Uri mMessageUri;
    private EditText mSubjectTextEditor;    // Text editor for MMS subject
    private String mSubject;                // MMS subject
    private AttachmentEditor mAttachmentEditor;
    private PduPersister mPersister;

    //==========================================================
    // Inner classes
    //==========================================================

    private final Handler mAttachmentEditorHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AttachmentEditor.MSG_EDIT_SLIDESHOW: {
                    Intent intent = new Intent(ComposeMessageActivity.this,
                            SlideshowEditActivity.class);
                    // Need this to make sure mMessageUri is set up.
                    convertMessageIfNeeded(HAS_ATTACHMENT, true);
                    intent.setData(mMessageUri);
                    startActivityForResult(intent, REQUEST_CODE_CREATE_SLIDESHOW);
                    break;
                }
                case AttachmentEditor.MSG_SEND_SLIDESHOW: {
                    if (isPreparedForSending()) {
                        ComposeMessageActivity.this.confirmSendMessageIfNeeded();
                    }
                    break;
                }
                case AttachmentEditor.MSG_VIEW_IMAGE:
                case AttachmentEditor.MSG_PLAY_AUDIO:
                case AttachmentEditor.MSG_PLAY_VIDEO:
                case AttachmentEditor.MSG_PLAY_SLIDESHOW: {
                    Intent intent = new Intent(ComposeMessageActivity.this,
                            SlideshowActivity.class);
                    intent.setData(mMessageUri);
                    startActivity(intent);
                    break;
                }

                case AttachmentEditor.MSG_REPLACE_IMAGE:
                    showReplaceImageDialog();
                    break;

                case AttachmentEditor.MSG_REPLACE_VIDEO:
                    MessageUtils.selectVideo(
                            ComposeMessageActivity.this, REQUEST_CODE_ATTACH_VIDEO);
                    break;

                case AttachmentEditor.MSG_REPLACE_AUDIO:
                    MessageUtils.selectAudio(
                            ComposeMessageActivity.this, REQUEST_CODE_ATTACH_SOUND);
                    break;

                default:
                    break;
            }
        }
    };

    private final Handler mMessageListItemHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String type;
            switch (msg.what) {
                case MessageListItem.MSG_LIST_EDIT_MMS:
                    type = "mms";
                    break;
                case MessageListItem.MSG_LIST_EDIT_SMS:
                    type = "sms";
                    break;
                default:
                    Log.w(TAG, "Unknown message: " + msg.what);
                    return;
            }

            MessageItem msgItem = getMessageItem(type, (Long) msg.obj);
            if (msgItem != null) {
                editMessageItem(msgItem);
                int attachmentType = requiresMms()
                        ? MessageUtils.getAttachmentType(mSlideshow)
                        : AttachmentEditor.TEXT_ONLY;
                drawBottomPanel(attachmentType);
            }
        }
    };

    private final OnKeyListener mSubjectKeyListener = new OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            // When the subject editor is empty, press "DEL" to hide the input field.
            if ((keyCode == KeyEvent.KEYCODE_DEL) && (mSubjectTextEditor.length() == 0)) {
                mSubjectTextEditor.setVisibility(View.GONE);
                ComposeMessageActivity.this.hideTopPanelIfNecessary();
                convertMessageIfNeeded(HAS_SUBJECT, false);
                return true;
            }

            return false;
        }
    };

    private final OnKeyListener mEmbeddedTextEditorKeyListener =
            new OnKeyListener() {
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if ((event.getAction() == KeyEvent.ACTION_DOWN)
                            && (keyCode == KeyEvent.KEYCODE_ENTER)
                            && !event.isShiftPressed()) {
                        if (isPreparedForSending()) {
                            sendMessage();
                        }
                        return true;
                    } else {
                        return false;
                    }
                }
            };

    private MessageItem getMessageItem(String type, long msgId) {
        // Check whether the cursor is valid or not.
        if (mMsgListCursor.isClosed()
                || mMsgListCursor.isBeforeFirst()
                || mMsgListCursor.isAfterLast()) {
            Log.e(TAG, "Bad cursor.", new RuntimeException());
            return null;
        }

        MessageItem msgItem = mMsgListAdapter.getCachedMessageItem(type, msgId);
        if (msgItem == null) {
            try {
                msgItem = mMsgListAdapter.cacheMessageItem(type, mMsgListCursor);
            } catch (MmsException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return msgItem;
    }

    private void resetCounter() {
        mSeptets = 0;
        mMsgCount = 1;

        mTextCounter.setText("");
        mTextCounter.setVisibility(View.GONE);
    }

    private void updateCounter(CharSequence text, boolean increment) {
        boolean wasShown = (mSeptets >= CHARS_BEFORE_COUNTER_SHOWN);

        if (increment) {
            mSeptets += GsmAlphabet.countGsmSeptets(text.toString());
        } else {
            mSeptets -= GsmAlphabet.countGsmSeptets(text.toString());
        }

        boolean needsShown = (mSeptets >= CHARS_BEFORE_COUNTER_SHOWN);

        if (needsShown) {
            // Calculate the number of messages required and space remaining.
            int remainingInCurrentMessage;
            if (mSeptets > SmsMessage.MAX_USER_DATA_SEPTETS) {
                // If we have more characters than will fit in one SMS, we need to factor
                // in the size of the header to determine how many will fit.
                mMsgCount = mSeptets / (SmsMessage.MAX_USER_DATA_SEPTETS_WITH_HEADER) + 1;
                remainingInCurrentMessage = SmsMessage.MAX_USER_DATA_SEPTETS_WITH_HEADER
                        - (mSeptets % SmsMessage.MAX_USER_DATA_SEPTETS_WITH_HEADER);
            } else {
                mMsgCount = 1;
                remainingInCurrentMessage = SmsMessage.MAX_USER_DATA_SEPTETS - mSeptets;
            }

            // Update the remaining characters and number of messages required.
            mTextCounter.setText(remainingInCurrentMessage + " / " + mMsgCount);
            if (!wasShown) {
                mTextCounter.setVisibility(View.VISIBLE);
            }
        } else {
            if (wasShown) {
                mTextCounter.setVisibility(View.GONE);
            }
        }

        convertMessageIfNeeded(LENGTH_REQUIRES_MMS, mMsgCount >= MMS_THRESHOLD);
    }

    private void initMmsComponents() {
       // Initialize subject editor.
        mSubjectTextEditor = (EditText) findViewById(R.id.subject);
        mSubjectTextEditor.setOnKeyListener(mSubjectKeyListener);
        mSubjectTextEditor.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(SUBJECT_MAX_LENGTH) });
        if (!TextUtils.isEmpty(mSubject)) {
            mSubjectTextEditor.setText(mSubject);
        }

        try {
            if (mMessageUri != null) {
                // Move the message into Draft before editing it.
                mMessageUri = mPersister.move(mMessageUri, Mms.Draft.CONTENT_URI);
                mSlideshow = SlideshowModel.createFromMessageUri(this, mMessageUri);
            } else {
                mSlideshow = createNewMessage(this);
                if (mMsgText != null) {
                    mSlideshow.get(0).getText().setText(mMsgText);
                }
                mMessageUri = createTemporaryMmsMessage();
            }
        } catch (MmsException e) {
            Log.e(TAG, e.getMessage(), e);
            finish();
            return;
        }

        // Set up the attachment editor.
        mAttachmentEditor = new AttachmentEditor(this, mAttachmentEditorHandler,
                findViewById(R.id.attachment_editor));
        mAttachmentEditor.setOnAttachmentChangedListener(this);

        int attachmentType = MessageUtils.getAttachmentType(mSlideshow);
        if (attachmentType == AttachmentEditor.EMPTY) {
            fixEmptySlideshow(mSlideshow);
            attachmentType = AttachmentEditor.TEXT_ONLY;
        }
        mAttachmentEditor.setAttachment(mSlideshow, attachmentType);
    }

    synchronized private void uninitMmsComponents() {
        // Get text from slideshow if needed.
        if (mAttachmentEditor != null) {
            int attachmentType = mAttachmentEditor.getAttachmentType();
            if (AttachmentEditor.TEXT_ONLY == attachmentType) {
                mMsgText = mSlideshow.get(0).getText().getText();
            }
        }

        mMessageState = 0;
        mSlideshow = null;
        if (mMessageUri != null) {
            // Not sure if this is the best way to do this..
            if (mMessageUri.toString().startsWith(Mms.Draft.CONTENT_URI.toString())) {
                SqliteWrapper.delete(this, mContentResolver, mMessageUri, null, null);
                mMessageUri = null;
            }
        }
        if (mSubjectTextEditor != null) {
            mSubjectTextEditor.setText("");
            mSubjectTextEditor.setVisibility(View.GONE);
            hideTopPanelIfNecessary();
            mSubjectTextEditor = null;
        }
        mSubject = null;
        mAttachmentEditor = null;
    }

    synchronized private void refreshMmsComponents() {
        mMessageState = RECIPIENTS_REQUIRE_MMS;
        if (mSubjectTextEditor != null) {
            mSubjectTextEditor.setText("");
            mSubjectTextEditor.setVisibility(View.GONE);
        }
        mSubject = null;

        try {
            mSlideshow = createNewMessage(this);
            if (mMsgText != null) {
                mSlideshow.get(0).getText().setText(mMsgText);
            }
            mMessageUri = createTemporaryMmsMessage();
        } catch (MmsException e) {
            Log.e(TAG, e.getMessage(), e);
            finish();
            return;
        }

        int attachmentType = MessageUtils.getAttachmentType(mSlideshow);
        if (attachmentType == AttachmentEditor.EMPTY) {
            fixEmptySlideshow(mSlideshow);
            attachmentType = AttachmentEditor.TEXT_ONLY;
        }
        mAttachmentEditor.setAttachment(mSlideshow, attachmentType);
    }

    private boolean requiresMms() {
        return (mMessageState > 0);
    }

    private boolean recipientsRequireMms() {
        return mRecipientList.containsBcc() || mRecipientList.containsEmail();
    }

    private boolean hasAttachment() {
        return ((mAttachmentEditor != null)
             && (mAttachmentEditor.getAttachmentType() > AttachmentEditor.TEXT_ONLY));
    }

    private void updateState(int whichState, boolean set) {
        if (set) {
            mMessageState |= whichState;
        } else {
            mMessageState &= ~whichState;
        }
    }

    private void convertMessage(boolean toMms) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "Message type: " + (requiresMms() ? "MMS" : "SMS")
                    + " -> " + (toMms ? "MMS" : "SMS"));
        }

        if (toMms) {
            // Hide the counter and alert the user with a toast
            if (mTextCounter != null) {
                mTextCounter.setVisibility(View.GONE);
            }
            initMmsComponents();
        } else {
            uninitMmsComponents();
            // Show the counter and alert the user with a toast
            if ((mTextCounter != null) && (mSeptets >= CHARS_BEFORE_COUNTER_SHOWN)) {
                mTextCounter.setVisibility(View.VISIBLE);
            }
        }

        updateSendButtonState();
    }

    private void toastConvertInfo(boolean toMms) {
        // If we didn't know whether to convert (e.g. resetting after message
        // send, we need to notify the user.
        int resId = toMms  ? R.string.converting_to_picture_message
                           : R.string.converting_to_text_message;
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void convertMessageIfNeeded(int whichState, boolean set) {
        int oldState = mMessageState;
        updateState(whichState, set);

        boolean toMms;
        // If any bits are set in the new state and none were set in the
        // old state, we need to convert to MMS.
        if ((oldState == 0) && (mMessageState != 0)) {
            toMms = true;
        } else if ((oldState != 0) && (mMessageState == 0)) {
            // Vice versa, to SMS.
            toMms = false;
        } else {
            // If we changed state but didn't change SMS vs. MMS status,
            // there is nothing to do.
            return;
        }

        toastConvertInfo(toMms);
        convertMessage(toMms);
    }

    private class DeleteMessageListener implements OnClickListener {
        private final Uri mDeleteUri;
        private final boolean mDeleteAll;

        public DeleteMessageListener(Uri uri, boolean all) {
            mDeleteUri = uri;
            mDeleteAll = all;
        }

        public DeleteMessageListener(long msgId, String type) {
            if ("mms".equals(type)) {
                mDeleteUri = ContentUris.withAppendedId(
                        Mms.CONTENT_URI, msgId);
            } else {
                mDeleteUri = ContentUris.withAppendedId(
                        Sms.CONTENT_URI, msgId);
            }
            mDeleteAll = false;
        }

        public void onClick(DialogInterface dialog, int whichButton) {
            SqliteWrapper.delete(ComposeMessageActivity.this, mContentResolver,
                                mDeleteUri, null, null);

            // Update the notification for new messages since they
            // may be deleted.
            MessagingNotification.updateNewMessageIndicator(
                    ComposeMessageActivity.this);
            // Update the notification for failed messages since they
            // may be deleted.
            MessagingNotification.updateSendFailedNotification(
                    ComposeMessageActivity.this);

            if (mDeleteAll) {
                discardTemporaryMessage();
                ComposeMessageActivity.this.finish();
            }
        }
    }

    private class ResizeButtonListener implements OnClickListener {
        private final Uri mImageUri;
        private final ResizeImageResultCallback
        mCallback = new ResizeImageResultCallback() {
            public void onResizeResult(PduPart part) {
                convertMessageIfNeeded(HAS_ATTACHMENT, true);

                Context context = ComposeMessageActivity.this;
                Resources r = context.getResources();
                try {
                    long messageId = ContentUris.parseId(mMessageUri);
                    Uri newUri = mPersister.persistPart(part, messageId);
                    mAttachmentEditor.changeImage(newUri);
                    mAttachmentEditor.setAttachment(
                            mSlideshow, AttachmentEditor.IMAGE_ATTACHMENT);
                } catch (MmsException e) {
                    Toast.makeText(context,
                            r.getString(R.string.failed_to_add_media, getPictureString()),
                            Toast.LENGTH_SHORT).show();
                } catch (UnsupportContentTypeException e) {
                    MessageUtils.showErrorDialog(context,
                            r.getString(R.string.unsupported_media_format, getPictureString()),
                            r.getString(R.string.select_different_media, getPictureString()));
                } catch (ResolutionException e) {
                    MessageUtils.showErrorDialog(context,
                            r.getString(R.string.failed_to_resize_image),
                            r.getString(R.string.resize_image_error_information));
                } catch (ExceedMessageSizeException e) {
                    MessageUtils.showErrorDialog(context,
                            r.getString(R.string.exceed_message_size_limitation),
                            r.getString(R.string.failed_to_add_media, getPictureString()));
                }
            }
        };

        public ResizeButtonListener(Uri uri) {
            mImageUri = uri;
        }

        public void onClick(DialogInterface dialog, int which) {
            MessageUtils.resizeImageAsync(ComposeMessageActivity.this,
                    mImageUri, mAttachmentEditorHandler, mCallback);
        }
    }

    private void discardTemporaryMessage() {
        if (requiresMms()) {
            if (mMessageUri != null) {
                SqliteWrapper.delete(ComposeMessageActivity.this,
                        mContentResolver, mMessageUri, null, null);
            }
        } else if (mThreadId > 0) {
            deleteTemporarySmsMessage(mThreadId);
        }

        // Don't save this message as a draft, even if it is only an SMS.
        mMsgText = "";
    }

    private class DiscardDraftListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            discardTemporaryMessage();
            goToConversationList();
            finish();
        }
    }

    private class SendIgnoreInvalidRecipientListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            sendMessage();
        }
    }

    private class CancelSendingListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            if ((mRecipientsEditor != null) &&
                    (mRecipientsEditor.getVisibility() == View.VISIBLE)) {
                mRecipientsEditor.requestFocus();
            }
        }
    }

    private void confirmSendMessageIfNeeded() {
        if (mRecipientList.hasInvalidRecipient()) {
            if (mRecipientList.hasValidRecipient()) {
                String title = getResourcesString(R.string.has_invalid_recipient,
                        mRecipientList.getInvalidRecipientString());
                new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(title)
                    .setMessage(R.string.invalid_recipient_message)
                    .setPositiveButton(R.string.try_to_send,
                            new SendIgnoreInvalidRecipientListener())
                    .setNegativeButton(R.string.no, new CancelSendingListener())
                    .show();
            } else {
                new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.cannot_send_message)
                    .setMessage(R.string.cannot_send_message_reason)
                    .setPositiveButton(R.string.yes, new CancelSendingListener())
                    .show();
            }
        } else {
            sendMessage();
        }
    }

    private final OnFocusChangeListener mRecipientsFocusListener = new OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus) {
                convertMessageIfNeeded(RECIPIENTS_REQUIRE_MMS, recipientsRequireMms());
                updateWindowTitle();
            }
        }
    };

    private final TextWatcher mRecipientsWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void afterTextChanged(Editable s) {
            int oldValidCount = mRecipientList.size();
            int oldTotal = mRecipientList.countInvalidRecipients() + oldValidCount;

            // Refresh our local copy of the recipient list.
            mRecipientList = mRecipientsEditor.getRecipientList();
            // If we have gone to zero recipients, disable send button.
            updateSendButtonState();

            // If a recipient has been added or deleted (or an invalid one has become valid),
            // convert the message if necessary.  This causes us to "drop" conversions when
            // a recipient becomes invalid, but we check again upon losing focus to ensure our
            // state doesn't get too stale.  This keeps us from thrashing around between
            // valid and invalid when typing in an email address.
            int newValidCount = mRecipientList.size();
            int newTotal = mRecipientList.countInvalidRecipients() + newValidCount;
            if ((oldTotal != newTotal) || (newValidCount > oldValidCount)) {
                convertMessageIfNeeded(RECIPIENTS_REQUIRE_MMS, recipientsRequireMms());
            }

            String recipients = s.toString();
            if (recipients.endsWith(",") || recipients.endsWith(", ")) {
                updateWindowTitle();
            }
        }
    };

    private final OnCreateContextMenuListener mRecipientsMenuCreateListener =
        new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            if (menuInfo != null) {
                Recipient r = ((RecipientContextMenuInfo) menuInfo).recipient;
                RecipientsMenuClickListener l = new RecipientsMenuClickListener(r);

                if (r.person_id != -1) {
                    menu.add(0, MENU_VIEW_CONTACT, 0, R.string.menu_view_contact)
                            .setOnMenuItemClickListener(l);
                } else {
                    menu.add(0, MENU_ADD_TO_CONTACTS, 0, R.string.menu_add_to_contacts)
                            .setOnMenuItemClickListener(l);
                }
            }
        }
    };

    private final class RecipientsMenuClickListener implements MenuItem.OnMenuItemClickListener {
        private final Recipient mRecipient;

        RecipientsMenuClickListener(Recipient recipient) {
            mRecipient = recipient;
        }

        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                // Context menu handlers for the recipients editor.
                case MENU_VIEW_CONTACT: {
                    Uri uri = ContentUris.withAppendedId(People.CONTENT_URI,
                            mRecipient.person_id);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    ComposeMessageActivity.this.startActivity(intent);
                    return true;
                }
                case MENU_ADD_TO_CONTACTS: {
                    Intent intent = new Intent(Insert.ACTION, People.CONTENT_URI);
                    if (Recipient.isPhoneNumber(mRecipient.number)) {
                        intent.putExtra(Insert.PHONE, mRecipient.number);
                    } else {
                        intent.putExtra(Insert.EMAIL, mRecipient.number);
                    }
                    ComposeMessageActivity.this.startActivity(intent);
                    return true;
                }
            }
            return false;
        }
    }

    private void addPositionBasedMenuItems(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo");
            return;
        }
        final int position = info.position;

        addUriSpecificMenuItems(menu, v, position);
    }

    private Uri getSelectedUriFromMessageList(ListView listView, int position) {
        // If the context menu was opened over a uri, get that uri.
        MessageListItem msglistItem = (MessageListItem) listView.getChildAt(position);
        if (msglistItem == null) {
            // FIXME: Should get the correct view. No such interface in ListView currently
            // to get the view by position. The ListView.getChildAt(position) cannot
            // get correct view since the list doesn't create one child for each item.
            // And if setSelection(position) then getSelectedView(),
            // cannot get corrent view when in touch mode.
            return null;
        }

        TextView textView;
        CharSequence text = null;
        int selStart = -1;
        int selEnd = -1;

        //check if message sender is selected
        textView = (TextView) msglistItem.findViewById(R.id.text_view);
        if (textView != null) {
            text = textView.getText();
            selStart = textView.getSelectionStart();
            selEnd = textView.getSelectionEnd();
        }

        if (selStart == -1) {
            //sender is not being selected, it may be within the message body
            textView = (TextView) msglistItem.findViewById(R.id.body_text_view);
            if (textView != null) {
                text = textView.getText();
                selStart = textView.getSelectionStart();
                selEnd = textView.getSelectionEnd();
            }
        }

        // Check that some text is actually selected, rather than the cursor
        // just being placed within the TextView.
        if (selStart != selEnd) {
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            URLSpan[] urls = ((Spanned) text).getSpans(min, max,
                                                        URLSpan.class);

            if (urls.length == 1) {
                return Uri.parse(urls[0].getURL());
            }
        }

        //no uri was selected
        return null;
    }

    private void addUriSpecificMenuItems(ContextMenu menu, View v, int position) {
        Uri uri = getSelectedUriFromMessageList((ListView) v, position);

        if (uri != null) {
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.CATEGORY_SELECTED_ALTERNATIVE);
            menu.addIntentOptions(0, 0, 0,
                    new android.content.ComponentName(this, ComposeMessageActivity.class),
                    null, intent, 0, null);
        }
    }

    private final void addReplyMenuItems(
            ContextMenu menu, MsgListMenuClickListener l, String recipient) {
        if (Sms.isEmailAddress(recipient)) {
            String sendEmailString = getString(
                    R.string.menu_send_email).replace("%s", recipient);

            menu.add(0, MENU_SEND_EMAIL, 0, sendEmailString)
                    .setOnMenuItemClickListener(l);
        } else {
            String callBackString = getString(
                    R.string.menu_call_back).replace("%s", recipient);

            menu.add(0, MENU_CALL_BACK, 0, callBackString)
                    .setOnMenuItemClickListener(l);
        }
    }

    private final OnCreateContextMenuListener mMsgListMenuCreateListener =
        new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            String type = mMsgListCursor.getString(COLUMN_MSG_TYPE);
            long msgId = mMsgListCursor.getLong(COLUMN_ID);

            addPositionBasedMenuItems(menu, v, menuInfo);

            MessageItem msgItem = mMsgListAdapter.getCachedMessageItem(type, msgId);
            if (msgItem == null) {
                try {
                    msgItem = mMsgListAdapter.cacheMessageItem(type, mMsgListCursor);
                } catch (MmsException e) {
                    Log.e(TAG, e.getMessage());
                }
            }

            if (msgItem == null) {
                Log.e(TAG, "Cannot load message item for type = " + type
                        + ", msgId = " + msgId);
                return;
            }

            String recipient = msgItem.mAddress;

            MsgListMenuClickListener l = new MsgListMenuClickListener();
            if (msgItem.isMms()) {
                switch (msgItem.mBoxId) {
                    case Mms.MESSAGE_BOX_INBOX:
                        break;
                    case Mms.MESSAGE_BOX_DRAFTS:
                    case Mms.MESSAGE_BOX_OUTBOX:
                        menu.add(0, MENU_EDIT_MESSAGE, 0, R.string.menu_edit)
                                .setOnMenuItemClickListener(l);
                        break;
                }
                switch (msgItem.mAttachmentType) {
                    case AttachmentEditor.TEXT_ONLY:
                        break;
                    case AttachmentEditor.IMAGE_ATTACHMENT:
                        menu.add(0, MENU_VIEW_PICTURE, 0, R.string.view_picture)
                                .setOnMenuItemClickListener(l);
                        break;
                    case AttachmentEditor.SLIDESHOW_ATTACHMENT:
                    default:
                        menu.add(0, MENU_VIEW_SLIDESHOW, 0, R.string.view_slideshow)
                                .setOnMenuItemClickListener(l);
                        break;
                }
            } else {
                // Message type is sms.
                if ((msgItem.mBoxId == Sms.MESSAGE_TYPE_OUTBOX) ||
                        (msgItem.mBoxId == Sms.MESSAGE_TYPE_FAILED)) {
                    menu.add(0, MENU_EDIT_MESSAGE, 0, R.string.menu_edit)
                            .setOnMenuItemClickListener(l);
                }
            }
            addReplyMenuItems(menu, l, recipient);

            // Forward is not available for undownloaded messages.
            if (msgItem.isDownloaded()) {
                menu.add(0, MENU_FORWARD_MESSAGE, 0, R.string.menu_forward)
                        .setOnMenuItemClickListener(l);
            }

            menu.add(0, MENU_VIEW_MESSAGE_DETAILS, 0, R.string.view_message_details)
                    .setOnMenuItemClickListener(l);
            menu.add(0, MENU_DELETE_MESSAGE, 0, R.string.delete_message)
                    .setOnMenuItemClickListener(l);
            if (msgItem.mDeliveryReport || msgItem.mReadReport) {
                menu.add(0, MENU_DELIVERY_REPORT, 0, R.string.view_delivery_report)
                        .setOnMenuItemClickListener(l);
            }
        }
    };

    private void editMessageItem(MessageItem msgItem) {
        if ("sms".equals(msgItem.mType)) {
            editSmsMessageItem(msgItem);
        } else {
            editMmsMessageItem(msgItem);
        }
    }

    private void editSmsMessageItem(MessageItem msgItem) {
        // Delete the old undelivered SMS and load its content.
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgItem.mMsgId);
        SqliteWrapper.delete(ComposeMessageActivity.this,
                  mContentResolver, uri, null, null);
        mMsgText = msgItem.mBody;
    }

    private void editMmsMessageItem(MessageItem msgItem) {
        if (mMessageUri != null) {
            // Delete the former draft.
            SqliteWrapper.delete(ComposeMessageActivity.this,
                    mContentResolver, mMessageUri, null, null);
        }
        mMessageUri = msgItem.mMessageUri;
        ContentValues values = new ContentValues(1);
        values.put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_DRAFTS);
        SqliteWrapper.update(ComposeMessageActivity.this,
                mContentResolver, mMessageUri, values, null, null);

        updateState(RECIPIENTS_REQUIRE_MMS, recipientsRequireMms());
        if (!TextUtils.isEmpty(msgItem.mSubject)) {
            mSubject = msgItem.mSubject;
            updateState(HAS_SUBJECT, true);
        }

        if (msgItem.mAttachmentType > AttachmentEditor.TEXT_ONLY) {
            updateState(HAS_ATTACHMENT, true);
        }

        convertMessage(true);
        if (!TextUtils.isEmpty(mSubject)) {
            mSubjectTextEditor.setVisibility(View.VISIBLE);
            mTopPanel.setVisibility(View.VISIBLE);
        } else {
            mSubjectTextEditor.setVisibility(View.GONE);
            hideTopPanelIfNecessary();
        }
    }

    /**
     * Context menu handlers for the message list view.
     */
    private final class MsgListMenuClickListener implements MenuItem.OnMenuItemClickListener {
        public boolean onMenuItemClick(MenuItem item) {
            String type = mMsgListCursor.getString(COLUMN_MSG_TYPE);
            long msgId = mMsgListCursor.getLong(COLUMN_ID);
            MessageItem msgItem = getMessageItem(type, msgId);

            if (msgItem == null) {
                return false;
            }

            switch (item.getItemId()) {
                case MENU_EDIT_MESSAGE: {
                    editMessageItem(msgItem);
                    int attachmentType = requiresMms()
                            ? MessageUtils.getAttachmentType(mSlideshow)
                            : AttachmentEditor.TEXT_ONLY;
                    drawBottomPanel(attachmentType);
                    return true;
                }
                case MENU_FORWARD_MESSAGE: {
                    Uri uri = null;
                    Intent intent = new Intent(ComposeMessageActivity.this,
                                               ComposeMessageActivity.class);

                    intent.putExtra("compose_mode", true);
                    intent.putExtra("exit_on_sent", true);
                    if (type.equals("sms")) {
                        uri = ContentUris.withAppendedId(
                                Sms.CONTENT_URI, msgId);
                        intent.putExtra("sms_body", msgItem.mBody);
                    } else {
                        SendReq sendReq = new SendReq();
                        String subject = getString(R.string.forward_prefix);
                        if (msgItem.mSubject != null) {
                            subject += msgItem.mSubject;
                        }
                        sendReq.setSubject(new EncodedStringValue(subject));
                        sendReq.setBody(msgItem.mSlideshow.makeCopy(
                                ComposeMessageActivity.this));

                        try {
                            // Implicitly copy the parts of the message here.
                            uri = mPersister.persist(sendReq, Mms.Draft.CONTENT_URI);
                        } catch (MmsException e) {
                            Log.e(TAG, "Failed to copy message: " + msgItem.mMessageUri, e);
                            Toast.makeText(ComposeMessageActivity.this,
                                    R.string.cannot_save_message, Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        intent.putExtra("msg_uri", uri);
                        intent.putExtra("subject", subject);
                    }
                    startActivityIfNeeded(intent, -1);
                    return true;
                }
                case MENU_VIEW_PICTURE:
                    // FIXME: Use SlideshowActivity to view image for the time being.
                    // As described in UI spec, Pressing an inline attachment will
                    // open up the full view of the attachment in its associated app
                    // (here should the pictures app).
                    // But the <ViewImage> would only show images in MediaStore.
                    // Should we save a copy to MediaStore temporarily for displaying?
                case MENU_VIEW_SLIDESHOW: {
                    Intent intent = new Intent(ComposeMessageActivity.this,
                            SlideshowActivity.class);
                    intent.setData(ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
                    startActivity(intent);
                    return true;
                }
                case MENU_VIEW_MESSAGE_DETAILS: {
                    String messageDetails = MessageUtils.getMessageDetails(
                            ComposeMessageActivity.this, mMsgListCursor, msgItem.mMessageSize);
                    new AlertDialog.Builder(ComposeMessageActivity.this)
                            .setTitle(R.string.message_details_title)
                            .setMessage(messageDetails)
                            .setPositiveButton(android.R.string.ok, null)
                            .setCancelable(false)
                            .show();
                    return true;
                }
                case MENU_DELETE_MESSAGE: {
                    DeleteMessageListener l = new DeleteMessageListener(
                            msgItem.mMessageUri, false);
                    confirmDeleteDialog(l, false);
                    return true;
                }
                case MENU_DELIVERY_REPORT:
                    showDeliveryReport(msgId, type);
                    return true;

                case MENU_CALL_BACK: {
                    String address = msgItem.mAddress;

                    if (Sms.isEmailAddress(address)) {
                        return false;
                    } else {
                        startActivity(
                                new Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:" + address)));
                        return true;
                    }
                }

                case MENU_SEND_EMAIL: {
                    String address = msgItem.mAddress;

                    if (Sms.isEmailAddress(address)) {
                        startActivity(
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("mailto:" + address)));
                        return true;
                    } else {
                        return false;
                    }
                }

                default:
                    return false;
            }
        }
    }

    private void showDeliveryReport(long messageId, String type) {
        Intent intent = new Intent(this, DeliveryReportActivity.class);
        intent.putExtra("message_id", messageId);
        intent.putExtra("message_type", type);

        startActivity(intent);
    }

    private final IntentFilter mHttpProgressFilter = new IntentFilter(PROGRESS_STATUS_ACTION);

    private final BroadcastReceiver mHttpProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PROGRESS_STATUS_ACTION.equals(intent.getAction())) {
                long token = intent.getLongExtra("token",
                                    SendingProgressTokenManager.NO_TOKEN);
                if (token != mThreadId) {
                    return;
                }

                int progress = intent.getIntExtra("progress", 0);
                switch (progress) {
                    case PROGRESS_START:
                        setProgressBarVisibility(true);
                        break;
                    case PROGRESS_ABORT:
                    case PROGRESS_COMPLETE:
                        setProgressBarVisibility(false);
                        break;
                    default:
                        setProgress(100 * progress);
                }
            }
        }
    };

    //==========================================================
    // Static methods
    //==========================================================

    private static SlideshowModel createNewMessage(Context context) {
        SlideshowModel slideshow = SlideshowModel.createNew(context);
        SlideModel slide = new SlideModel(slideshow);

        TextModel text = new TextModel(
                context, ContentType.TEXT_PLAIN, "text_0.txt",
                slideshow.getLayout().getTextRegion());
        slide.add(text);

        slideshow.add(slide);
        return slideshow;
    }

    private static EncodedStringValue[] encodeStrings(String[] array) {
        int count = array.length;
        if (count > 0) {
            EncodedStringValue[] encodedArray = new EncodedStringValue[count];
            for (int i = 0; i < count; i++) {
                encodedArray[i] = new EncodedStringValue(array[i]);
            }
            return encodedArray;
        }
        return null;
    }

    // Get the recipients editor ready to be displayed onscreen.
    private void initRecipientsEditor() {
        ViewStub stub = (ViewStub)findViewById(R.id.recipients_editor_stub);
        mRecipientsEditor = (RecipientsEditor) stub.inflate();

        mRecipientsEditor.setAdapter(new RecipientsAdapter(this));
        mRecipientsEditor.populate(mRecipientList);
        mRecipientsEditor.setOnCreateContextMenuListener(mRecipientsMenuCreateListener);
        mRecipientsEditor.addTextChangedListener(mRecipientsWatcher);
        mRecipientsEditor.setOnFocusChangeListener(mRecipientsFocusListener);
        mRecipientsEditor.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(RECIPIENTS_MAX_LENGTH) });

        mTopPanel.setVisibility(View.VISIBLE);
    }

    //==========================================================
    // Activity methods
    //==========================================================

    private boolean isFailedToDeliver() {
        Intent intent = getIntent();
        return (intent != null) && intent.getBooleanExtra("undelivered_flag", false);
    }

    private static final String[] MMS_DRAFT_PROJECTION = {
            Mms._ID,        // 0
            Mms.SUBJECT     // 1
        };

    private static final int MMS_ID_INDEX       = 0;
    private static final int MMS_SUBJECT_INDEX  = 1;

    private Cursor queryMmsDraft(long threadId) {
        final String selection = Mms.THREAD_ID + " = " + threadId;
        return SqliteWrapper.query(this, mContentResolver,
                    Mms.Draft.CONTENT_URI, MMS_DRAFT_PROJECTION,
                    selection, null, null);
    }

    private void loadMmsDraftIfNeeded() {
        Cursor cursor = queryMmsDraft(mThreadId);
        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    mMessageUri = ContentUris.withAppendedId(Mms.Draft.CONTENT_URI,
                                        cursor.getLong(MMS_ID_INDEX));
                    mSubject = cursor.getString(MMS_SUBJECT_INDEX);
                    if (!TextUtils.isEmpty(mSubject)) {
                        updateState(HAS_SUBJECT, true);
                    }

                    if (!requiresMms()) {
                        // it is an MMS draft, since it has no subject or
                        // multiple recipients, it must have an attachment
                        updateState(HAS_ATTACHMENT, true);
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.compose_message_activity);
        setProgressBarVisibility(false);
        setTitle("");

        // Initialize members for UI elements.
        initResourceRefs();

        mContentResolver = getContentResolver();
        mPersister = PduPersister.getPduPersister(this);

        // Read parameters or previously saved state of this activity.
        initActivityState(savedInstanceState, getIntent());

        if (LOCAL_LOGV) {
            Log.v(TAG, "onCreate(): savedInstanceState = " + savedInstanceState);
            Log.v(TAG, "onCreate(): intent = " + getIntent());
            Log.v(TAG, "onCreate(): mThreadId = " + mThreadId);
            Log.v(TAG, "onCreate(): mMessageUri = " + mMessageUri);
        }

        // Parse the recipient list.
        mRecipientList = RecipientList.from(mExternalAddress, this);
        updateState(RECIPIENTS_REQUIRE_MMS, recipientsRequireMms());

        if (isFailedToDeliver()) {
            // Show a pop-up dialog to inform user the message was
            // failed to deliver.
            undeliveredMessageDialog(getMessageDate(mMessageUri));
        }
        loadMmsDraftIfNeeded();

        // Initialize MMS-specific stuff if we need to.
        if ((mMessageUri != null) || requiresMms()) {
            convertMessage(true);

            if (!TextUtils.isEmpty(mSubject)) {
                mSubjectTextEditor.setVisibility(View.VISIBLE);
                mTopPanel.setVisibility(View.VISIBLE);
            } else {
                mSubjectTextEditor.setVisibility(View.GONE);
                hideTopPanelIfNecessary();
            }
        } else if (isEmptySms()) {
            mMsgText = readTemporarySmsMessage(mThreadId);
        }

        // If we are in an existing thread and we are not in "compose mode",
        // start up the message list view.
        if ((mThreadId > 0L) && !mComposeMode) {
            initMessageList(false);
        } else {
            // Otherwise, show the recipients editor.
            initRecipientsEditor();
        }

        int attachmentType = requiresMms()
                ? MessageUtils.getAttachmentType(mSlideshow)
                : AttachmentEditor.TEXT_ONLY;
        drawBottomPanel(attachmentType);

        updateSendButtonState();

        handleSendIntent(getIntent());

        mTopPanel.setFocusable(false);

        Configuration config = getResources().getConfiguration();
        mIsKeyboardOpen = config.keyboardHidden == KEYBOARDHIDDEN_NO;
        onKeyboardStateChanged(mIsKeyboardOpen);

        if (TRACE) {
            android.os.Debug.startMethodTracing("compose");
        }
    }

    private void hideTopPanelIfNecessary() {
        if ( (((mSubjectTextEditor != null) &&
                (mSubjectTextEditor.getVisibility() != View.VISIBLE)) ||
                (mSubjectTextEditor == null)) &&
                (((mRecipientsEditor != null) &&
                (mRecipientsEditor.getVisibility() != View.VISIBLE)) ||
                (mRecipientsEditor == null))) {
            mTopPanel.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);

        long oldThreadId = mThreadId;
        boolean oldIsMms = requiresMms();
        mMessageState = 0;
        String oldText = mMsgText;

        // Read parameters or previously saved state of this activity.
        initActivityState(null, intent);

        if (LOCAL_LOGV) {
            Log.v(TAG, "onNewIntent(): intent = " + getIntent());
            Log.v(TAG, "onNewIntent(): mThreadId = " + mThreadId);
            Log.v(TAG, "onNewIntent(): mMessageUri = " + mMessageUri);
        }

        if (mThreadId != oldThreadId) {
            // Save the old message as a draft.
            if (oldIsMms) {
                // Save the old temporary message if necessary.
                if ((mMessageUri != null) && isPreparedForSending()) {
                    try {
                        updateTemporaryMmsMessage();
                    } catch (MmsException e) {
                        Log.e(TAG, "Cannot update temporary message.", e);
                    }
                }
            } else {
                if (oldThreadId <= 0) {
                    oldThreadId = getOrCreateThreadId(mRecipientList.getToNumbers());
                }
                updateTemporarySmsMessage(oldThreadId, oldText);
            }

            // Refresh the recipient list.
            mRecipientList = RecipientList.from(mExternalAddress, this);
            updateState(RECIPIENTS_REQUIRE_MMS, recipientsRequireMms());

            if ((mThreadId > 0L) && !mComposeMode) {
                // If we have already initialized the recipients editor, just
                // hide it in the display.
                if (mRecipientsEditor != null) {
                    mRecipientsEditor.setVisibility(View.GONE);
                    hideTopPanelIfNecessary();
                }
                initMessageList(false);
            } else {
                initRecipientsEditor();
            }

            boolean isMms = (mMessageUri != null) || requiresMms();
            if (isMms != oldIsMms) {
                convertMessage(isMms);
            }

            if (isMms) {
                // Initialize subject editor.
                if (!TextUtils.isEmpty(mSubject)) {
                    mSubjectTextEditor.setText(mSubject);
                    mSubjectTextEditor.setVisibility(View.VISIBLE);
                    mTopPanel.setVisibility(View.VISIBLE);
                } else {
                    mSubjectTextEditor.setVisibility(View.GONE);
                    hideTopPanelIfNecessary();
                }

                try {
                    mSlideshow = createNewMessage(this);
                    mMessageUri = createTemporaryMmsMessage();
                    Toast.makeText(this, R.string.message_saved_as_draft,
                            Toast.LENGTH_SHORT).show();
                } catch (MmsException e) {
                    Log.e(TAG, "Cannot create new slideshow and temporary message.");
                    finish();
                }
            } else if (isEmptySms()) {
                mMsgText = readTemporarySmsMessage(mThreadId);
            }

            int attachmentType = requiresMms() ? MessageUtils.getAttachmentType(mSlideshow)
                                               : AttachmentEditor.TEXT_ONLY;
            drawBottomPanel(attachmentType);

            if (mMsgListCursor != null) {
                mMsgListCursor.close();
                mMsgListCursor = null;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        updateWindowTitle();
        initFocus();

        // Register a BroadcastReceiver to listen on HTTP I/O process.
        registerReceiver(mHttpProgressReceiver, mHttpProgressFilter);

        if (mMsgListAdapter != null) {
            mMsgListAdapter.registerObservers();
            synchronized (mMsgListCursorLock) {
                if (mMsgListCursor == null) {
                    startMsgListQuery();
                } else {
                    SqliteWrapper.requery(this, mMsgListCursor);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mThreadId > 0) {
            MessageUtils.handleReadReport(
                    ComposeMessageActivity.this, mThreadId,
                    PduHeaders.READ_STATUS_READ, null);
            MessageUtils.markAsRead(this, mThreadId);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mThreadId > 0L) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "ONFREEZE: thread_id: " + mThreadId);
            }
            outState.putLong("thread_id", mThreadId);
        }

        if (LOCAL_LOGV) {
            Log.v(TAG, "ONFREEZE: address: " + mRecipientList.serialize());
        }
        outState.putString("address", mRecipientList.serialize());

        if (requiresMms()) {
            if ((mSubjectTextEditor != null)
                    && (View.VISIBLE == mSubjectTextEditor.getVisibility())) {
                outState.putString("subject", mSubjectTextEditor.getText().toString());
            }

            if (mMessageUri != null) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "ONFREEZE: mMessageUri: " + mMessageUri);
                }
                try {
                    updateTemporaryMmsMessage();
                } catch (MmsException e) {
                    Log.e(TAG, "Cannot update message.", e);
                }
                outState.putParcelable("msg_uri", mMessageUri);
            }
        } else {
            outState.putString("sms_body", mMsgText);
            if (mThreadId <= 0) {
                mThreadId = getOrCreateThreadId(mRecipientList.getToNumbers());
            }
            updateTemporarySmsMessage(mThreadId, mMsgText);
        }

        if (mComposeMode) {
            outState.putBoolean("compose_mode", mComposeMode);
        }

        if (mExitOnSent) {
            outState.putBoolean("exit_on_sent", mExitOnSent);
        }
    }

    private boolean isEmptyMessage() {
        if (requiresMms()) {
            return isEmptyMms();
        }
        return isEmptySms();
    }

    private boolean isEmptySms() {
        return TextUtils.isEmpty(mMsgText);
    }

    private boolean isEmptyMms() {
        return !(hasText() || hasSubject() || hasAttachment());
    }

    private boolean needSaveAsMms() {
        // subject editor is visible without any contents.
        if ( (mMessageState == HAS_SUBJECT) && !hasSubject()) {
            convertMessage(false);
            return false;
        }
        return requiresMms();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isFinishing()) {
            if (hasValidRecipient()) {
                if (needSaveAsMms()) {
                    if (mMessageUri != null) {
                        if (isEmptyMms()) {
                            SqliteWrapper.delete(ComposeMessageActivity.this,
                                    mContentResolver, mMessageUri, null, null);
                        } else {
                            mThreadId = getOrCreateThreadId(mRecipientList.getToNumbers());
                            try {
                                updateTemporaryMmsMessage();
                                Toast.makeText(this, R.string.message_saved_as_draft,
                                        Toast.LENGTH_SHORT).show();
                            } catch (MmsException e) {
                                Log.e(TAG, "Cannot update message.", e);
                            }
                        }
                    }
                } else {
                    if (isEmptySms()) {
                        if (mThreadId > 0) {
                            deleteTemporarySmsMessage(mThreadId);
                        }
                    } else {
                        mThreadId = getOrCreateThreadId(mRecipientList.getToNumbers());
                        updateTemporarySmsMessage(mThreadId, mMsgText);
                        Toast.makeText(this, R.string.message_saved_as_draft,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                discardTemporaryMessage();
            }
        }

        MessageUtils.markAsRead(this, mThreadId);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mMsgListAdapter != null) {
            synchronized (mMsgListCursorLock) {
                mMsgListCursor = null;
                mMsgListAdapter.changeCursor(null);
            }
        }

        // Cleanup the BroadcastReceiver.
        unregisterReceiver(mHttpProgressReceiver);
    }

    @Override
    protected void onDestroy() {
        if (TRACE) {
            android.os.Debug.stopMethodTracing();
        }

        super.onDestroy();

        if (mMsgListCursor != null) {
            mMsgListCursor.close();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (LOCAL_LOGV) {
            Log.v(TAG, "onConfigurationChanged: " + newConfig);
        }

        mIsKeyboardOpen = newConfig.keyboardHidden == KEYBOARDHIDDEN_NO;
        onKeyboardStateChanged(mIsKeyboardOpen);
    }

    private void onKeyboardStateChanged(boolean isKeyboardOpen) {
        // If the keyboard is hidden, don't show focus highlights for
        // things that cannot receive input.
        if (isKeyboardOpen) {
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setFocusableInTouchMode(true);
            }
            if (mSubjectTextEditor != null) {
                mSubjectTextEditor.setFocusableInTouchMode(true);
            }
            mTextEditor.setFocusableInTouchMode(true);
            mTextEditor.setHint(R.string.type_to_compose_text_enter_to_send);
            initFocus();
        } else {
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setFocusable(false);
            }
            if (mSubjectTextEditor != null) {
                mSubjectTextEditor.setFocusable(false);
            }
            mTextEditor.setFocusable(false);
            mTextEditor.setHint(R.string.open_keyboard_to_compose_message);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        checkPendingNotification();
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        checkPendingNotification();
        return super.dispatchTrackballEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        checkPendingNotification();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if ((mMsgListAdapter != null) && mMsgListView.isFocused()) {
                    Cursor cursor;
                    try {
                        cursor = (Cursor) mMsgListView.getSelectedItem();
                    } catch (ClassCastException e) {
                        Log.e(TAG, "Unexpected ClassCastException.", e);
                        return super.onKeyDown(keyCode, event);
                    }

                    if (cursor != null) {
                        DeleteMessageListener l = new DeleteMessageListener(
                                cursor.getLong(COLUMN_ID),
                                cursor.getString(COLUMN_MSG_TYPE));
                        confirmDeleteDialog(l, false);
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (isPreparedForSending()) {
                    confirmSendMessageIfNeeded();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                exitComposeMessageActivity(new Runnable() {
                    public void run() {
                        finish();
                    }
                });
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void exitComposeMessageActivity(final Runnable exit) {
        if (mThreadId != -1) {
            if (isComposingNewMessage()) {
                if (hasValidRecipient()) {
                    exit.run();
                } else {
                    if (isEmptyMessage()) {
                        discardTemporaryMessage();
                        exit.run();
                    } else {
                        MessageUtils.showDiscardDraftConfirmDialog(this,
                                new DiscardDraftListener());
                    }
                }
            } else {
                exit.run();
            }
        }
    }

    private void goToConversationList() {
        finish();
        startActivity(new Intent(this, ConversationList.class));
    }

    // FIXME: need to optimize
    private boolean isComposingNewMessage() {
        return (null != mRecipientsEditor)
                    && (View.VISIBLE == mRecipientsEditor.getVisibility());
    }

    public void onAttachmentChanged(int newType, int oldType) {
        drawBottomPanel(newType);
        if (newType > AttachmentEditor.TEXT_ONLY) {
            if (!requiresMms() && !mComposeMode) {
                toastConvertInfo(true);
            }
            updateState(HAS_ATTACHMENT, true);
        } else {
            convertMessageIfNeeded(HAS_ATTACHMENT, false);
        }
        updateSendButtonState();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        menu.add(0, MENU_CONVERSATION_LIST, 0, R.string.all_threads).setIcon(
                    R.drawable.ic_menu_friendslist);

        if ((mSubjectTextEditor == null) || (mSubjectTextEditor.getVisibility() != View.VISIBLE)) {
            menu.add(0, MENU_ADD_SUBJECT, 0, R.string.add_subject).setIcon(
                    android.R.drawable.ic_menu_edit);
        }

        if ((mAttachmentEditor == null) ||
                (mAttachmentEditor.getAttachmentType() == AttachmentEditor.TEXT_ONLY)) {
            menu.add(0, MENU_ADD_ATTACHMENT, 0, R.string.add_attachment).setIcon(
                    R.drawable.ic_menu_attachment);
        }

        if (isPreparedForSending()) {
            menu.add(0, MENU_SEND, 0, R.string.send).setIcon(R.drawable.ic_menu_send);
        }

        if (mThreadId > 0L) {
            menu.add(0, MENU_COMPOSE_NEW, 0, R.string.menu_compose_new).setIcon(
                    R.drawable.ic_menu_compose);
            // Removed search as part of b/1205708
            //menu.add(0, MENU_SEARCH, 0, R.string.menu_search).setIcon(
            //        R.drawable.ic_menu_search);
            if ((null != mMsgListCursor) && (mMsgListCursor.getCount() > 0)) {
                menu.add(0, MENU_DELETE_THREAD, 0, R.string.delete_thread).setIcon(
                    R.drawable.ic_menu_delete);
            }
        } else {
            menu.add(0, MENU_DISCARD, 0, R.string.discard).setIcon(R.drawable.ic_menu_delete);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD_SUBJECT:
                convertMessageIfNeeded(HAS_SUBJECT, true);
                mSubjectTextEditor.setVisibility(View.VISIBLE);
                mTopPanel.setVisibility(View.VISIBLE);
                mSubjectTextEditor.requestFocus();
                break;
            case MENU_ADD_ATTACHMENT:
                // Launch the add-attachment list dialog
                showAddAttachmentDialog();
                break;
            case MENU_DISCARD:
                discardTemporaryMessage();
                finish();
                break;
            case MENU_SEND:
                if (isPreparedForSending()) {
                    confirmSendMessageIfNeeded();
                }
                break;
            case MENU_COMPOSE_NEW:
                Intent i = new Intent(this, ComposeMessageActivity.class);
                startActivity(i);
                finish();
                break;
            case MENU_SEARCH:
                onSearchRequested();
                break;
            case MENU_DELETE_THREAD:
                DeleteMessageListener l = new DeleteMessageListener(
                        getThreadUri(), true);
                confirmDeleteDialog(l, true);
                break;
            case MENU_CONVERSATION_LIST:
                exitComposeMessageActivity(new Runnable() {
                    public void run() {
                        goToConversationList();
                    }
                });
                break;
        }

        return true;
    }

    private void addAttachment(int type) {
        switch (type) {
            case AttachmentTypeSelectorAdapter.ADD_IMAGE:
                MessageUtils.selectImage(this, REQUEST_CODE_ATTACH_IMAGE);
                break;

            case AttachmentTypeSelectorAdapter.TAKE_PICTURE:
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, REQUEST_CODE_TAKE_PICTURE);
                break;

            case AttachmentTypeSelectorAdapter.ADD_VIDEO:
                MessageUtils.selectVideo(this, REQUEST_CODE_ATTACH_VIDEO);
                break;

            case AttachmentTypeSelectorAdapter.ADD_SOUND:
                MessageUtils.selectAudio(this, REQUEST_CODE_ATTACH_SOUND);
                break;

            case AttachmentTypeSelectorAdapter.RECORD_SOUND:
                MessageUtils.recordSound(this, REQUEST_CODE_RECORD_SOUND);
                break;

            case AttachmentTypeSelectorAdapter.ADD_SLIDESHOW:
                boolean wasSms = !requiresMms();

                // SlideshowEditActivity needs mMessageUri to work with.
                convertMessageIfNeeded(HAS_ATTACHMENT, true);

                if (wasSms) {
                    // If we are converting from SMS, make sure the SMS
                    // text message gets imported into the first slide.
                    TextModel text = mSlideshow.get(0).getText();
                    if (text != null) {
                        text.setText(mMsgText);
                    }
                }

                intent = new Intent(this, SlideshowEditActivity.class);
                intent.setData(mMessageUri);
                startActivityForResult(intent, REQUEST_CODE_CREATE_SLIDESHOW);
                break;

            default:
                break;
        }
    }

    private void showReplaceImageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.replace_image);

        AttachmentTypeSelectorAdapter adapter = new AttachmentTypeSelectorAdapter(
                this, AttachmentTypeSelectorAdapter.MODE_REPLACE_IMAGE);

        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                addAttachment(which);
            }
        });

        builder.show();
    }

    private void showAddAttachmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_sms_add_attachment);
        builder.setTitle(R.string.add_attachment);

        AttachmentTypeSelectorAdapter adapter = new AttachmentTypeSelectorAdapter(
                this, AttachmentTypeSelectorAdapter.MODE_WITH_SLIDESHOW);

        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                addAttachment(which);
            }
        });

        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "onActivityResult: requestCode=" + requestCode
                    + ", resultCode=" + resultCode + ", data=" + data);
        }

        if (resultCode != RESULT_OK) {
            // Make sure if there was an error that our message
            // type remains correct.
            convertMessageIfNeeded(HAS_ATTACHMENT, hasAttachment());
            return;
        }

        if (!requiresMms()) {
            convertMessage(true);
        }

        switch(requestCode) {
            case REQUEST_CODE_CREATE_SLIDESHOW:
                try {
                    // Refresh the slideshow model since it may be changed
                    // by the slideshow editor.
                    mSlideshow = SlideshowModel.createFromMessageUri(this, mMessageUri);
                } catch (MmsException e) {
                    Log.e(TAG, "Failed to load slideshow from " + mMessageUri);
                    Toast.makeText(this, getString(R.string.cannot_load_message),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Find the most suitable type for the attachment.
                int attachmentType = MessageUtils.getAttachmentType(mSlideshow);
                switch (attachmentType) {
                    case AttachmentEditor.EMPTY:
                        fixEmptySlideshow(mSlideshow);
                        attachmentType = AttachmentEditor.TEXT_ONLY;
                        // fall-through
                    case AttachmentEditor.TEXT_ONLY:
                        mAttachmentEditor.setAttachment(mSlideshow, attachmentType);
                        convertMessageIfNeeded(HAS_ATTACHMENT, false);
                        drawBottomPanel(attachmentType);
                        return;
                    default:
                        mAttachmentEditor.setAttachment(mSlideshow, attachmentType);
                        break;
                }

                drawBottomPanel(attachmentType);
                break;

            case REQUEST_CODE_TAKE_PICTURE:
                Bitmap bitmap = (Bitmap) data.getParcelableExtra("data");

                if (bitmap == null) {
                    Toast.makeText(this,
                            getResourcesString(R.string.failed_to_add_media, getPictureString()),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                addImage(bitmap);
                break;

            case REQUEST_CODE_ATTACH_IMAGE:
                addImage(data.getData());
                break;

            case REQUEST_CODE_ATTACH_VIDEO:
                try {
                    mAttachmentEditor.changeVideo(data.getData());
                    mAttachmentEditor.setAttachment(
                            mSlideshow, AttachmentEditor.VIDEO_ATTACHMENT);
                } catch (MmsException e) {
                    Log.e(TAG, "add video failed", e);
                    Toast.makeText(this,
                            getResourcesString(R.string.failed_to_add_media, getVideoString()),
                            Toast.LENGTH_SHORT).show();
                } catch (UnsupportContentTypeException e) {
                    MessageUtils.showErrorDialog(ComposeMessageActivity.this,
                            getResourcesString(R.string.unsupported_media_format, getVideoString()),
                            getResourcesString(R.string.select_different_media, getVideoString()));
                } catch (ExceedMessageSizeException e) {
                    MessageUtils.showErrorDialog(ComposeMessageActivity.this,
                            getResourcesString(R.string.exceed_message_size_limitation),
                            getResourcesString(R.string.failed_to_add_media, getVideoString()));
                }
                break;

            case REQUEST_CODE_ATTACH_SOUND:
            case REQUEST_CODE_RECORD_SOUND:
                Uri uri;
                if (requestCode == REQUEST_CODE_ATTACH_SOUND) {
                    uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (Settings.System.DEFAULT_RINGTONE_URI.equals(uri)) {
                        uri = null;
                    }
                } else {
                    uri = data.getData();
                }

                if (uri == null) {
                    convertMessageIfNeeded(HAS_ATTACHMENT, hasAttachment());
                    return;
                }

                try {
                    mAttachmentEditor.changeAudio(uri);
                    mAttachmentEditor.setAttachment(
                            mSlideshow, AttachmentEditor.AUDIO_ATTACHMENT);
                } catch (MmsException e) {
                    Log.e(TAG, "add audio failed", e);
                    Toast.makeText(this,
                            getResourcesString(R.string.failed_to_add_media, getAudioString()),
                            Toast.LENGTH_SHORT).show();
                } catch (UnsupportContentTypeException e) {
                    MessageUtils.showErrorDialog(ComposeMessageActivity.this,
                            getResourcesString(R.string.unsupported_media_format, getAudioString()),
                            getResourcesString(R.string.select_different_media, getAudioString()));
                } catch (ExceedMessageSizeException e) {
                    MessageUtils.showErrorDialog(ComposeMessageActivity.this,
                            getResourcesString(R.string.exceed_message_size_limitation),
                            getResourcesString(R.string.failed_to_add_media, getAudioString()));
                }
                break;

            default:
                // TODO
                break;
        }
        // Make sure if there was an error that our message
        // type remains correct. Excludes add image because it may be in async
        // resize process.
        if (!requiresMms() && (REQUEST_CODE_ATTACH_IMAGE != requestCode)) {
            convertMessage(false);
        }
    }

    private void addImage(Bitmap bitmap) {
        try {
            addImage(MessageUtils.saveBitmapAsPart(this, mMessageUri, bitmap));
        } catch (MmsException e) {
            handleAddImageFailure(e);
        }
    }

    private void addImage(Uri uri) {
        try {
            mAttachmentEditor.changeImage(uri);
            mAttachmentEditor.setAttachment(
                    mSlideshow, AttachmentEditor.IMAGE_ATTACHMENT);
        } catch (MmsException e) {
            handleAddImageFailure(e);
        } catch (UnsupportContentTypeException e) {
            MessageUtils.showErrorDialog(
                    ComposeMessageActivity.this,
                    getResourcesString(R.string.unsupported_media_format, getPictureString()),
                    getResourcesString(R.string.select_different_media, getPictureString()));
        } catch (ResolutionException e) {
            MessageUtils.showResizeConfirmDialog(
                    this, new ResizeButtonListener(uri),
                    new Runnable() {
                        public void run() {
                            if (!requiresMms()) {
                                convertMessage(false);
                            }
                        }
                    });
        } catch (ExceedMessageSizeException e) {
            MessageUtils.showErrorDialog(
                    ComposeMessageActivity.this,
                    getResourcesString(R.string.exceed_message_size_limitation),
                    getResourcesString(R.string.failed_to_add_media, getPictureString()));
        }
    }

    private void handleAddImageFailure(MmsException exception) {
        Log.e(TAG, "add image failed", exception);
        Toast.makeText(
                this,
                getResourcesString(R.string.failed_to_add_media, getPictureString()),
                Toast.LENGTH_SHORT).show();
    }

    private void handleSendIntent(Intent intent) {
        Bundle extras = intent.getExtras();

        if (Intent.ACTION_SEND.equals(intent.getAction()) &&
            (extras != null) &&
            extras.containsKey(Intent.EXTRA_STREAM)) {
            Uri uri = (Uri)extras.getParcelable(Intent.EXTRA_STREAM);
            if (uri != null) {
                convertMessage(true);
                addImage(uri);
            }
        }
    }

    private String getAudioString() {
        return getResourcesString(R.string.type_audio);
    }

    private String getPictureString() {
        return getResourcesString(R.string.type_picture);
    }

    private String getVideoString() {
        return getResourcesString(R.string.type_video);
    }

    private String getResourcesString(int id, String mediaName) {
        Resources r = getResources();
        return r.getString(id, mediaName);
    }

    private String getResourcesString(int id) {
        Resources r = getResources();
        return r.getString(id);
    }

    private void fixEmptySlideshow(SlideshowModel slideshow) {
        TextModel tm = new TextModel(
                this, ContentType.TEXT_PLAIN, "text_0.txt",
                slideshow.getLayout().getTextRegion());
        SlideModel slide = new SlideModel(slideshow);
        slide.add(tm);
        slideshow.add(slide);
    }

    private void drawBottomPanel(int attachmentType) {
        // Reset the counter for text editor.
        resetCounter();

        switch (attachmentType) {
            case AttachmentEditor.EMPTY:
                throw new IllegalArgumentException(
                        "Type of the attachment may not be EMPTY.");
            case AttachmentEditor.SLIDESHOW_ATTACHMENT:
                mBottomPanel.setVisibility(View.GONE);
                findViewById(R.id.attachment_editor).requestFocus();
                return;
            default:
                mBottomPanel.setVisibility(View.VISIBLE);
                String text = null;
                if (requiresMms()) {
                    TextModel tm = mSlideshow.get(0).getText();
                    if (tm != null) {
                        text = tm.getText();
                    }
                } else {
                    text = mMsgText;
                }

                if ((text != null) && !text.equals(mTextEditor.getText().toString())) {
                    mTextEditor.setText(text);
                }
        }
    }

    //==========================================================
    // Interface methods
    //==========================================================

    public void onClick(View v) {
        if ((v == mSendButton) && isPreparedForSending()) {
            confirmSendMessageIfNeeded();
        }
    }

    private final TextWatcher mTextEditorWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (count > 0) {
                updateCounter(s.subSequence(start, start + count), false);
            }
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            String str = s.toString();

            if (requiresMms()) {
                // Update the content of the text model.
                TextModel text = mSlideshow.get(0).getText();
                if ((text != null) && !text.getText().equals(str)) {
                    text.setText(str);
                }
            } else {
                mMsgText = str;
            }

            updateSendButtonState();

            if (count > 0) {
                updateCounter(s.subSequence(start, start + count), true);
            }
        }

        public void afterTextChanged(Editable s) {
        }
    };

    //==========================================================
    // Private methods
    //==========================================================

    /**
     * Initialize all UI elements from resources.
     */
    private void initResourceRefs() {
        mMsgListView = (ListView) findViewById(R.id.history);
        mBottomPanel = findViewById(R.id.bottom_panel);
        mTextEditor = (EditText) findViewById(R.id.embedded_text_editor);
        mTextEditor.setOnKeyListener(mEmbeddedTextEditorKeyListener);
        mTextEditor.addTextChangedListener(mTextEditorWatcher);
        mTextCounter = (TextView) findViewById(R.id.text_counter);
        mSendButton = (Button) findViewById(R.id.send_button);
        mSendButton.setOnClickListener(this);
        mTopPanel = findViewById(R.id.recipients_subject_linear);
    }

    private void confirmDeleteDialog(OnClickListener listener, boolean allMessages) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(true);
        builder.setMessage(allMessages
                ? R.string.confirm_delete_conversation
                : R.string.confirm_delete_message);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }

    void undeliveredMessageDialog(long date) {
        String body;
        LinearLayout dialog = (LinearLayout) LayoutInflater.from(this).inflate(
                R.layout.retry_sending_dialog, null);

        if (date >= 0) {
            body = getString(R.string.undelivered_msg_dialog_body,
                    MessageUtils.formatTimeStampString(this, date));
        } else {
            // FIXME: we can not get sms retry time.
            body = getString(R.string.undelivered_sms_dialog_body);
        }

        ((TextView) dialog.findViewById(R.id.body_text_view)).setText(body);

        Toast undeliveredDialog = new Toast(this);
        undeliveredDialog.setView(dialog);
        undeliveredDialog.setDuration(Toast.LENGTH_LONG);
        undeliveredDialog.show();
    }

    private String deriveAddress(Intent intent) {
        Uri recipientUri = intent.getData();
        return (recipientUri == null)
                ? null : recipientUri.getSchemeSpecificPart();
    }

    private Uri getThreadUri() {
        return ContentUris.withAppendedId(Threads.CONTENT_URI, mThreadId);
    }

    private void startMsgListQuery() {
        synchronized (mMsgListCursorLock) {
            // Cancel any pending queries
            mMsgListQueryHandler.cancelOperation(MESSAGE_LIST_QUERY_TOKEN);
            try {
                // Kick off the new query
                mMsgListQueryHandler.startQuery(
                        MESSAGE_LIST_QUERY_TOKEN, null, getThreadUri(),
                        PROJECTION, null, null, null);
            } catch (SQLiteException e) {
                SqliteWrapper.checkSQLiteException(this, e);
            }
        }
    }

    private void initMessageList(boolean startNewQuery) {
        // Initialize the list adapter with a null cursor.
        mMsgListAdapter = new MessageListAdapter(
                this, null, mMsgListView, true, getThreadType());
        mMsgListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
        mMsgListAdapter.setMsgListItemHandler(mMessageListItemHandler);
        mMsgListView.setAdapter(mMsgListAdapter);
        mMsgListView.setItemsCanFocus(false);
        mMsgListView.setVisibility(View.VISIBLE);
        mMsgListView.setOnCreateContextMenuListener(mMsgListMenuCreateListener);
        mMsgListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ((MessageListItem) view).onMessageListItemClick();
            }
        });

        // Initialize the async query handler for the message list.
        if (mMsgListQueryHandler == null) {
            mMsgListQueryHandler = new MsgListQueryHandler(mContentResolver);
        }

        if (startNewQuery) {
            startMsgListQuery();
        }
    }

    private Uri createTemporaryMmsMessage() throws MmsException {
        SendReq sendReq = new SendReq();
        fillMessageHeaders(sendReq);
        PduBody pb = mSlideshow.toPduBody();
        sendReq.setBody(pb);
        Uri res = mPersister.persist(sendReq, Mms.Draft.CONTENT_URI);
        mSlideshow.sync(pb);
        return res;
    }

    private void updateTemporaryMmsMessage() throws MmsException {
        if (mMessageUri == null) {
            mMessageUri = createTemporaryMmsMessage();
        } else {
            SendReq sendReq = new SendReq();
            fillMessageHeaders(sendReq);
            mPersister.updateHeaders(mMessageUri, sendReq);
            PduBody pb = mSlideshow.toPduBody();
            mPersister.updateParts(mMessageUri, pb);
            mSlideshow.sync(pb);
        }
        deleteTemporarySmsMessage(mThreadId);
    }

    private String getTemporarySmsMessageWhere(long thread_id) {
        String where = Sms.THREAD_ID + "=" + thread_id
                        + " AND " +
                        Sms.TYPE + "=" + Sms.MESSAGE_TYPE_DRAFT;
        return where;
    }

    private static final String[] SMS_BODY_PROJECTION = { Sms._ID, Sms.BODY };

    /**
     * Reads a draft message for the given thread ID from the database,
     * if there is one, deletes it from the database, and returns it.
     * @return The draft message or an empty string.
     */
    private String readTemporarySmsMessage(long thread_id) {
        // If it's an invalid thread, don't bother.
        if (thread_id <= 0) {
            return "";
        }

        String where = getTemporarySmsMessageWhere(thread_id);

        Cursor c = SqliteWrapper.query(this, mContentResolver,
                        Sms.CONTENT_URI, SMS_BODY_PROJECTION,
                        where, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) { return c.getString(1); }
            } finally {
                c.close();
            }
        }

        // FIXME: Why we need to delete it? It's safe to let it be.
        // Removing it may result in the thread which contains this sms
        // to be deleted by a trigger in database.
        // SqliteWrapper.delete(this, mContentResolver, Sms.CONTENT_URI, where, null);
        return "";
    }

    private void updateTemporarySmsMessage(long thread_id, String contents) {
        // If we don't have a valid thread, there's nothing to do.
        if (thread_id <= 0) {
            return;
        }

        // Don't bother saving an empty message.
        if (TextUtils.isEmpty(contents)) {
            // But delete the old temporary message if it's there.
            deleteTemporarySmsMessage(thread_id);
            return;
        }
        String where = getTemporarySmsMessageWhere(thread_id);
        Cursor c = SqliteWrapper.query(this, mContentResolver,
                Sms.CONTENT_URI, SMS_BODY_PROJECTION,
                where, null, null);

        if (c.moveToFirst()) {
            c.updateString(1, contents);
            c.commitUpdates();
        } else {
            ContentValues values = new ContentValues(3);
            values.put(Sms.THREAD_ID, thread_id);
            values.put(Sms.BODY, contents);
            values.put(Sms.TYPE, Sms.MESSAGE_TYPE_DRAFT);
            SqliteWrapper.insert(this, mContentResolver,
                    Sms.CONTENT_URI, values);
            deleteTemporaryMmsMessage(thread_id);
        }

        c.close();
    }

    private void deleteTemporarySmsMessage(long threadId) {
        String where = getTemporarySmsMessageWhere(threadId);
        SqliteWrapper.delete(this, mContentResolver, Sms.CONTENT_URI, where, null);
    }

    private void deleteTemporaryMmsMessage(long threadId) {
        final String where = Mms.THREAD_ID + " = " + threadId;
        SqliteWrapper.delete(this, mContentResolver, Mms.Draft.CONTENT_URI, where, null);
    }

    private String[] fillMessageHeaders(SendReq sendReq) {
        // Set the numbers in the 'TO' field.
        String[] dests = mRecipientList.getToNumbers();
        EncodedStringValue[] encodedNumbers = encodeStrings(dests);
        if (encodedNumbers != null) {
            sendReq.setTo(encodedNumbers);
        }

        // Set the numbers in the 'BCC' field.
        encodedNumbers = encodeStrings(mRecipientList.getBccNumbers());
        if (encodedNumbers != null) {
            sendReq.setBcc(encodedNumbers);
        }

        // Set the subject of the message.
        String subject = (mSubjectTextEditor == null)
                ? "" : mSubjectTextEditor.getText().toString();
        sendReq.setSubject(new EncodedStringValue(subject));

        // Update the 'date' field of the message before sending it.
        sendReq.setDate(System.currentTimeMillis() / 1000L);

        return dests;
    }

    private boolean hasRecipient() {
        return hasValidRecipient() || hasInvalidRecipient();
    }

    private boolean hasValidRecipient() {
        // If someone is in the recipient list, or if a valid recipient is
        // currently in the recipients editor, we have recipients.
        return (mRecipientList.hasValidRecipient())
                 || ((mRecipientsEditor != null)
                         && Recipient.isValid(mRecipientsEditor.getText().toString()));
    }

    private boolean hasInvalidRecipient() {
        return (mRecipientList.hasInvalidRecipient())
                 || ((mRecipientsEditor != null)
                         && !TextUtils.isEmpty(mRecipientsEditor.getText().toString())
                         && !Recipient.isValid(mRecipientsEditor.getText().toString()));
    }

    private boolean hasText() {
        return mTextEditor.length() > 0;
    }

    private boolean hasSubject() {
        return (null != mSubjectTextEditor)
                && !TextUtils.isEmpty(mSubjectTextEditor.getText().toString());
    }

    private boolean isPreparedForSending() {
        return hasRecipient() && (hasAttachment() || hasText());
    }

    private boolean preSendingMessage() {
        // Nothing to do here for SMS.
        if (!requiresMms()) {
            return true;
        }

        try {
            // Update contents of the message before sending it.
            updateTemporaryMmsMessage();
        } catch (MmsException e) {
            Log.e(TAG, "Cannot update message.", e);
            return false;
        }
        return true;
    }

    private void sendMessage() {
        boolean failed = false;

        if (!preSendingMessage()) {
            return;
        }

        String[] dests = fillMessageHeaders(new SendReq());
        MessageSender msgSender = requiresMms()
                ? new MmsMessageSender(this, mMessageUri)
                : new SmsMessageSender(this, dests, mMsgText, mThreadId);

        try {
            if (!msgSender.sendMessage(mThreadId) && (mMessageUri != null)) {
                // The message was sent through SMS protocol, we should
                // delete the copy which was previously saved in MMS drafts.
                SqliteWrapper.delete(this, mContentResolver, mMessageUri, null, null);
            }
        } catch (MmsException e) {
            Log.e(TAG, "Failed to send message: " + mMessageUri, e);
            // TODO Indicate this error to user(for example, show a warning
            // icon beside the message.
            failed = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send message: " + mMessageUri, e);
            // TODO Indicate this error to user(for example, show a warning
            // icon beside the message.
            failed = true;
        } finally {
            if (mExitOnSent) {
                mMsgText = "";
                mMessageUri = null;
                finish();
            } else if (!failed) {
                postSendingMessage();
            }
        }
    }

    private long getOrCreateThreadId(String[] numbers) {
        HashSet<String> recipients = new HashSet<String>();
        recipients.addAll(Arrays.asList(numbers));
        return Threads.getOrCreateThreadId(this, recipients);
    }

    private void postSendingMessage() {
        if (!requiresMms()) {
            // This should not be necessary because we delete the draft
            // message from the database at the time we read it back,
            // but I am paranoid.
            deleteTemporarySmsMessage(mThreadId);
        }

        // Make the attachment editor hide its view before we destroy it.
        if (mAttachmentEditor != null) {
            mAttachmentEditor.hideView();
        }

        // Focus to the text editor.
        mTextEditor.requestFocus();

        // Setting mMessageUri to null here keeps the conversion back to
        // SMS from deleting the "unnecessary" MMS in the database.
        mMessageUri = null;

        if (0 == (RECIPIENTS_REQUIRE_MMS & mMessageState)) {
            // Start a new message as an SMS.
            convertMessage(false);
        } else {
            // Start a new message as an MMS
            refreshMmsComponents();
        }

        // Clear the text box.
        TextKeyListener.clear(mTextEditor.getText());
        drawBottomPanel(AttachmentEditor.TEXT_ONLY);

        // "Or not", in this case.
        updateSendButtonState();

        String[] numbers = mRecipientList.getToNumbers();
        long threadId = getOrCreateThreadId(numbers);
        if (threadId > 0) {
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setVisibility(View.GONE);
                hideTopPanelIfNecessary();
            }

            if ((mMsgListAdapter == null) || (threadId != mThreadId)) {
                mThreadId = threadId;
                initMessageList(true);
            }
        } else {
            Log.e(TAG, "Failed to find/create thread with: "
                    + Arrays.toString(numbers));
            finish();
            return;
        }
    }

    private void updateSendButtonState() {
        boolean enable = false;
        if (isPreparedForSending()) {
            // When the type of attachment is slideshow, we should
            // also hide the 'Send' button since the slideshow view
            // already has a 'Send' button embedded.
            if ((mAttachmentEditor == null) ||
                (mAttachmentEditor.getAttachmentType() != AttachmentEditor.SLIDESHOW_ATTACHMENT)) {
                enable = true;
            } else {
                mAttachmentEditor.setCanSend(true);
            }
        } else if (null != mAttachmentEditor){
            mAttachmentEditor.setCanSend(false);
        }

        mSendButton.setEnabled(enable);
        mSendButton.setFocusable(enable);
    }

    private long getMessageDate(Uri uri) {
        if (uri != null) {
            Cursor cursor = SqliteWrapper.query(this, mContentResolver,
                    uri, new String[] { Mms.DATE }, null, null, null);
            if (cursor != null) {
                try {
                    if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                        return cursor.getLong(0) * 1000L;
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        return NO_DATE_FOR_DIALOG;
    }

    private void setSubjectFromIntent(Intent intent) {
        String subject = intent.getStringExtra("subject");
        if ( !TextUtils.isEmpty(subject) ) {
            mSubject = subject;
        }
    }
    
    private void initActivityState(Bundle savedInstanceState, Intent intent) {
        if (savedInstanceState != null) {
            mThreadId = savedInstanceState.getLong("thread_id", 0);
            mMessageUri = (Uri) savedInstanceState.getParcelable("msg_uri");
            mExternalAddress = savedInstanceState.getString("address");
            mComposeMode = savedInstanceState.getBoolean("compose_mode", false);
            mExitOnSent = savedInstanceState.getBoolean("exit_on_sent", false);
            mSubject = savedInstanceState.getString("subject");
            mMsgText = savedInstanceState.getString("sms_body");
        } else {
            mThreadId = intent.getLongExtra("thread_id", 0);
            mMessageUri = (Uri) intent.getParcelableExtra("msg_uri");
            if ((mMessageUri == null) && (mThreadId == 0)) {
                // If we haven't been given a thread id or a URI in the extras,
                // get it out of the intent.
                Uri uri = intent.getData();
                if ((uri != null) && (uri.getPathSegments().size() >= 2)) {
                    try {
                        mThreadId = Long.parseLong(uri.getPathSegments().get(1));
                    } catch (NumberFormatException exception) {
                        Log.e(TAG, "Thread ID must be a Long.");
                    }
                }
            }
            mExternalAddress = intent.getStringExtra("address");
            mComposeMode = intent.getBooleanExtra("compose_mode", false);
            mExitOnSent = intent.getBooleanExtra("exit_on_sent", false);
            mMsgText = intent.getStringExtra("sms_body");

            setSubjectFromIntent(intent);
        }

        if (!TextUtils.isEmpty(mSubject)) {
            updateState(HAS_SUBJECT, true);
        }

        // If there was not a body already, start with a blank one.
        if (mMsgText == null) {
            mMsgText = "";
        }

        if (mExternalAddress == null) {
            if (mThreadId > 0L) {
                mExternalAddress = MessageUtils.getAddressByThreadId(
                        this, mThreadId);
            } else {
                mExternalAddress = deriveAddress(intent);
            }
        }
    }

    private int getThreadType() {
        boolean isMms = (mMessageUri != null) || requiresMms();

        return (!isMms
                && (mRecipientList != null)
                && (mRecipientList.size() > 1))
                ? Threads.BROADCAST_THREAD
                : Threads.COMMON_THREAD;
    }

    private void updateWindowTitle() {
        StringBuilder sb = new StringBuilder();
        String[] values = mRecipientList.getToNumbers();
        for (String v : values) {
            sb.append(Mms.getDisplayAddress(this, v)).append(", ");
        }

        values = mRecipientList.getBccNumbers();
        if (values.length > 0) {
            sb.append("Bcc: ");
            for (String v : values) {
                sb.append(Mms.getDisplayAddress(this, v)).append(", ");
            }
        }

        if (sb.length() > 0) {
            // Delete the trailing ", " characters.
            int tail = sb.length() - 2;
            setTitle(sb.delete(tail, tail + 2).toString());
        } else {
            setTitle(getString(R.string.compose_title));
        }
    }

    private void initFocus() {
        if (!mIsKeyboardOpen) {
            return;
        }

        if ((mRecipientsEditor != null) &&
                (mRecipientsEditor.getVisibility() == View.VISIBLE) &&
                TextUtils.isEmpty(mRecipientsEditor.getText())) {
            mRecipientsEditor.requestFocus();
            return;
        }

        if ((mTextEditor != null) &&
                (mTextEditor.getVisibility() == View.VISIBLE)) {
            mTextEditor.requestFocus();
            return;
        }
    }

    private final MessageListAdapter.OnDataSetChangedListener
                    mDataSetChangedListener = new MessageListAdapter.OnDataSetChangedListener() {
        public void onDataSetChanged(MessageListAdapter adapter) {
            mPossiblePendingNotification = true;
        }
    };

    private void checkPendingNotification() {
        if (mPossiblePendingNotification) {
            // start async query so as not to slow down user input
            Uri.Builder builder = Threads.CONTENT_URI.buildUpon();
            builder.appendQueryParameter("simple", "true");
            mMsgListQueryHandler.startQuery(
                    THREAD_READ_QUERY_TOKEN, null, builder.build(),
                    new String[] { Threads.READ },
                    "_id=" + mThreadId, null, null);

            mPossiblePendingNotification = false;
        }
    }

    private final class MsgListQueryHandler extends AsyncQueryHandler {
        public MsgListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch(token) {
                case MESSAGE_LIST_QUERY_TOKEN:
                    synchronized (mMsgListCursorLock) {
                        if (cursor != null) {
                            mMsgListCursor = cursor;
                            mMsgListAdapter.changeCursor(cursor);
                        } else {
                            if (mMsgListCursor != null) {
                                mMsgListCursor.close();
                            }
                            Log.e(TAG, "Cannot init the cursor for the message list.");
                            finish();
                        }

                        // FIXME: freshing layout changes the focused view to an unexpected
                        // one, set it back to TextEditor forcely.
                        mTextEditor.requestFocus();
                    }
                    return;

                case THREAD_READ_QUERY_TOKEN:
                    boolean isRead = (cursor.moveToFirst() && (cursor.getInt(0) == 1));
                    if (!isRead) {
                        MessageUtils.handleReadReport(
                                ComposeMessageActivity.this, mThreadId,
                                PduHeaders.READ_STATUS_READ, null);

                        MessageUtils.markAsRead(ComposeMessageActivity.this, mThreadId);
                    }
                    cursor.close();
                    return;
            }
        }
    }
}
