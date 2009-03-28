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

import com.android.mms.ExceedMessageSizeException;
import com.android.mms.MmsConfig;
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
import com.android.mms.util.ContactInfoCache;
import com.android.mms.util.DraftCache;
import com.android.mms.util.SendingProgressTokenManager;
import com.android.mms.util.SmileyParser;

import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduBody;
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
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Presence;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.telephony.gsm.SmsMessage;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Config;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.webkit.MimeTypeMap;

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
 * exit_on_sent boolean Exit this activity after the message is sent.
 */
public class ComposeMessageActivity extends Activity
        implements View.OnClickListener, TextView.OnEditorActionListener,
        OnAttachmentChangedListener {
    public static final int REQUEST_CODE_ATTACH_IMAGE     = 10;
    public static final int REQUEST_CODE_TAKE_PICTURE     = 11;
    public static final int REQUEST_CODE_ATTACH_VIDEO     = 12;
    public static final int REQUEST_CODE_TAKE_VIDEO       = 13;
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
    private static final int MENU_SEND                  = 4;
    private static final int MENU_CALL_RECIPIENT        = 5;
    private static final int MENU_CONVERSATION_LIST     = 6;

    // Context menu ID
    private static final int MENU_VIEW_CONTACT          = 12;
    private static final int MENU_ADD_TO_CONTACTS       = 13;

    private static final int MENU_EDIT_MESSAGE          = 14;
    private static final int MENU_VIEW_SLIDESHOW        = 16;
    private static final int MENU_VIEW_MESSAGE_DETAILS  = 17;
    private static final int MENU_DELETE_MESSAGE        = 18;
    private static final int MENU_SEARCH                = 19;
    private static final int MENU_DELIVERY_REPORT       = 20;
    private static final int MENU_FORWARD_MESSAGE       = 21;
    private static final int MENU_CALL_BACK             = 22;
    private static final int MENU_SEND_EMAIL            = 23;
    private static final int MENU_COPY_MESSAGE_TEXT     = 24;
    private static final int MENU_COPY_TO_SDCARD        = 25;
    private static final int MENU_INSERT_SMILEY         = 26;
    private static final int MENU_ADD_ADDRESS_TO_CONTACTS = 27;

    private static final int SUBJECT_MAX_LENGTH    =  40;
    private static final int RECIPIENTS_MAX_LENGTH = 312;

    private static final int MESSAGE_LIST_QUERY_TOKEN = 9527;

    private static final int DELETE_MESSAGE_TOKEN  = 9700;
    private static final int DELETE_CONVERSATION_TOKEN  = 9701;

    private static final int CALLER_ID_QUERY_TOKEN = 9800;
    private static final int EMAIL_CONTACT_QUERY_TOKEN = 9801;

    private static final int MARK_AS_READ_TOKEN = 9900;
    
    private static final int MMS_THRESHOLD = 4;

    private static final int CHARS_REMAINING_BEFORE_COUNTER_SHOWN = 10;

    private static final long NO_DATE_FOR_DIALOG = -1L;
    
    private static final int REFRESH_PRESENCE = 45236;


    // caller id query params
    private static final String[] CALLER_ID_PROJECTION = new String[] {
            People.PRESENCE_STATUS,     // 0
    };
    private static final int PRESENCE_STATUS_COLUMN = 0;

    private static final String NUMBER_LOOKUP = "PHONE_NUMBERS_EQUAL("
        + Contacts.Phones.NUMBER + ",?)";
    private static final Uri PHONES_WITH_PRESENCE_URI
        = Uri.parse(Contacts.Phones.CONTENT_URI + "_with_presence");

    // email contact query params
    private static final String[] EMAIL_QUERY_PROJECTION = new String[] {
            Contacts.People.PRESENCE_STATUS,     // 0
    };

    private static final String METHOD_LOOKUP = Contacts.ContactMethods.DATA + "=?";
    private static final Uri METHOD_WITH_PRESENCE_URI =
            Uri.withAppendedPath(Contacts.ContactMethods.CONTENT_URI, "with_presence");



    private ContentResolver mContentResolver;

    // The parameters/states of the activity.
    private long mThreadId;                 // Database key for the current conversation
    private String mExternalAddress;        // Serialized recipients in the current conversation
    private boolean mExitOnSent;            // Should we finish() after sending a message?

    private View mTopPanel;                 // View containing the recipient and subject editors
    private View mBottomPanel;              // View containing the text editor, send button, ec.
    private EditText mTextEditor;           // Text editor to type your message into
    private TextView mTextCounter;          // Shows the number of characters used in text editor
    private Button mSendButton;             // Press to detonate

    private CharSequence mMsgText;                // Text of message

    private BackgroundQueryHandler mBackgroundQueryHandler;

    private MessageListView mMsgListView;        // ListView for messages in this conversation
    private MessageListAdapter mMsgListAdapter;  // and its corresponding ListAdapter

    private RecipientList mRecipientList;        // List of recipients for this conversation
    private RecipientsEditor mRecipientsEditor;  // UI control for editing recipients

    private boolean mIsKeyboardOpen;             // Whether the hardware keyboard is visible
    private boolean mIsLandscape;                // Whether we're in landscape mode

    private boolean mPossiblePendingNotification;   // If the message list has changed, we may have
                                                    // a pending notification to deal with.
    
    private boolean mToastForDraftSave;          // Whether to notify the user that a draft is
                                                 // being saved.

    private static final int RECIPIENTS_REQUIRE_MMS = (1 << 0);     // 1
    private static final int HAS_SUBJECT = (1 << 1);                // 2
    private static final int HAS_ATTACHMENT = (1 << 2);             // 4
    private static final int LENGTH_REQUIRES_MMS = (1 << 3);        // 8

    private int mMessageState;                  // A bitmap of the above indicating different
                                                // properties of the message -- any bit set
                                                // will require conversion to MMS.

    // These fields are only used in MMS compose mode (requiresMms() == true) and should
    // otherwise be null.
    private SlideshowModel mSlideshow;
    private Uri mMessageUri;
    private EditText mSubjectTextEditor;    // Text editor for MMS subject
    private String mSubject;                // MMS subject
    private AttachmentEditor mAttachmentEditor;
    private PduPersister mPersister;

    private AlertDialog mSmileyDialog;
    
    // Everything needed to deal with presence
    private Cursor mContactInfoCursor;
    private int mPresenceStatus;
    private String[] mContactInfoSelectionArgs = new String[1];
    
    private boolean mWaitingForSubActivity;
    
    private static void log(String format, Object... args) {
        Thread current = Thread.currentThread();
        long tid = current.getId();
        StackTraceElement[] stack = current.getStackTrace();
        String methodName = stack[3].getMethodName();
        // Prepend current thread ID and name of calling method to the message.
        format = "[" + tid + "] [" + methodName + "] " + format;
        String logMsg = String.format(format, args);
        Log.d(TAG, logMsg);
    }
    
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
                case AttachmentEditor.MSG_PLAY_VIDEO:
                case AttachmentEditor.MSG_PLAY_AUDIO:
                case AttachmentEditor.MSG_PLAY_SLIDESHOW:
                    MessageUtils.viewMmsMessageAttachment(ComposeMessageActivity.this,
                            mMessageUri, mSlideshow, mPersister);
                    break;

                case AttachmentEditor.MSG_REPLACE_IMAGE:
                case AttachmentEditor.MSG_REPLACE_VIDEO:
                case AttachmentEditor.MSG_REPLACE_AUDIO:
                    showAddAttachmentDialog();
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

    private final Handler mPresencePollingHandler = new Handler() {        
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == REFRESH_PRESENCE) {
                startQueryForContactInfo();
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

    private MessageItem getMessageItem(String type, long msgId) {
        // Check whether the cursor is valid or not.
        Cursor cursor = mMsgListAdapter.getCursor();
        if (cursor.isClosed() || cursor.isBeforeFirst() || cursor.isAfterLast()) {
            Log.e(TAG, "Bad cursor.", new RuntimeException());
            return null;
        }

        return mMsgListAdapter.getCachedMessageItem(type, msgId, cursor);
    }

    private void resetCounter() {
        mTextCounter.setText("");
        mTextCounter.setVisibility(View.GONE);
    }

    private void updateCounter(CharSequence text, int start, int before, int count) {
        // The worst case before we begin showing the text counter would be
        // a UCS-2 message, providing space for 70 characters, minus
        // CHARS_REMAINING_BEFORE_COUNTER_SHOWN.  Don't bother calling
        // the relatively expensive SmsMessage.calculateLength() until that
        // point is reached.
        if (text.length() < (70-CHARS_REMAINING_BEFORE_COUNTER_SHOWN)) {
            mTextCounter.setVisibility(View.GONE);
            return;
        }

        // If we're not removing text (i.e. no chance of converting back to SMS
        // because of this change) and we're in MMS mode, just bail out.
        final boolean textAdded = (before < count);
        if (textAdded && requiresMms()) {
            mTextCounter.setVisibility(View.GONE);
            return;
        }
        
        int[] params = SmsMessage.calculateLength(text, false);
            /* SmsMessage.calculateLength returns an int[4] with:
             *   int[0] being the number of SMS's required,
             *   int[1] the number of code units used,
             *   int[2] is the number of code units remaining until the next message.
             *   int[3] is the encoding type that should be used for the message.
             */
        int msgCount = params[0];
        int remainingInCurrentMessage = params[2];

        // Convert to MMS if this message has gotten too long for SMS.
        convertMessageIfNeeded(LENGTH_REQUIRES_MMS, msgCount >= MMS_THRESHOLD);
        
        // Show the counter only if:
        // - We are not in MMS mode
        // - We are going to send more than one message OR we are getting close
        boolean showCounter = false;
        if (!requiresMms() &&
            (msgCount > 1 || remainingInCurrentMessage <= CHARS_REMAINING_BEFORE_COUNTER_SHOWN)) {
            showCounter = true;
        }
        
        if (showCounter) {
            // Update the remaining characters and number of messages required.
            mTextCounter.setText(remainingInCurrentMessage + " / " + msgCount);
            mTextCounter.setVisibility(View.VISIBLE);
        } else {
            mTextCounter.setVisibility(View.GONE);
        }
    }

    private void initMmsComponents() {
       // Initialize subject editor.
        mSubjectTextEditor = (EditText) findViewById(R.id.subject);
        mSubjectTextEditor.setOnKeyListener(mSubjectKeyListener);
        mSubjectTextEditor.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(SUBJECT_MAX_LENGTH) });
        if (!TextUtils.isEmpty(mSubject)) {
            updateState(HAS_SUBJECT, true);
            mSubjectTextEditor.setText(mSubject);
            showSubjectEditor();
        }

        try {
            if (mMessageUri != null) {
                // Move the message into Draft before editing it.
                mMessageUri = mPersister.move(mMessageUri, Mms.Draft.CONTENT_URI);
                mSlideshow = SlideshowModel.createFromMessageUri(this, mMessageUri);
            } else {
                mSlideshow = createNewSlideshow(this);
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
        fixEmptySlideshow(mSlideshow);
        if (attachmentType == AttachmentEditor.EMPTY) {
            attachmentType = AttachmentEditor.TEXT_ONLY;
        }
        mAttachmentEditor.setAttachment(mSlideshow, attachmentType);

        if (attachmentType > AttachmentEditor.TEXT_ONLY) {
            updateState(HAS_ATTACHMENT, true);
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode)
    {
        // requestCode >= 0 means the activity in question is a sub-activity.
        if (requestCode >= 0) {
            mWaitingForSubActivity = true;
        }
        
        super.startActivityForResult(intent, requestCode);
    }
    
    synchronized private void uninitMmsComponents() {
        // Get text from slideshow if needed.
        if (mAttachmentEditor != null && mSlideshow != null) {
            int attachmentType = mAttachmentEditor.getAttachmentType();
            if (AttachmentEditor.TEXT_ONLY == attachmentType && mSlideshow != null) {
                SlideModel model = mSlideshow.get(0);
                if (model != null) {
                    TextModel textModel = model.getText();
                    if (textModel != null) {
                        mMsgText = textModel.getText();
                    }
                }
            }
        }

        mMessageState = 0;
        mSlideshow = null;
        if (mMessageUri != null) {
            // Not sure if this is the best way to do this..
            if (mMessageUri.toString().startsWith(Mms.Draft.CONTENT_URI.toString())) {
                asyncDelete(mMessageUri, null, null);
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

    private void resetMmsComponents() {
        mMessageState = RECIPIENTS_REQUIRE_MMS;
        if (mSubjectTextEditor != null) {
            mSubjectTextEditor.setText("");
            mSubjectTextEditor.setVisibility(View.GONE);
        }
        mSubject = null;

        try {
            mSlideshow = createNewSlideshow(this);
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
            // Hide the counter
            if (mTextCounter != null) {
                mTextCounter.setVisibility(View.GONE);
            }
            initMmsComponents();
            CharSequence mmsText = mSlideshow.get(0).getText().getText();
            // Show or hide the counter as necessary
            updateCounter(mmsText, 0, 0, mmsText.length());
        } else {
            uninitMmsComponents();
            // Show or hide the counter as necessary
            updateCounter(mMsgText, 0, 0, mMsgText.length());
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
        convertMessageIfNeeded(whichState, set, true);
    }
    
    private void convertMessageIfNeeded(int whichState, boolean set, boolean toast) {
        int oldState = mMessageState;
        updateState(whichState, set);

        // With MMS disabled, LENGTH_REQUIRES_MMS is a no-op.
        if (MmsConfig.DISABLE_MMS) {
            whichState &= ~LENGTH_REQUIRES_MMS;
        }

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

        if (MmsConfig.DISABLE_MMS && toMms) {
            throw new IllegalStateException(
                    "Message converted to MMS with DISABLE_MMS set");
        }
        
        if (toast) {
            toastConvertInfo(toMms);
        }
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
            int token = mDeleteAll ? DELETE_CONVERSATION_TOKEN
                                   : DELETE_MESSAGE_TOKEN;
            mBackgroundQueryHandler.startDelete(token,
                    null, mDeleteUri, null, null);
        }
    }

    private void discardTemporaryMessage() {
        if (requiresMms()) {
            if (mMessageUri != null) {
                if (LOCAL_LOGV) Log.v(TAG, "discardTemporaryMessage " + mMessageUri);
                asyncDelete(mMessageUri, null, null);
                // Prevent the message from being re-saved in onStop().
                mMessageUri = null;
            }
        }
        
        asyncDeleteTemporarySmsMessage(mThreadId);

        // Don't save this message as a draft, even if it is only an SMS.
        mMsgText = "";
    }

    private class DiscardDraftListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            discardTemporaryMessage();
            goToConversationList();
        }
    }

    private class SendIgnoreInvalidRecipientListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            sendMessage();
        }
    }

    private class CancelSendingListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            if (isRecipientsEditorVisible()) {
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
                startQueryForContactInfo();
            }
        }
    };

    private final TextWatcher mRecipientsWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // This is a workaround for bug 1609057.  Since onUserInteraction() is
            // not called when the user touches the soft keyboard, we pretend it was
            // called when textfields changes.  This should be removed when the bug
            // is fixed.
            onUserInteraction();
        }

        public void afterTextChanged(Editable s) {
            int oldValidCount = mRecipientList.size();
            int oldTotal = mRecipientList.countInvalidRecipients() + oldValidCount;

            // Bug 1474782 describes a situation in which we send to
            // the wrong recipient.  We have been unable to reproduce this,
            // but the best theory we have so far is that the contents of
            // mRecipientList somehow become stale when entering
            // ComposeMessageActivity via onNewIntent().  This assertion is
            // meant to catch one possible path to that, of a non-visible
            // mRecipientsEditor having its TextWatcher fire and refreshing
            // mRecipientList with its stale contents.
            if (!isRecipientsEditorVisible()) {
                IllegalStateException e = new IllegalStateException(
                        "afterTextChanged called with invisible mRecipientsEditor");
                // Make sure the crash is uploaded to the service so we
                // can see if this is happening in the field.
                Log.e(TAG, "RecipientsWatcher called incorrectly", e);
                throw e;
            }

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
                startQueryForContactInfo();
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

                String title = !TextUtils.isEmpty(r.name) ? r.name : r.number;
                menu.setHeaderTitle(title);

                long personId = getPersonId(r);
                if (personId > 0) {
                    r.person_id = personId;     // make sure it's updated with the latest.
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
                    viewContact(mRecipient.person_id);
                    return true;
                }
                case MENU_ADD_TO_CONTACTS: {
                    Intent intent = ConversationList.createAddContactIntent(mRecipient.number);
                    ComposeMessageActivity.this.startActivity(intent);
                    return true;
                }
            }
            return false;
        }
    }

    private void viewContact(long personId) {
        Uri uri = ContentUris.withAppendedId(People.CONTENT_URI, personId);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);            
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

    private final void addCallAndContactMenuItems(
            ContextMenu menu, MsgListMenuClickListener l, MessageItem msgItem) {
        // Add all possible links in the address & message
        StringBuilder textToSpannify = new StringBuilder();
        if (msgItem.mBoxId == Mms.MESSAGE_BOX_INBOX) {
            textToSpannify.append(msgItem.mAddress + ": ");
        }
        textToSpannify.append(msgItem.mBody);

        SpannableString msg = new SpannableString(textToSpannify.toString());
        Linkify.addLinks(msg, Linkify.ALL);
        ArrayList<String> uris =
            MessageUtils.extractUris(msg.getSpans(0, msg.length(), URLSpan.class));

        while (uris.size() > 0) {
            String uriString = uris.remove(0);
            // Remove any dupes so they don't get added to the menu multiple times
            while (uris.contains(uriString)) {
                uris.remove(uriString);
            }
            
            int sep = uriString.indexOf(":");
            String prefix = null;
            if (sep >= 0) {
                prefix = uriString.substring(0, sep);
                uriString = uriString.substring(sep + 1);
            }
            boolean addToContacts = false;
            if ("mailto".equalsIgnoreCase(prefix))  {
                String sendEmailString = getString(
                        R.string.menu_send_email).replace("%s", uriString);
                menu.add(0, MENU_SEND_EMAIL, 0, sendEmailString)
                    .setOnMenuItemClickListener(l)
                    .setIntent(new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("mailto:" + uriString)));
                addToContacts = !haveEmailContact(uriString);
            } else if ("tel".equalsIgnoreCase(prefix)) {
                String callBackString = getString(
                        R.string.menu_call_back).replace("%s", uriString);
                menu.add(0, MENU_CALL_BACK, 0, callBackString)
                    .setOnMenuItemClickListener(l)
                    .setIntent(new Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:" + uriString)));
                addToContacts = !isNumberInContacts(uriString);
            }
            if (addToContacts) {
                Intent intent = ConversationList.createAddContactIntent(uriString);
                String addContactString = getString(
                        R.string.menu_add_address_to_contacts).replace("%s", uriString);
                menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, addContactString)
                    .setOnMenuItemClickListener(l)
                    .setIntent(intent);
            }
        }
    }

    private boolean haveEmailContact(String emailAddress) {
        Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
                Contacts.ContactMethods.CONTENT_EMAIL_URI,
                new String[] { Contacts.ContactMethods.NAME },
                Contacts.ContactMethods.DATA + " = " + DatabaseUtils.sqlEscapeString(emailAddress),
                null, null);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (!TextUtils.isEmpty(name)) {
                        return true;
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return false;
    }

    private boolean isNumberInContacts(String phoneNumber) {
        ContactInfoCache.CacheEntry entry =
                ContactInfoCache.getInstance().getContactInfo(this, phoneNumber);
        return !TextUtils.isEmpty(entry.name);
    }

    private final OnCreateContextMenuListener mMsgListMenuCreateListener =
        new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            Cursor cursor = mMsgListAdapter.getCursor();
            String type = cursor.getString(COLUMN_MSG_TYPE);
            long msgId = cursor.getLong(COLUMN_ID);

            addPositionBasedMenuItems(menu, v, menuInfo);

            MessageItem msgItem = mMsgListAdapter.getCachedMessageItem(type, msgId, cursor);
            if (msgItem == null) {
                Log.e(TAG, "Cannot load message item for type = " + type
                        + ", msgId = " + msgId);
                return;
            }

            menu.setHeaderTitle(R.string.message_options);

            MsgListMenuClickListener l = new MsgListMenuClickListener();
            if (msgItem.isMms()) {
                switch (msgItem.mBoxId) {
                    case Mms.MESSAGE_BOX_INBOX:
                        break;
                    case Mms.MESSAGE_BOX_OUTBOX:
                        if (mRecipientList.size() == 1) {
                            menu.add(0, MENU_EDIT_MESSAGE, 0, R.string.menu_edit)
                                    .setOnMenuItemClickListener(l);
                        }
                        break;
                }
                switch (msgItem.mAttachmentType) {
                    case AttachmentEditor.TEXT_ONLY:
                        break;
                    case AttachmentEditor.VIDEO_ATTACHMENT:
                    case AttachmentEditor.IMAGE_ATTACHMENT:
                        if (haveSomethingToCopyToSDCard(msgItem.mMsgId)) {
                            menu.add(0, MENU_COPY_TO_SDCARD, 0, R.string.copy_to_sdcard)
                            .setOnMenuItemClickListener(l);
                        }
                        break;
                    case AttachmentEditor.SLIDESHOW_ATTACHMENT:
                    default:
                        menu.add(0, MENU_VIEW_SLIDESHOW, 0, R.string.view_slideshow)
                        .setOnMenuItemClickListener(l);
                        if (haveSomethingToCopyToSDCard(msgItem.mMsgId)) {
                            menu.add(0, MENU_COPY_TO_SDCARD, 0, R.string.copy_to_sdcard)
                            .setOnMenuItemClickListener(l);
                        }
                        break;
                }
            } else {
                // Message type is sms. Only allow "edit" if the message has a single recipient
                if (mRecipientList.size() == 1 &&
                        (msgItem.mBoxId == Sms.MESSAGE_TYPE_OUTBOX ||
                        msgItem.mBoxId == Sms.MESSAGE_TYPE_FAILED)) {
                    menu.add(0, MENU_EDIT_MESSAGE, 0, R.string.menu_edit)
                            .setOnMenuItemClickListener(l);
                }
            }

            addCallAndContactMenuItems(menu, l, msgItem);

            // Forward is not available for undownloaded messages.
            if (msgItem.isDownloaded()) {
                menu.add(0, MENU_FORWARD_MESSAGE, 0, R.string.menu_forward)
                        .setOnMenuItemClickListener(l);
            }

            // It is unclear what would make most sense for copying an MMS message
            // to the clipboard, so we currently do SMS only.
            if (msgItem.isSms()) {
                menu.add(0, MENU_COPY_MESSAGE_TEXT, 0, R.string.copy_message_text)
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
        if (MessageListItem.isFailedMessage(msgItem) && mMsgListAdapter.getCount() <= 1) {
            // For messages with bad addresses, let the user re-edit the recipients.
            initRecipientsEditor();
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
            showSubjectEditor();
        } else {
            mSubjectTextEditor.setVisibility(View.GONE);
            hideTopPanelIfNecessary();
        }
    }

    private void copyToClipboard(String str) {
        ClipboardManager clip =
            (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setText(str);
    }

    /**
     * Context menu handlers for the message list view.
     */
    private final class MsgListMenuClickListener implements MenuItem.OnMenuItemClickListener {
        public boolean onMenuItemClick(MenuItem item) {
            Cursor cursor = mMsgListAdapter.getCursor();
            String type = cursor.getString(COLUMN_MSG_TYPE);
            long msgId = cursor.getLong(COLUMN_ID);
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
                case MENU_COPY_MESSAGE_TEXT: {
                    copyToClipboard(msgItem.mBody);
                    return true;
                }
                case MENU_FORWARD_MESSAGE: {
                    Intent intent = new Intent(ComposeMessageActivity.this,
                                               ComposeMessageActivity.class);

                    intent.putExtra("exit_on_sent", true);
                    intent.putExtra("forwarded_message", true);
                    if (type.equals("sms")) {
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

                        Uri uri = null;
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
                case MENU_VIEW_SLIDESHOW: {
                    MessageUtils.viewMmsMessageAttachment(ComposeMessageActivity.this,
                            ContentUris.withAppendedId(Mms.CONTENT_URI, msgId),
                            null /* slideshow */, null /* persister */);
                    return true;
                }
                case MENU_VIEW_MESSAGE_DETAILS: {
                    String messageDetails = MessageUtils.getMessageDetails(
                            ComposeMessageActivity.this, cursor, msgItem.mMessageSize);
                    new AlertDialog.Builder(ComposeMessageActivity.this)
                            .setTitle(R.string.message_details_title)
                            .setMessage(messageDetails)
                            .setPositiveButton(android.R.string.ok, null)
                            .setCancelable(true)
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

                case MENU_COPY_TO_SDCARD: {
                    int resId = copyMedia(msgId) ? R.string.copy_to_sdcard_success :
                        R.string.copy_to_sdcard_fail;
                    Toast.makeText(ComposeMessageActivity.this, resId, Toast.LENGTH_SHORT).show();
                    return true;
                }

                default:
                    return false;
            }
        }
    }

    /**
     * Looks to see if there are any valid parts of the attachment that can be copied to a SD card.
     * @param msgId
     */
    private boolean haveSomethingToCopyToSDCard(long msgId) {
        PduBody body;
        try {
           body = SlideshowModel.getPduBody(this,
                   ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }

        boolean result = false;
        int partNum = body.getPartsNum();
        for(int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());

            if ((ContentType.isImageType(type) || ContentType.isVideoType(type) ||
                    ContentType.isAudioType(type))) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Copies media from an Mms to the "download" directory on the SD card
     * @param msgId
     */
    private boolean copyMedia(long msgId) {
        PduBody body;
        boolean result = true;
        try {
           body = SlideshowModel.getPduBody(this, ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }

        int partNum = body.getPartsNum();
        for(int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());

            if ((ContentType.isImageType(type) || ContentType.isVideoType(type) ||
                    ContentType.isAudioType(type))) {
                result &= copyPart(part);   // all parts have to be successful for a valid result.
            }
        }
        return result;
    }

    private boolean copyPart(PduPart part) {
        Uri uri = part.getDataUri();

        InputStream input = null;
        FileOutputStream fout = null;
        try {
            input = mContentResolver.openInputStream(uri);
            if (input instanceof FileInputStream) {
                FileInputStream fin = (FileInputStream) input;

                byte[] location = part.getName();
                if (location == null) {
                    location = part.getFilename();
                }
                if (location == null) {
                    location = part.getContentLocation();
                }

                // Depending on the location, there may be an
                // extension already on the name or not
                String fileName = new String(location);
                String dir = "/sdcard/download/";
                String extension;
                int index;
                if ((index = fileName.indexOf(".")) == -1) {
                    String type = new String(part.getContentType());
                    extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
                } else {
                    extension = fileName.substring(index + 1, fileName.length());
                    fileName = fileName.substring(0, index);
                }

                File file = getUniqueDestination(dir + fileName, extension);

                // make sure the path is valid and directories created for this file.
                File parentFile = file.getParentFile();
                if (!parentFile.exists() && !parentFile.mkdirs()) {
                    Log.e(TAG, "[MMS] copyPart: mkdirs for " + parentFile.getPath() + " failed!");
                    return false;
                }

                fout = new FileOutputStream(file);

                byte[] buffer = new byte[8000];
                while(fin.read(buffer) != -1) {
                    fout.write(buffer);
                }

                // Notify other applications listening to scanner events
                // that a media file has been added to the sd card
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(file)));
            }
        } catch (IOException e) {
            // Ignore
            Log.e(TAG, "IOException caught while opening or reading stream", e);
            return false;
        } finally {
            if (null != input) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(TAG, "IOException caught while closing stream", e);
                    return false;
                }
            }
            if (null != fout) {
                try {
                    fout.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(TAG, "IOException caught while closing stream", e);
                    return false;
                }
            }
        }
        return true;
    }

    private File getUniqueDestination(String base, String extension) {
        File file = new File(base + "." + extension);

        for (int i = 2; file.exists(); i++) {
            file = new File(base + "_" + i + "." + extension);
        }
        return file;
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

    private static SlideshowModel createNewSlideshow(Context context) {
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
        if (isRecipientsEditorVisible()) {
            return;
        }
        ViewStub stub = (ViewStub)findViewById(R.id.recipients_editor_stub);
        if (stub != null) {
            mRecipientsEditor = (RecipientsEditor) stub.inflate();
        } else {
            mRecipientsEditor = (RecipientsEditor)findViewById(R.id.recipients_editor);
            mRecipientsEditor.setVisibility(View.VISIBLE);
        }

        mRecipientsEditor.setAdapter(new RecipientsAdapter(this));
        mRecipientsEditor.populate(mRecipientList);
        mRecipientsEditor.setOnCreateContextMenuListener(mRecipientsMenuCreateListener);
        mRecipientsEditor.addTextChangedListener(mRecipientsWatcher);
        mRecipientsEditor.setOnFocusChangeListener(mRecipientsFocusListener);
        mRecipientsEditor.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(RECIPIENTS_MAX_LENGTH) });
        mRecipientsEditor.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // After the user selects an item in the pop-up contacts list, move the
                // focus to the text editor if there is only one recipient.  This helps
                // the common case of selecting one recipient and then typing a message,
                // but avoids annoying a user who is trying to add five recipients and
                // keeps having focus stolen away.
                if (mRecipientList.size() == 1) {
                    // if we're in extract mode then don't request focus
                    final InputMethodManager inputManager = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (inputManager == null || !inputManager.isFullscreenMode()) {
                        mTextEditor.requestFocus();
                    }
                }
            }
        });

        mTopPanel.setVisibility(View.VISIBLE);
    }

    //==========================================================
    // Activity methods
    //==========================================================

    private void setPresenceIcon(int iconId) {
        Drawable icon = iconId == 0 ? null : this.getResources().getDrawable(iconId);
        getWindow().setFeatureDrawable(Window.FEATURE_LEFT_ICON, icon);
    }
    
    static public boolean cancelFailedToDeliverNotification(Intent intent, Context context) {
        if (ConversationList.isFailedToDeliver(intent)) {
            // Cancel any failed message notifications
            MessagingNotification.cancelNotification(context,
                        MessagingNotification.MESSAGE_FAILED_NOTIFICATION_ID);
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);

        setContentView(R.layout.compose_message_activity);
        setProgressBarVisibility(false);

        setTitle("");

        // Initialize members for UI elements.
        initResourceRefs();

        mContentResolver = getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(mContentResolver);
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

        if (cancelFailedToDeliverNotification(getIntent(), getApplicationContext())) {
            // Show a pop-up dialog to inform user the message was
            // failed to deliver.
            undeliveredMessageDialog(getMessageDate(mMessageUri));
        }

        // Set up the message history ListAdapter
        initMessageList();

        // Mark the current thread as read.
        markAsRead(mThreadId);
        
        // Load the draft for this thread, if we aren't already handling
        // existing data, such as a shared picture or forwarded message.
        if (!handleSendIntent(getIntent()) && !handleForwardedMessage()) {
            loadDraft();
        }
        
        // If we are still not in MMS mode, check to see if we need to convert
        // because of e-mail recipients.
        convertMessageIfNeeded(RECIPIENTS_REQUIRE_MMS, recipientsRequireMms(), false);

        // Show the recipients editor if we don't have a valid thread.
        if (mThreadId <= 0) {
            initRecipientsEditor();
        }
        
        int attachmentType = requiresMms()
                ? MessageUtils.getAttachmentType(mSlideshow)
                : AttachmentEditor.TEXT_ONLY;

        updateSendButtonState();

        drawBottomPanel(attachmentType);

        mTopPanel.setFocusable(false);

        Configuration config = getResources().getConfiguration();
        mIsKeyboardOpen = config.keyboardHidden == KEYBOARDHIDDEN_NO;
        mIsLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        onKeyboardStateChanged(mIsKeyboardOpen);

        if (TRACE) {
            android.os.Debug.startMethodTracing("compose");
        }
    }

    private void showSubjectEditor() {
        mSubjectTextEditor.setVisibility(View.VISIBLE);
        mTopPanel.setVisibility(View.VISIBLE);
    }
    
    private void hideTopPanelIfNecessary() {
        if (!isSubjectEditorVisible() && !isRecipientsEditorVisible()) {
            mTopPanel.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        markAsRead(mThreadId);
        
        // If the user added a contact from a recipient, we've got to make sure we invalidate
        // our local contact cache so we'll go out and refresh that particular contact and
        // get the real person_id and other info.
        invalidateRecipientsInCache();
    }

    @Override
    protected void onStart() {
        super.onStart();

        updateWindowTitle();
        initFocus();

        // Register a BroadcastReceiver to listen on HTTP I/O process.
        registerReceiver(mHttpProgressReceiver, mHttpProgressFilter);

        startMsgListQuery();
        startQueryForContactInfo();
        updateSendFailedNotification();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        startPresencePollingRequest();
    }

    private void updateSendFailedNotification() {
        // updateSendFailedNotificationForThread makes a database call, so do the work off
        // of the ui thread.
        new Thread(new Runnable() {
            public void run() {
                MessagingNotification.updateSendFailedNotificationForThread(
                        ComposeMessageActivity.this, mThreadId);
            }
        }).run();
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

        removeSubjectIfEmpty();
        if (requiresMms()) {
            if (isSubjectEditorVisible()) {
                outState.putString("subject", mSubjectTextEditor.getText().toString());
            }

            if (mMessageUri != null) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "ONFREEZE: mMessageUri: " + mMessageUri);
                }
                outState.putParcelable("msg_uri", mMessageUri);
            }
        } else {
            outState.putString("sms_body", mMsgText.toString());
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

    private void removeSubjectIfEmpty() {
        // subject editor is visible without any contents.
        if ((mMessageState == HAS_SUBJECT) && !hasSubject()) {
            convertMessage(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelPresencePollingRequests();
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        if (mMsgListAdapter != null) {
            mMsgListAdapter.changeCursor(null);
        }

        saveDraft();

        // Cleanup the BroadcastReceiver.
        unregisterReceiver(mHttpProgressReceiver);
        
        cleanupContactInfoCursor();
    }

    @Override
    protected void onDestroy() {
        if (TRACE) {
            android.os.Debug.stopMethodTracing();
        }
        
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (LOCAL_LOGV) {
            Log.v(TAG, "onConfigurationChanged: " + newConfig);
        }
        
        mIsKeyboardOpen = newConfig.keyboardHidden == KEYBOARDHIDDEN_NO;
        mIsLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
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
    public void onUserInteraction() {
        checkPendingNotification();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            checkPendingNotification();
        }
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
        // If the message is empty, just quit -- finishing the
        // activity will cause an empty draft to be deleted.
        if (isEmptyMessage()) {
            exit.run();
            return;
        }
        
        if (!hasValidRecipient()) {
            MessageUtils.showDiscardDraftConfirmDialog(this,
                    new DiscardDraftListener());
            return;
        }

        mToastForDraftSave = true;
        exit.run();
    }

    private void goToConversationList() {
        finish();
        startActivity(new Intent(this, ConversationList.class));
    }

    private boolean isRecipientsEditorVisible() {
        return (null != mRecipientsEditor)
                    && (View.VISIBLE == mRecipientsEditor.getVisibility());
    }

    private boolean isSubjectEditorVisible() {
        return (null != mSubjectTextEditor)
                    && (View.VISIBLE == mSubjectTextEditor.getVisibility());
    }
    
    public void onAttachmentChanged(int newType, int oldType) {
        drawBottomPanel(newType);
        if (newType > AttachmentEditor.TEXT_ONLY) {
            updateState(HAS_ATTACHMENT, true);
        } else {
            convertMessageIfNeeded(HAS_ATTACHMENT, false);
        }
        updateSendButtonState();
    }

    // We don't want to show the "call" option unless there is only one
    // recipient and it's a phone number.
    private boolean isRecipientCallable() {
        return (mRecipientList.size() == 1 && !mRecipientList.containsEmail());
    }
    
    private void dialRecipient() {
        String number = mRecipientList.getSingleRecipientNumber();
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
        startActivity(dialIntent);
    }
        
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (isRecipientCallable()) {
            menu.add(0, MENU_CALL_RECIPIENT, 0, R.string.menu_call).setIcon(
                com.android.internal.R.drawable.ic_menu_call);
        }
        
        // Only add the "View contact" menu item when there's a single recipient and that
        // recipient is someone in contacts.
        long personId = getPersonId(mRecipientList.getSingleRecipient());
        if (personId > 0) {
            menu.add(0, MENU_VIEW_CONTACT, 0, R.string.menu_view_contact).setIcon(
                    R.drawable.ic_menu_contact);
        }

        if (!MmsConfig.DISABLE_MMS) {
            if (!isSubjectEditorVisible()) {
                menu.add(0, MENU_ADD_SUBJECT, 0, R.string.add_subject).setIcon(
                        com.android.internal.R.drawable.ic_menu_edit);
            }

            if ((mAttachmentEditor == null) || (mAttachmentEditor.getAttachmentType() == AttachmentEditor.TEXT_ONLY)) {
                menu.add(0, MENU_ADD_ATTACHMENT, 0, R.string.add_attachment).setIcon(
                        R.drawable.ic_menu_attachment);
            }
        }
        
        if (isPreparedForSending()) {
            menu.add(0, MENU_SEND, 0, R.string.send).setIcon(android.R.drawable.ic_menu_send);
        }

        menu.add(0, MENU_INSERT_SMILEY, 0, R.string.menu_insert_smiley).setIcon(
                com.android.internal.R.drawable.ic_menu_emoticons);

        if (mMsgListAdapter.getCount() > 0) {
            // Removed search as part of b/1205708
            //menu.add(0, MENU_SEARCH, 0, R.string.menu_search).setIcon(
            //        R.drawable.ic_menu_search);
            Cursor cursor = mMsgListAdapter.getCursor();
            if ((null != cursor) && (cursor.getCount() > 0)) {
                menu.add(0, MENU_DELETE_THREAD, 0, R.string.delete_thread).setIcon(
                    android.R.drawable.ic_menu_delete);
            }
        } else {
            menu.add(0, MENU_DISCARD, 0, R.string.discard).setIcon(android.R.drawable.ic_menu_delete);
        }

        menu.add(0, MENU_CONVERSATION_LIST, 0, R.string.all_threads).setIcon(
                com.android.internal.R.drawable.ic_menu_friendslist);

        buildAddAddressToContactMenuItem(menu);
        return true;
    }
    
    private void buildAddAddressToContactMenuItem(Menu menu) {
        if (mRecipientList.hasValidRecipient()) {
            // Look for the first recipient we don't have a contact for and create a menu item to
            // add the number to contacts.
            Iterator<Recipient> recipientIterator = mRecipientList.iterator();
            while (recipientIterator.hasNext()) {
                Recipient r = recipientIterator.next();
                long personId = getPersonId(r);

                if (personId <= 0) {
                    Intent intent = ConversationList.createAddContactIntent(r.number);
                    menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, R.string.menu_add_to_contacts)
                        .setIcon(android.R.drawable.ic_menu_add)
                        .setIntent(intent);
                    break;
                }
            }
        }
    }
    
    private void invalidateRecipientsInCache() {
        ContactInfoCache cache = ContactInfoCache.getInstance();
        Iterator<Recipient> recipientIterator = mRecipientList.iterator();
        while (recipientIterator.hasNext()) {
            Recipient r = recipientIterator.next();
            cache.invalidateContact(r.number);
        }
    }

    private long getPersonId(Recipient r) {
        // The recipient doesn't always have a person_id. This can happen when a user adds
        // a contact in the middle of an activity after the recipient has already been loaded.
        if (r == null) {
            return -1;
        }
        if (r.person_id > 0) {
            return r.person_id;
        }
        ContactInfoCache.CacheEntry entry = ContactInfoCache.getInstance()
            .getContactInfo(this, r.number);
        if (entry.person_id > 0) {
            return entry.person_id;
        }    
        return -1;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD_SUBJECT:
                convertMessageIfNeeded(HAS_SUBJECT, true);
                showSubjectEditor();
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
            case MENU_CALL_RECIPIENT:
                dialRecipient();
                break;
            case MENU_INSERT_SMILEY:
                showSmileyDialog();
                break;
            case MENU_VIEW_CONTACT:
                // View the contact for the first (and only) recipient.
                long personId = getPersonId(mRecipientList.getSingleRecipient());
                if (personId > 0) {
                    viewContact(personId);
                }
                break;
            case MENU_ADD_ADDRESS_TO_CONTACTS:
                return false;   // so the intent attached to the menu item will get launched.
        }

        return true;
    }

    private void addAttachment(int type) {
        switch (type) {
            case AttachmentTypeSelectorAdapter.ADD_IMAGE:
                MessageUtils.selectImage(this, REQUEST_CODE_ATTACH_IMAGE);
                break;

            case AttachmentTypeSelectorAdapter.TAKE_PICTURE: {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, REQUEST_CODE_TAKE_PICTURE);
            }
                break;

            case AttachmentTypeSelectorAdapter.ADD_VIDEO:
                MessageUtils.selectVideo(this, REQUEST_CODE_ATTACH_VIDEO);
                break;

            case AttachmentTypeSelectorAdapter.RECORD_VIDEO: {
                Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
                startActivityForResult(intent, REQUEST_CODE_TAKE_VIDEO);
            }
                break;

            case AttachmentTypeSelectorAdapter.ADD_SOUND:
                MessageUtils.selectAudio(this, REQUEST_CODE_ATTACH_SOUND);
                break;

            case AttachmentTypeSelectorAdapter.RECORD_SOUND:
                MessageUtils.recordSound(this, REQUEST_CODE_RECORD_SOUND);
                break;

            case AttachmentTypeSelectorAdapter.ADD_SLIDESHOW: {
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

                Intent intent = new Intent(this, SlideshowEditActivity.class);
                intent.setData(mMessageUri);
                startActivityForResult(intent, REQUEST_CODE_CREATE_SLIDESHOW);
            }
                break;

            default:
                break;
        }
    }

    private void showAddAttachmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_dialog_attach);
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
        mWaitingForSubActivity = false;     // We're back!
        
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

            case REQUEST_CODE_TAKE_VIDEO:
            case REQUEST_CODE_ATTACH_VIDEO:
                addVideo(data.getData());
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
        
        // Make sure if there was an error that our message type remains correct.
        if (!requiresMms()) {
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

    private final ResizeImageResultCallback mResizeImageCallback = new ResizeImageResultCallback() {
        public void onResizeResult(PduPart part) {
            Context context = ComposeMessageActivity.this;
            Resources r = context.getResources();

            if (part == null) {
                Toast.makeText(context,
                        r.getString(R.string.failed_to_add_media, getPictureString()),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            convertMessageIfNeeded(HAS_ATTACHMENT, true);
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
            MessageUtils.resizeImageAsync(ComposeMessageActivity.this,
                    uri, mAttachmentEditorHandler, mResizeImageCallback);
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

    private void addVideo(Uri uri) {
        try {
            mAttachmentEditor.changeVideo(uri);
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
    }
    
    private boolean handleForwardedMessage() {
        // If this is a forwarded message, it will have an Intent extra
        // indicating so.  If not, bail out.
        if (getIntent().getBooleanExtra("forwarded_message", false) == false) {
            return false;
        }

        // If we are forwarding an MMS, mMessageUri will already be set.
        if (mMessageUri != null) {
            convertMessage(true);
        }
        
        return true;
    }
    
    private boolean handleSendIntent(Intent intent) {
        Bundle extras = intent.getExtras();

        if (!Intent.ACTION_SEND.equals(intent.getAction()) || (extras == null)) {
            return false;
        }
        
        if (extras.containsKey(Intent.EXTRA_STREAM)) {
            Uri uri = (Uri)extras.getParcelable(Intent.EXTRA_STREAM);
            if (uri != null) {
                convertMessage(true);
                if (intent.getType().startsWith("image/")) {
                    addImage(uri);
                } else if (intent.getType().startsWith("video/")) {
                    addVideo(uri);
                }
            }
            return true;
        } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
            mMsgText = extras.getString(Intent.EXTRA_TEXT);
            return true;
        }
        
        return false;
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
        if (slideshow.size() == 0) {
            SlideModel slide = new SlideModel(slideshow);
            slideshow.add(slide);
        }

        if (!slideshow.get(0).hasText()) {
            TextModel text = new TextModel(
                    this, ContentType.TEXT_PLAIN, "text_0.txt",
                    slideshow.getLayout().getTextRegion());
            slideshow.get(0).add(text);
        }
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
                CharSequence text = null;
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

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (event != null) {
            if (!event.isShiftPressed()) {
                if (isPreparedForSending()) {
                    sendMessage();
                }
                return true;
            }
            return false;
        }
        
        if (isPreparedForSending()) {
            confirmSendMessageIfNeeded();
        }
        return true;
    }

    private final TextWatcher mTextEditorWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // This is a workaround for bug 1609057.  Since onUserInteraction() is
            // not called when the user touches the soft keyboard, we pretend it was
            // called when textfields changes.  This should be removed when the bug
            // is fixed.
            onUserInteraction();

            if (requiresMms()) {
                // Update the content of the text model.
                TextModel text = mSlideshow.get(0).getText();
                if (text == null) {
                    text = new TextModel(
                                    ComposeMessageActivity.this,
                                    ContentType.TEXT_PLAIN,
                                    "text_0.txt",
                                    mSlideshow.getLayout().getTextRegion()
                           );
                    mSlideshow.get(0).add(text);
                }

                text.setText(s);
            } else {
                mMsgText = s;
            }

            updateSendButtonState();

            updateCounter(s, start, before, count);
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
        mMsgListView = (MessageListView) findViewById(R.id.history);
        mMsgListView.setDivider(null);      // no divider so we look like IM conversation.
        mBottomPanel = findViewById(R.id.bottom_panel);
        mTextEditor = (EditText) findViewById(R.id.embedded_text_editor);
        mTextEditor.setOnEditorActionListener(this);
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
        if (mThreadId <= 0) {
            return;
        }
        
        // Cancel any pending queries
        mBackgroundQueryHandler.cancelOperation(MESSAGE_LIST_QUERY_TOKEN);
        try {
            // Kick off the new query
            mBackgroundQueryHandler.startQuery(
                    MESSAGE_LIST_QUERY_TOKEN, null, getThreadUri(),
                    PROJECTION, null, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void initMessageList() {
        if (mMsgListAdapter != null) {
            return;
        }
        
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
    }

    private void loadDraft() {
        // If we have no associated thread ID, there can't be a draft.
        if (mThreadId <= 0) {
            return;
        }
       
        // If we already have text, don't stomp on it with the draft.
        if (!TextUtils.isEmpty(mMsgText)) {
            return;
        }
        
        // If we know there is no draft, don't bother to look for one.
        if (!DraftCache.getInstance().hasDraft(mThreadId)) {
            return;
        }
        
        // Try to load an SMS draft; if one does not exist,
        // load an MMS draft.
        mMsgText = readTemporarySmsMessage(mThreadId);
        if (TextUtils.isEmpty(mMsgText)) {
            if (readTemporaryMmsMessage(mThreadId)) {
                convertMessage(true);
            } else {
                Log.e(TAG, "no SMS or MMS drafts in thread " + mThreadId);
                return;
            }
        }
    }
    
    private void asyncDelete(Uri uri, String selection, String[] selectionArgs) {
        if (LOCAL_LOGV) Log.v(TAG, "asyncDelete " + uri);
        mBackgroundQueryHandler.startDelete(0, null, uri, selection, selectionArgs);
    }
    
    private void saveDraft() {
        // Convert back to SMS if we were only in MMS mode because there was
        // a subject and it is empty.
        removeSubjectIfEmpty();

        // If we are in MMS mode but mMessageUri is null, the message has already
        // been discarded.  Just bail out early.
        if (requiresMms() && mMessageUri == null && !mWaitingForSubActivity) {
            return;
        }

        // Throw the message out if it's empty, unless we're in the middle
        // of creating a slideshow or some other subactivity -- don't discard
        // the draft behind the subactivity's back.
        if (isEmptyMessage() && !mWaitingForSubActivity) {
            discardTemporaryMessage();
            DraftCache.getInstance().setDraftState(mThreadId, false);
            return;
        }

        // If the user hasn't typed in a recipient and has managed to
        // get away from us (e.g. by pressing HOME), we don't have any
        // choice but to save an anonymous draft.  Fall through to the
        // normal case but set up an anonymous thread first.
        if (!hasValidRecipient()) {
            setThreadId(getOrCreateThreadId(new String[] {}));
        }
        
        boolean savedAsDraft = false;
        if (requiresMms()) {
            if (isEmptyMms() && !mWaitingForSubActivity) {
                asyncDelete(mMessageUri, null, null);
            } else {
                asyncUpdateTemporaryMmsMessage(mRecipientList.getToNumbers());
                savedAsDraft = true;
            }
        } else {
            if (isEmptySms()) {
                asyncDeleteTemporarySmsMessage(mThreadId);
            } else {
                asyncUpdateTemporarySmsMessage(mRecipientList.getToNumbers(),
                        mMsgText.toString());
                savedAsDraft = true;
            }
        }

        DraftCache.getInstance().setDraftState(mThreadId, savedAsDraft);
        
        if (mToastForDraftSave && savedAsDraft) {
            Toast.makeText(this, R.string.message_saved_as_draft,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static final String[] MMS_DRAFT_PROJECTION = {
        Mms._ID,        // 0
        Mms.SUBJECT     // 1
    };

    private static final int MMS_ID_INDEX       = 0;
    private static final int MMS_SUBJECT_INDEX  = 1;

    private boolean readTemporaryMmsMessage(long threadId) {
        Cursor cursor;

        if (mMessageUri != null) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "readTemporaryMmsMessage: already has message url " + mMessageUri);
            }
            return true;
        }

        final String selection = Mms.THREAD_ID + " = " + threadId;
        cursor = SqliteWrapper.query(this, mContentResolver,
                Mms.Draft.CONTENT_URI, MMS_DRAFT_PROJECTION,
                selection, null, null);

        try {
            if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                mMessageUri = ContentUris.withAppendedId(Mms.Draft.CONTENT_URI,
                        cursor.getLong(MMS_ID_INDEX));

                mSubject = cursor.getString(MMS_SUBJECT_INDEX);
                if (!TextUtils.isEmpty(mSubject)) {
                    updateState(HAS_SUBJECT, true);
                }
                return true;
            }
        } finally {
            cursor.close();
        }

        return false;
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

    private void asyncUpdateTemporaryMmsMessage(final String[] dests) {
        // PduPersister makes database calls and is known to ANR. Do the work on a
        // background thread.
        final SendReq sendReq = new SendReq();
        fillMessageHeaders(sendReq);

        new Thread(new Runnable() {
            public void run() {
                setThreadId(getOrCreateThreadId(dests));
                DraftCache.getInstance().setDraftState(mThreadId, true);
                updateTemporaryMmsMessage(mMessageUri, mPersister,
                        mSlideshow, sendReq);
            }
        }).start();

        // Be paranoid and delete any SMS drafts that might be lying around.
        asyncDeleteTemporarySmsMessage(mThreadId);
    }
    
    public static void updateTemporaryMmsMessage(Uri uri, PduPersister persister,
            SlideshowModel slideshow, SendReq sendReq) {
        persister.updateHeaders(uri, sendReq);
        final PduBody pb = slideshow.toPduBody();

        try {
            persister.updateParts(uri, pb);
        } catch (MmsException e) {
            Log.e(TAG, "updateTemporaryMmsMessage: cannot update message " + uri);
        }

        slideshow.sync(pb);
    }

    private static final String[] SMS_BODY_PROJECTION = { Sms._ID, Sms.BODY };
    private static final String SMS_DRAFT_WHERE = Sms.TYPE + "=" + Sms.MESSAGE_TYPE_DRAFT;
    
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

        Uri thread_uri = ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, thread_id);
        String body = "";

        Cursor c = SqliteWrapper.query(this, mContentResolver,
                        thread_uri, SMS_BODY_PROJECTION, SMS_DRAFT_WHERE, null, null);

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    body = c.getString(1);
                }
            } finally {
                c.close();
            }
        }

        // Clean out drafts for this thread -- if the recipient set changes,
        // we will lose track of the original draft and be unable to delete
        // it later.  The message will be re-saved if necessary upon exit of
        // the activity.
        SqliteWrapper.delete(this, mContentResolver, thread_uri, SMS_DRAFT_WHERE, null);

        return body;
    }

    private void asyncUpdateTemporarySmsMessage(final String[] dests, final String contents) {
        new Thread(new Runnable() {
            public void run() {
                setThreadId(getOrCreateThreadId(dests));
                DraftCache.getInstance().setDraftState(mThreadId, true);
                updateTemporarySmsMessage(mThreadId, contents);
            }
        }).start();
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
        
        Uri thread_uri = ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, thread_id);
        Cursor c = SqliteWrapper.query(this, mContentResolver,
                thread_uri, SMS_BODY_PROJECTION, SMS_DRAFT_WHERE, null, null);

        try {
            if (c.moveToFirst()) {
                ContentValues values = new ContentValues(1);
                values.put(Sms.BODY, contents);
                SqliteWrapper.update(this, mContentResolver, thread_uri, values,
                        SMS_DRAFT_WHERE, null);
            } else {
                ContentValues values = new ContentValues(3);
                values.put(Sms.THREAD_ID, thread_id);
                values.put(Sms.BODY, contents);
                values.put(Sms.TYPE, Sms.MESSAGE_TYPE_DRAFT);
                SqliteWrapper.insert(this, mContentResolver, Sms.CONTENT_URI, values);
                asyncDeleteTemporaryMmsMessage(thread_id);
            }
        } finally {
            c.close();
        }
    }

    private void asyncDeleteTemporarySmsMessage(long threadId) {
        if (threadId > 0) {
            asyncDelete(ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, threadId),
                SMS_DRAFT_WHERE, null);
        }
    }

    private void deleteTemporarySmsMessage(long threadId) {
        SqliteWrapper.delete(this, mContentResolver,
                ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, threadId),
                SMS_DRAFT_WHERE, null);
    }

    private void asyncDeleteTemporaryMmsMessage(long threadId) {
        final String where = Mms.THREAD_ID + " = " + threadId;
        asyncDelete(Mms.Draft.CONTENT_URI, where, null);
    }

    private void abandonDraftsAndFinish() {
        // If we are in MMS mode, first convert the message to SMS,
        // which will cause the MMS draft to be deleted.
        if (mMessageUri != null) {
            convertMessage(false);
        }
        // Now get rid of the SMS text to inhibit saving of a draft.
        mMsgText = "";
        finish();
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

    private long getOrCreateThreadId(String[] numbers) {
        HashSet<String> recipients = new HashSet<String>();
        recipients.addAll(Arrays.asList(numbers));
        return Threads.getOrCreateThreadId(this, recipients);
    }


    private void sendMessage() {
        // Need this for both SMS and MMS.
        final String[] dests = mRecipientList.getToNumbers();
        
        // removeSubjectIfEmpty will convert a message that is solely an MMS
        // message because it has an empty subject back into an SMS message.
        // It doesn't notify the user of the conversion.
        removeSubjectIfEmpty();
        if (requiresMms()) {
            // Make local copies of the bits we need for sending a message,
            // because we will be doing it off of the main thread, which will
            // immediately continue on to resetting some of this state.
            final Uri mmsUri = mMessageUri;
            final PduPersister persister = mPersister;
            final SlideshowModel slideshow = mSlideshow;
            final SendReq sendReq = new SendReq();
            fillMessageHeaders(sendReq);
            
            // Make sure the text in slide 0 is no longer holding onto a reference to the text
            // in the message text box.
            slideshow.prepareForSend();

            // Do the dirty work of sending the message off of the main UI thread.
            new Thread(new Runnable() {
                public void run() {
                    sendMmsWorker(dests, mmsUri, persister, slideshow, sendReq);
                }
            }).start();
        } else {
            // Same rules apply as above.
            final String msgText = mMsgText.toString();
            new Thread(new Runnable() {
                public void run() {
                    sendSmsWorker(dests, msgText);
                }
            }).start();
        }
        
        if (mExitOnSent) {
            // If we are supposed to exit after a message is sent,
            // clear out the text and URIs to inhibit saving of any
            // drafts and call finish().
            mMsgText = "";
            mMessageUri = null;
            finish();
        } else {
            // Otherwise, reset the UI to be ready for the next message.
            resetMessage();
        }
    }
    
    /**
     * Do the actual work of sending a message.  Runs outside of the main thread.
     */
    private void sendSmsWorker(String[] dests, String msgText) {
        // Make sure we are still using the correct thread ID for our
        // recipient set.
        long threadId = getOrCreateThreadId(dests);

        MessageSender sender = new SmsMessageSender(this, dests, msgText, threadId);
        try {
            sender.sendMessage(threadId);
            setThreadId(threadId);
            startMsgListQuery();
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS message, threadId=" + threadId, e);
        }
    }

    private void sendMmsWorker(String[] dests, Uri mmsUri, PduPersister persister,
                               SlideshowModel slideshow, SendReq sendReq) {
        // Make sure we are still using the correct thread ID for our
        // recipient set.
        long threadId = getOrCreateThreadId(dests);

        if (LOCAL_LOGV) Log.v(TAG, "sendMmsWorker: update temporary MMS message " + mmsUri);

        // Sync the MMS message in progress to disk.
        updateTemporaryMmsMessage(mmsUri, persister, slideshow, sendReq);
        // Be paranoid and clean any draft SMS up.
        deleteTemporarySmsMessage(threadId);

        MessageSender sender = new MmsMessageSender(this, mmsUri);
        try {
            if (!sender.sendMessage(threadId)) {
                // The message was sent through SMS protocol, we should
                // delete the copy which was previously saved in MMS drafts.
                SqliteWrapper.delete(this, mContentResolver, mmsUri, null, null);
            }
            
            setThreadId(threadId);
            startMsgListQuery();
        } catch (Exception e) {
            Log.e(TAG, "Failed to send message: " + mmsUri + ", threadId=" + threadId, e);
        }
    }

    private void resetMessage() {
        // Make the attachment editor hide its view before we destroy it.
        if (mAttachmentEditor != null) {
            mAttachmentEditor.hideView();
        }

        // Focus to the text editor.
        mTextEditor.requestFocus();

        // We have to remove the text change listener while the text editor gets cleared and
        // we subsequently turn the message back into SMS. When the listener is listening while
        // doing the clearing, it's fighting to update its counts and itself try and turn
        // the message one way or the other.
        mTextEditor.removeTextChangedListener(mTextEditorWatcher);

        // RECIPIENTS_REQUIRE_MMS is the only state flag that is valid
        // when starting a new message, so preserve only that.
        mMessageState &= RECIPIENTS_REQUIRE_MMS;
        
        // Clear the text box.
        TextKeyListener.clear(mTextEditor.getText());

        // Clear out the slideshow and message URI.  New ones will be
        // created if we are starting a new message as MMS.
        mSlideshow = null;
        mMessageUri = null;
        
        // Empty out text.
        mMsgText = "";
        
        // Convert back to SMS if necessary, or if we still need to
        // be in MMS mode, reset the MMS components.
        if (!requiresMms()) {
            // Start a new message as an SMS.
            convertMessage(false);
        } else {
            // Start a new message as an MMS.
            resetMmsComponents();
        }
        
        drawBottomPanel(AttachmentEditor.TEXT_ONLY);

        // "Or not", in this case.
        updateSendButtonState();
        
        // Hide the recipients editor.
        if (mRecipientsEditor != null) {
            mRecipientsEditor.setVisibility(View.GONE);
            hideTopPanelIfNecessary();
        }

        // Our changes are done. Let the listener respond to text changes once again.
        mTextEditor.addTextChangedListener(mTextEditorWatcher);
        
        // Close the soft on-screen keyboard if we're in landscape mode so the user can see the
        // conversation.
        if (mIsLandscape) {
            InputMethodManager inputMethodManager = 
                (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            
            inputMethodManager.hideSoftInputFromWindow(mTextEditor.getWindowToken(), 0);
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

    private void setThreadId(long threadId) {
        mThreadId = threadId;
    }

    private void initActivityState(Bundle savedInstanceState, Intent intent) {
        if (savedInstanceState != null) {
            setThreadId(savedInstanceState.getLong("thread_id", 0));
            mMessageUri = (Uri) savedInstanceState.getParcelable("msg_uri");
            mExternalAddress = savedInstanceState.getString("address");
            mExitOnSent = savedInstanceState.getBoolean("exit_on_sent", false);
            mSubject = savedInstanceState.getString("subject");
            mMsgText = savedInstanceState.getString("sms_body");
        } else {
            setThreadId(intent.getLongExtra("thread_id", 0));
            mMessageUri = (Uri) intent.getParcelableExtra("msg_uri");
            if ((mMessageUri == null) && (mThreadId == 0)) {
                // If we haven't been given a thread id or a URI in the extras,
                // get it out of the intent.
                Uri uri = intent.getData();
                if ((uri != null) && (uri.getPathSegments().size() >= 2)) {
                    try {
                        setThreadId(Long.parseLong(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException exception) {
                        Log.e(TAG, "Thread ID must be a Long.");
                    }
                }
            }
            mExternalAddress = intent.getStringExtra("address");
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
                mExternalAddress = MessageUtils.getAddressByThreadId(this, mThreadId);
            } else {
                mExternalAddress = deriveAddress(intent);
                // Even if we end up creating a thread here and the user
                // discards the message, we will clean it up later when we
                // delete obsolete threads.
                if (!TextUtils.isEmpty(mExternalAddress)) {
                    setThreadId(getOrCreateThreadId(new String[] { mExternalAddress }));
                }
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
        Iterator<Recipient> iter = mRecipientList.iterator();
        while (iter.hasNext()) {
            Recipient r = iter.next();
            sb.append(r.nameAndNumber).append(", ");
        }

        ContactInfoCache cache = ContactInfoCache.getInstance();
        String[] values = mRecipientList.getBccNumbers();
        if (values.length > 0) {
            sb.append("Bcc: ");
            for (String v : values) {
                sb.append(cache.getContactName(this, v)).append(", ");
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

        // If the recipients editor is visible, there is nothing in it,
        // and the text editor is not already focused, focus the
        // recipients editor.
        if (isRecipientsEditorVisible() && TextUtils.isEmpty(mRecipientsEditor.getText())
                                        && !mTextEditor.isFocused()) {
            mRecipientsEditor.requestFocus();
            return;
        }

        // If we decided not to focus the recipients editor, focus the text editor.
        mTextEditor.requestFocus();
    }

    private final MessageListAdapter.OnDataSetChangedListener
                    mDataSetChangedListener = new MessageListAdapter.OnDataSetChangedListener() {
        public void onDataSetChanged(MessageListAdapter adapter) {
            mPossiblePendingNotification = true;
        }
    };

    private void checkPendingNotification() {
        if (mPossiblePendingNotification && hasWindowFocus()) {
            markAsRead(mThreadId);
            mPossiblePendingNotification = false;
        }
    }

    private void markAsRead(long threadId) {
        if (threadId <= 0) {
            return;
        }
        
        Uri threadUri = ContentUris.withAppendedId(Threads.CONTENT_URI, threadId);
        ContentValues values = new ContentValues(1);
        values.put("read", 1);
        String where = "read = 0";

        mBackgroundQueryHandler.startUpdate(MARK_AS_READ_TOKEN, null,
                                            threadUri, values, where, null);
    }
    
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch(token) {
                case MESSAGE_LIST_QUERY_TOKEN:
                    mMsgListAdapter.changeCursor(cursor);
                    
                    // Once we have completed the query for the message history, if
                    // there is nothing in the cursor and we are not composing a new
                    // message, we must be editing a draft in a new conversation.
                    // Show the recipients editor to give the user a chance to add
                    // more people before the conversation begins.
                    if (cursor.getCount() == 0 && !isRecipientsEditorVisible()) {
                        initRecipientsEditor();
                    }

                    // FIXME: freshing layout changes the focused view to an unexpected
                    // one, set it back to TextEditor forcely.
                    mTextEditor.requestFocus();

                    return;

                case CALLER_ID_QUERY_TOKEN:
                case EMAIL_CONTACT_QUERY_TOKEN:
                    cleanupContactInfoCursor();
                    mContactInfoCursor = cursor;
                    updateContactInfo();
                    startPresencePollingRequest();
                    return;

            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            switch(token) {
            case DELETE_MESSAGE_TOKEN:
            case DELETE_CONVERSATION_TOKEN:
                // Update the notification for new messages since they
                // may be deleted.
                MessagingNotification.updateNewMessageIndicator(
                        ComposeMessageActivity.this);
                // Update the notification for failed messages since they
                // may be deleted.
                updateSendFailedNotification();
                break;
            }

            if (token == DELETE_CONVERSATION_TOKEN) {
                ComposeMessageActivity.this.abandonDraftsAndFinish();
            }
        }
        
        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            switch(token) {
            case MARK_AS_READ_TOKEN:
                MessagingNotification.updateAllNotifications(ComposeMessageActivity.this);
                break;
            }
        }
    }

    private void showSmileyDialog() {
        if (mSmileyDialog == null) {
            int[] icons = SmileyParser.DEFAULT_SMILEY_RES_IDS;
            String[] names = getResources().getStringArray(
                    SmileyParser.DEFAULT_SMILEY_NAMES);
            final String[] texts = getResources().getStringArray(
                    SmileyParser.DEFAULT_SMILEY_TEXTS);

            final int N = names.length;

            List<Map<String, ?>> entries = new ArrayList<Map<String, ?>>();
            for (int i = 0; i < N; i++) {
                // We might have different ASCII for the same icon, skip it if
                // the icon is already added.
                boolean added = false;
                for (int j = 0; j < i; j++) {
                    if (icons[i] == icons[j]) {
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    HashMap<String, Object> entry = new HashMap<String, Object>();

                    entry. put("icon", icons[i]);
                    entry. put("name", names[i]);
                    entry.put("text", texts[i]);

                    entries.add(entry);
                }
            }

            final SimpleAdapter a = new SimpleAdapter(
                    this,
                    entries,
                    R.layout.smiley_menu_item,
                    new String[] {"icon", "name", "text"},
                    new int[] {R.id.smiley_icon, R.id.smiley_name, R.id.smiley_text});
            SimpleAdapter.ViewBinder viewBinder = new SimpleAdapter.ViewBinder() {
                public boolean setViewValue(View view, Object data, String textRepresentation) {
                    if (view instanceof ImageView) {
                        Drawable img = getResources().getDrawable((Integer)data);
                        ((ImageView)view).setImageDrawable(img);
                        return true;
                    }
                    return false;
                }
            };
            a.setViewBinder(viewBinder);

            AlertDialog.Builder b = new AlertDialog.Builder(this);

            b.setTitle(getString(R.string.menu_insert_smiley));

            b.setCancelable(true);
            b.setAdapter(a, new DialogInterface.OnClickListener() {
                public final void onClick(DialogInterface dialog, int which) {
                    HashMap<String, Object> item = (HashMap<String, Object>) a.getItem(which);
                    mTextEditor.append((String)item.get("text"));
                }
            });

            mSmileyDialog = b.create();
        }

        mSmileyDialog.show();
    }
    
    private void cleanupContactInfoCursor() {
        if (mContactInfoCursor != null) {
            mContactInfoCursor.close();
        }
    }
    
    private void cancelPresencePollingRequests() {
        mPresencePollingHandler.removeMessages(REFRESH_PRESENCE);
    }
    
    private void startPresencePollingRequest() {
        mPresencePollingHandler.sendEmptyMessageDelayed(REFRESH_PRESENCE,
                60 * 1000); // refresh every minute
    }
    
    private void startQueryForContactInfo() {
        String number = mRecipientList.getSingleRecipientNumber();
        cancelPresencePollingRequests();    // make sure there are no outstanding polling requests
        if (TextUtils.isEmpty(number)) {
            setPresenceIcon(0);
            startPresencePollingRequest();
            return;
        }

        mContactInfoSelectionArgs[0] = number;

        if (Mms.isEmailAddress(number)) {
            // Cancel any pending queries
            mBackgroundQueryHandler.cancelOperation(EMAIL_CONTACT_QUERY_TOKEN);

            mBackgroundQueryHandler.startQuery(EMAIL_CONTACT_QUERY_TOKEN, null,
                    METHOD_WITH_PRESENCE_URI,
                    EMAIL_QUERY_PROJECTION,
                    METHOD_LOOKUP,
                    mContactInfoSelectionArgs,
                    null);
        } else {
            // Cancel any pending queries
            mBackgroundQueryHandler.cancelOperation(CALLER_ID_QUERY_TOKEN);

            mBackgroundQueryHandler.startQuery(CALLER_ID_QUERY_TOKEN, null,
                    PHONES_WITH_PRESENCE_URI,
                    CALLER_ID_PROJECTION,
                    NUMBER_LOOKUP,
                    mContactInfoSelectionArgs,
                    null);
        }
    }

    private void updateContactInfo() {
        boolean updated = false;
        if (mContactInfoCursor != null && mContactInfoCursor.moveToFirst()) {
            mPresenceStatus = mContactInfoCursor.getInt(PRESENCE_STATUS_COLUMN);
            if (mPresenceStatus != Contacts.People.OFFLINE) {
                int presenceIcon = Presence.getPresenceIconResourceId(mPresenceStatus);
                setPresenceIcon(presenceIcon);
                updated = true;
            }
        } 
        if (!updated) {
            setPresenceIcon(0);
        }
    }

}
