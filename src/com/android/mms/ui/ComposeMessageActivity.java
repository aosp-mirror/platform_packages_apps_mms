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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.drm.DrmStore;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;
import android.provider.Telephony;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.Settings;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.view.ViewStub;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.TempFileProvider;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.data.Conversation.ConversationQueryHandler;
import com.android.mms.data.WorkingMessage;
import com.android.mms.data.WorkingMessage.MessageStatusListener;
import com.android.mms.drm.DrmUtils;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.ui.MessageListView.OnSizeChangedListener;
import com.android.mms.ui.MessageUtils.ResizeImageResultCallback;
import com.android.mms.ui.RecipientsEditor.RecipientContextMenuInfo;
import com.android.mms.util.DraftCache;
import com.android.mms.util.PhoneNumberFormatter;
import com.android.mms.util.SendingProgressTokenManager;
import com.android.mms.widget.MmsWidgetProvider;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendReq;

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
        MessageStatusListener, Contact.UpdateListener {
    public static final int REQUEST_CODE_ATTACH_IMAGE     = 100;
    public static final int REQUEST_CODE_TAKE_PICTURE     = 101;
    public static final int REQUEST_CODE_ATTACH_VIDEO     = 102;
    public static final int REQUEST_CODE_TAKE_VIDEO       = 103;
    public static final int REQUEST_CODE_ATTACH_SOUND     = 104;
    public static final int REQUEST_CODE_RECORD_SOUND     = 105;
    public static final int REQUEST_CODE_CREATE_SLIDESHOW = 106;
    public static final int REQUEST_CODE_ECM_EXIT_DIALOG  = 107;
    public static final int REQUEST_CODE_ADD_CONTACT      = 108;
    public static final int REQUEST_CODE_PICK             = 109;

    private static final String TAG = LogTag.TAG;

    private static final boolean DEBUG = false;
    private static final boolean TRACE = false;
    private static final boolean LOCAL_LOGV = false;

    // Menu ID
    private static final int MENU_ADD_SUBJECT           = 0;
    private static final int MENU_DELETE_THREAD         = 1;
    private static final int MENU_ADD_ATTACHMENT        = 2;
    private static final int MENU_DISCARD               = 3;
    private static final int MENU_SEND                  = 4;
    private static final int MENU_CALL_RECIPIENT        = 5;
    private static final int MENU_CONVERSATION_LIST     = 6;
    private static final int MENU_DEBUG_DUMP            = 7;

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
    private static final int MENU_ADD_ADDRESS_TO_CONTACTS = 27;
    private static final int MENU_LOCK_MESSAGE          = 28;
    private static final int MENU_UNLOCK_MESSAGE        = 29;
    private static final int MENU_SAVE_RINGTONE         = 30;
    private static final int MENU_PREFERENCES           = 31;
    private static final int MENU_GROUP_PARTICIPANTS    = 32;

    private static final int RECIPIENTS_MAX_LENGTH = 312;

    private static final int MESSAGE_LIST_QUERY_TOKEN = 9527;
    private static final int MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN = 9528;

    private static final int DELETE_MESSAGE_TOKEN  = 9700;

    private static final int CHARS_REMAINING_BEFORE_COUNTER_SHOWN = 10;

    private static final long NO_DATE_FOR_DIALOG = -1L;

    private static final String KEY_EXIT_ON_SENT = "exit_on_sent";
    private static final String KEY_FORWARDED_MESSAGE = "forwarded_message";

    private static final String EXIT_ECM_RESULT = "exit_ecm_result";

    // When the conversation has a lot of messages and a new message is sent, the list is scrolled
    // so the user sees the just sent message. If we have to scroll the list more than 20 items,
    // then a scroll shortcut is invoked to move the list near the end before scrolling.
    private static final int MAX_ITEMS_TO_INVOKE_SCROLL_SHORTCUT = 20;

    // Any change in height in the message list view greater than this threshold will not
    // cause a smooth scroll. Instead, we jump the list directly to the desired position.
    private static final int SMOOTH_SCROLL_THRESHOLD = 200;

    // To reduce janky interaction when message history + draft loads and keyboard opening
    // query the messages + draft after the keyboard opens. This controls that behavior.
    private static final boolean DEFER_LOADING_MESSAGES_AND_DRAFT = true;

    // The max amount of delay before we force load messages and draft.
    // 500ms is determined empirically. We want keyboard to have a chance to be shown before
    // we force loading. However, there is at least one use case where the keyboard never shows
    // even if we tell it to (turning off and on the screen). So we need to force load the
    // messages+draft after the max delay.
    private static final int LOADING_MESSAGES_AND_DRAFT_MAX_DELAY_MS = 500;

    private ContentResolver mContentResolver;

    private BackgroundQueryHandler mBackgroundQueryHandler;

    private Conversation mConversation;     // Conversation we are working in

    // When mSendDiscreetMode is true, this activity only allows a user to type in and send
    // a single sms, send the message, and then exits. The message history and menus are hidden.
    private boolean mSendDiscreetMode;
    private boolean mForwardMessageMode;

    private View mTopPanel;                 // View containing the recipient and subject editors
    private View mBottomPanel;              // View containing the text editor, send button, ec.
    private EditText mTextEditor;           // Text editor to type your message into
    private TextView mTextCounter;          // Shows the number of characters used in text editor
    private TextView mSendButtonMms;        // Press to send mms
    private ImageButton mSendButtonSms;     // Press to send sms
    private EditText mSubjectTextEditor;    // Text editor for MMS subject

    private AttachmentEditor mAttachmentEditor;
    private View mAttachmentEditorScrollView;

    private MessageListView mMsgListView;        // ListView for messages in this conversation
    public MessageListAdapter mMsgListAdapter;  // and its corresponding ListAdapter

    private RecipientsEditor mRecipientsEditor;  // UI control for editing recipients
    private ImageButton mRecipientsPicker;       // UI control for recipients picker

    // For HW keyboard, 'mIsKeyboardOpen' indicates if the HW keyboard is open.
    // For SW keyboard, 'mIsKeyboardOpen' should always be true.
    private boolean mIsKeyboardOpen;
    private boolean mIsLandscape;                // Whether we're in landscape mode

    private boolean mToastForDraftSave;   // Whether to notify the user that a draft is being saved

    private boolean mSentMessage;       // true if the user has sent a message while in this
                                        // activity. On a new compose message case, when the first
                                        // message is sent is a MMS w/ attachment, the list blanks
                                        // for a second before showing the sent message. But we'd
                                        // think the message list is empty, thus show the recipients
                                        // editor thinking it's a draft message. This flag should
                                        // help clarify the situation.

    private WorkingMessage mWorkingMessage;         // The message currently being composed.

    private boolean mWaitingForSubActivity;
    private int mLastRecipientCount;            // Used for warning the user on too many recipients.
    private AttachmentTypeSelectorAdapter mAttachmentTypeSelectorAdapter;

    private boolean mSendingMessage;    // Indicates the current message is sending, and shouldn't send again.

    private Intent mAddContactIntent;   // Intent used to add a new contact

    private Uri mTempMmsUri;            // Only used as a temporary to hold a slideshow uri
    private long mTempThreadId;         // Only used as a temporary to hold a threadId

    private AsyncDialog mAsyncDialog;   // Used for background tasks.

    private String mDebugRecipients;
    private int mLastSmoothScrollPosition;
    private boolean mScrollOnSend;      // Flag that we need to scroll the list to the end.

    private int mSavedScrollPosition = -1;  // we save the ListView's scroll position in onPause(),
                                            // so we can remember it after re-entering the activity.
                                            // If the value >= 0, then we jump to that line. If the
                                            // value is maxint, then we jump to the end.
    private long mLastMessageId;

    /**
     * Whether this activity is currently running (i.e. not paused)
     */
    private boolean mIsRunning;

    // we may call loadMessageAndDraft() from a few different places. This is used to make
    // sure we only load message+draft once.
    private boolean mMessagesAndDraftLoaded;

    // whether we should load the draft. For example, after attaching a photo and coming back
    // in onActivityResult(), we should not load the draft because that will mess up the draft
    // state of mWorkingMessage. Also, if we are handling a Send or Forward Message Intent,
    // we should not load the draft.
    private boolean mShouldLoadDraft;

    // Whether or not we are currently enabled for SMS. This field is updated in onStart to make
    // sure we notice if the user has changed the default SMS app.
    private boolean mIsSmsEnabled;

    private Handler mHandler = new Handler();

    // keys for extras and icicles
    public final static String THREAD_ID = "thread_id";
    private final static String RECIPIENTS = "recipients";

    @SuppressWarnings("unused")
    public static void log(String logMsg) {
        Thread current = Thread.currentThread();
        long tid = current.getId();
        StackTraceElement[] stack = current.getStackTrace();
        String methodName = stack[3].getMethodName();
        // Prepend current thread ID and name of calling method to the message.
        logMsg = "[" + tid + "] [" + methodName + "] " + logMsg;
        Log.d(TAG, logMsg);
    }

    //==========================================================
    // Inner classes
    //==========================================================

    private void editSlideshow() {
        // The user wants to edit the slideshow. That requires us to persist the slideshow to
        // disk as a PDU in saveAsMms. This code below does that persisting in a background
        // task. If the task takes longer than a half second, a progress dialog is displayed.
        // Once the PDU persisting is done, another runnable on the UI thread get executed to start
        // the SlideshowEditActivity.
        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                // This runnable gets run in a background thread.
                mTempMmsUri = mWorkingMessage.saveAsMms(false);
            }
        }, new Runnable() {
            @Override
            public void run() {
                // Once the above background thread is complete, this runnable is run
                // on the UI thread.
                if (mTempMmsUri == null) {
                    return;
                }
                Intent intent = new Intent(ComposeMessageActivity.this,
                        SlideshowEditActivity.class);
                intent.setData(mTempMmsUri);
                startActivityForResult(intent, REQUEST_CODE_CREATE_SLIDESHOW);
            }
        }, R.string.building_slideshow_title);
    }

    private final Handler mAttachmentEditorHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AttachmentEditor.MSG_EDIT_SLIDESHOW: {
                    editSlideshow();
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
                    viewMmsMessageAttachment(msg.what);
                    break;

                case AttachmentEditor.MSG_REPLACE_IMAGE:
                case AttachmentEditor.MSG_REPLACE_VIDEO:
                case AttachmentEditor.MSG_REPLACE_AUDIO:
                    showAddAttachmentDialog(true);
                    break;

                case AttachmentEditor.MSG_REMOVE_ATTACHMENT:
                    mWorkingMessage.removeAttachment(true);
                    break;

                default:
                    break;
            }
        }
    };


    private void viewMmsMessageAttachment(final int requestCode) {
        SlideshowModel slideshow = mWorkingMessage.getSlideshow();
        if (slideshow == null) {
            throw new IllegalStateException("mWorkingMessage.getSlideshow() == null");
        }
        if (slideshow.isSimple()) {
            MessageUtils.viewSimpleSlideshow(this, slideshow);
        } else {
            // The user wants to view the slideshow. That requires us to persist the slideshow to
            // disk as a PDU in saveAsMms. This code below does that persisting in a background
            // task. If the task takes longer than a half second, a progress dialog is displayed.
            // Once the PDU persisting is done, another runnable on the UI thread get executed to
            // start the SlideshowActivity.
            getAsyncDialog().runAsync(new Runnable() {
                @Override
                public void run() {
                    // This runnable gets run in a background thread.
                    mTempMmsUri = mWorkingMessage.saveAsMms(false);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    // Once the above background thread is complete, this runnable is run
                    // on the UI thread.
                    if (mTempMmsUri == null) {
                        return;
                    }
                    MessageUtils.launchSlideshowActivity(ComposeMessageActivity.this, mTempMmsUri,
                            requestCode);
                }
            }, R.string.building_slideshow_title);
        }
    }


    private final Handler mMessageListItemHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            MessageItem msgItem = (MessageItem) msg.obj;
            if (msgItem != null) {
                switch (msg.what) {
                    case MessageListItem.MSG_LIST_DETAILS:
                        showMessageDetails(msgItem);
                        break;

                    case MessageListItem.MSG_LIST_EDIT:
                        editMessageItem(msgItem);
                        drawBottomPanel();
                        break;

                    case MessageListItem.MSG_LIST_PLAY:
                        switch (msgItem.mAttachmentType) {
                            case WorkingMessage.IMAGE:
                            case WorkingMessage.VIDEO:
                            case WorkingMessage.AUDIO:
                            case WorkingMessage.SLIDESHOW:
                                MessageUtils.viewMmsMessageAttachment(ComposeMessageActivity.this,
                                        msgItem.mMessageUri, msgItem.mSlideshow,
                                        getAsyncDialog());
                                break;
                        }
                        break;

                    default:
                        Log.w(TAG, "Unknown message: " + msg.what);
                        return;
                }
            }
        }
    };

    private boolean showMessageDetails(MessageItem msgItem) {
        Cursor cursor = mMsgListAdapter.getCursorForItem(msgItem);
        if (cursor == null) {
            return false;
        }
        String messageDetails = MessageUtils.getMessageDetails(
                ComposeMessageActivity.this, cursor, msgItem.mMessageSize);
        new AlertDialog.Builder(ComposeMessageActivity.this)
                .setTitle(R.string.message_details_title)
                .setMessage(messageDetails)
                .setCancelable(true)
                .show();
        return true;
    }

    private final OnKeyListener mSubjectKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            // When the subject editor is empty, press "DEL" to hide the input field.
            if ((keyCode == KeyEvent.KEYCODE_DEL) && (mSubjectTextEditor.length() == 0)) {
                showSubjectEditor(false);
                mWorkingMessage.setSubject(null, true);
                return true;
            }
            return false;
        }
    };

    /**
     * Return the messageItem associated with the type ("mms" or "sms") and message id.
     * @param type Type of the message: "mms" or "sms"
     * @param msgId Message id of the message. This is the _id of the sms or pdu row and is
     * stored in the MessageItem
     * @param createFromCursorIfNotInCache true if the item is not found in the MessageListAdapter's
     * cache and the code can create a new MessageItem based on the position of the current cursor.
     * If false, the function returns null if the MessageItem isn't in the cache.
     * @return MessageItem or null if not found and createFromCursorIfNotInCache is false
     */
    private MessageItem getMessageItem(String type, long msgId,
            boolean createFromCursorIfNotInCache) {
        return mMsgListAdapter.getCachedMessageItem(type, msgId,
                createFromCursorIfNotInCache ? mMsgListAdapter.getCursor() : null);
    }

    private boolean isCursorValid() {
        // Check whether the cursor is valid or not.
        Cursor cursor = mMsgListAdapter.getCursor();
        if (cursor.isClosed() || cursor.isBeforeFirst() || cursor.isAfterLast()) {
            Log.e(TAG, "Bad cursor.", new RuntimeException());
            return false;
        }
        return true;
    }

    private void resetCounter() {
        mTextCounter.setText("");
        mTextCounter.setVisibility(View.GONE);
    }

    private void updateCounter(CharSequence text, int start, int before, int count) {
        WorkingMessage workingMessage = mWorkingMessage;
        if (workingMessage.requiresMms()) {
            // If we're not removing text (i.e. no chance of converting back to SMS
            // because of this change) and we're in MMS mode, just bail out since we
            // then won't have to calculate the length unnecessarily.
            final boolean textRemoved = (before > count);
            if (!textRemoved) {
                showSmsOrMmsSendButton(workingMessage.requiresMms());
                return;
            }
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

        if (!MmsConfig.getMultipartSmsEnabled()) {
            // The provider doesn't support multi-part sms's so as soon as the user types
            // an sms longer than one segment, we have to turn the message into an mms.
            mWorkingMessage.setLengthRequiresMms(msgCount > 1, true);
        } else {
            int threshold = MmsConfig.getSmsToMmsTextThreshold();
            mWorkingMessage.setLengthRequiresMms(threshold > 0 && msgCount > threshold, true);
        }

        // Show the counter only if:
        // - We are not in MMS mode
        // - We are going to send more than one message OR we are getting close
        boolean showCounter = false;
        if (!workingMessage.requiresMms() &&
                (msgCount > 1 ||
                 remainingInCurrentMessage <= CHARS_REMAINING_BEFORE_COUNTER_SHOWN)) {
            showCounter = true;
        }

        showSmsOrMmsSendButton(workingMessage.requiresMms());

        if (showCounter) {
            // Update the remaining characters and number of messages required.
            String counterText = msgCount > 1 ? remainingInCurrentMessage + " / " + msgCount
                    : String.valueOf(remainingInCurrentMessage);
            mTextCounter.setText(counterText);
            mTextCounter.setVisibility(View.VISIBLE);
        } else {
            mTextCounter.setVisibility(View.GONE);
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode)
    {
        // requestCode >= 0 means the activity in question is a sub-activity.
        if (requestCode >= 0) {
            mWaitingForSubActivity = true;
        }
        // The camera and other activities take a long time to hide the keyboard so we pre-hide
        // it here. However, if we're opening up the quick contact window while typing, don't
        // mess with the keyboard.
        if (mIsKeyboardOpen && !QuickContact.ACTION_QUICK_CONTACT.equals(intent.getAction())) {
            hideKeyboard();
        }

        super.startActivityForResult(intent, requestCode);
    }

    private void showConvertToMmsToast() {
        Toast.makeText(this, R.string.converting_to_picture_message, Toast.LENGTH_SHORT).show();
    }

    private class DeleteMessageListener implements OnClickListener {
        private final MessageItem mMessageItem;

        public DeleteMessageListener(MessageItem messageItem) {
            mMessageItem = messageItem;
        }

        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            dialog.dismiss();

            new AsyncTask<Void, Void, Void>() {
                protected Void doInBackground(Void... none) {
                    if (mMessageItem.isMms()) {
                        WorkingMessage.removeThumbnailsFromCache(mMessageItem.getSlideshow());

                        MmsApp.getApplication().getPduLoaderManager()
                            .removePdu(mMessageItem.mMessageUri);
                        // Delete the message *after* we've removed the thumbnails because we
                        // need the pdu and slideshow for removeThumbnailsFromCache to work.
                    }
                    Boolean deletingLastItem = false;
                    Cursor cursor = mMsgListAdapter != null ? mMsgListAdapter.getCursor() : null;
                    if (cursor != null) {
                        cursor.moveToLast();
                        long msgId = cursor.getLong(COLUMN_ID);
                        deletingLastItem = msgId == mMessageItem.mMsgId;
                    }
                    mBackgroundQueryHandler.startDelete(DELETE_MESSAGE_TOKEN,
                            deletingLastItem, mMessageItem.mMessageUri,
                            mMessageItem.mLocked ? null : "locked=0", null);
                    return null;
                }
            }.execute();
        }
    }

    private class DiscardDraftListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            mWorkingMessage.discard();
            dialog.dismiss();
            finish();
        }
    }

    private class SendIgnoreInvalidRecipientListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            sendMessage(true);
            dialog.dismiss();
        }
    }

    private class CancelSendingListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            if (isRecipientsEditorVisible()) {
                mRecipientsEditor.requestFocus();
            }
            dialog.dismiss();
        }
    }

    private void confirmSendMessageIfNeeded() {
        if (!isRecipientsEditorVisible()) {
            sendMessage(true);
            return;
        }

        boolean isMms = mWorkingMessage.requiresMms();
        if (mRecipientsEditor.hasInvalidRecipient(isMms)) {
            if (mRecipientsEditor.hasValidRecipient(isMms)) {
                String title = getResourcesString(R.string.has_invalid_recipient,
                        mRecipientsEditor.formatInvalidNumbers(isMms));
                new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(R.string.invalid_recipient_message)
                    .setPositiveButton(R.string.try_to_send,
                            new SendIgnoreInvalidRecipientListener())
                    .setNegativeButton(R.string.no, new CancelSendingListener())
                    .show();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle(R.string.cannot_send_message)
                    .setMessage(R.string.cannot_send_message_reason)
                    .setPositiveButton(R.string.yes, new CancelSendingListener())
                    .show();
            }
        } else {
            // The recipients editor is still open. Make sure we use what's showing there
            // as the destination.
            ContactList contacts = mRecipientsEditor.constructContactsFromInput(false);
            mDebugRecipients = contacts.serialize();
            sendMessage(true);
        }
    }

    private final TextWatcher mRecipientsWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // This is a workaround for bug 1609057.  Since onUserInteraction() is
            // not called when the user touches the soft keyboard, we pretend it was
            // called when textfields changes.  This should be removed when the bug
            // is fixed.
            onUserInteraction();
        }

        @Override
        public void afterTextChanged(Editable s) {
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
                Log.w(TAG,
                     "RecipientsWatcher: afterTextChanged called with invisible mRecipientsEditor");
                return;
            }

            List<String> numbers = mRecipientsEditor.getNumbers();
            mWorkingMessage.setWorkingRecipients(numbers);
            boolean multiRecipients = numbers != null && numbers.size() > 1;
            mMsgListAdapter.setIsGroupConversation(multiRecipients);
            mWorkingMessage.setHasMultipleRecipients(multiRecipients, true);
            mWorkingMessage.setHasEmail(mRecipientsEditor.containsEmail(), true);

            checkForTooManyRecipients();

            // Walk backwards in the text box, skipping spaces.  If the last
            // character is a comma, update the title bar.
            for (int pos = s.length() - 1; pos >= 0; pos--) {
                char c = s.charAt(pos);
                if (c == ' ')
                    continue;

                if (c == ',') {
                    ContactList contacts = mRecipientsEditor.constructContactsFromInput(false);
                    updateTitle(contacts);
                }

                break;
            }

            // If we have gone to zero recipients, disable send button.
            updateSendButtonState();
        }
    };

    private void checkForTooManyRecipients() {
        final int recipientLimit = MmsConfig.getRecipientLimit();
        if (recipientLimit != Integer.MAX_VALUE && recipientLimit > 0) {
            final int recipientCount = recipientCount();
            boolean tooMany = recipientCount > recipientLimit;

            if (recipientCount != mLastRecipientCount) {
                // Don't warn the user on every character they type when they're over the limit,
                // only when the actual # of recipients changes.
                mLastRecipientCount = recipientCount;
                if (tooMany) {
                    String tooManyMsg = getString(R.string.too_many_recipients, recipientCount,
                            recipientLimit);
                    Toast.makeText(ComposeMessageActivity.this,
                            tooManyMsg, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private final OnCreateContextMenuListener mRecipientsMenuCreateListener =
        new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            if (menuInfo != null) {
                Contact c = ((RecipientContextMenuInfo) menuInfo).recipient;
                RecipientsMenuClickListener l = new RecipientsMenuClickListener(c);

                menu.setHeaderTitle(c.getName());

                if (c.existsInDatabase()) {
                    menu.add(0, MENU_VIEW_CONTACT, 0, R.string.menu_view_contact)
                            .setOnMenuItemClickListener(l);
                } else if (canAddToContacts(c)){
                    menu.add(0, MENU_ADD_TO_CONTACTS, 0, R.string.menu_add_to_contacts)
                            .setOnMenuItemClickListener(l);
                }
            }
        }
    };

    private final class RecipientsMenuClickListener implements MenuItem.OnMenuItemClickListener {
        private final Contact mRecipient;

        RecipientsMenuClickListener(Contact recipient) {
            mRecipient = recipient;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                // Context menu handlers for the recipients editor.
                case MENU_VIEW_CONTACT: {
                    Uri contactUri = mRecipient.getUri();
                    Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    startActivity(intent);
                    return true;
                }
                case MENU_ADD_TO_CONTACTS: {
                    mAddContactIntent = ConversationList.createAddContactIntent(
                            mRecipient.getNumber());
                    ComposeMessageActivity.this.startActivityForResult(mAddContactIntent,
                            REQUEST_CODE_ADD_CONTACT);
                    return true;
                }
            }
            return false;
        }
    }

    private boolean canAddToContacts(Contact contact) {
        // There are some kind of automated messages, like STK messages, that we don't want
        // to add to contacts. These names begin with special characters, like, "*Info".
        final String name = contact.getName();
        if (!TextUtils.isEmpty(contact.getNumber())) {
            char c = contact.getNumber().charAt(0);
            if (isSpecialChar(c)) {
                return false;
            }
        }
        if (!TextUtils.isEmpty(name)) {
            char c = name.charAt(0);
            if (isSpecialChar(c)) {
                return false;
            }
        }
        if (!(Mms.isEmailAddress(name) ||
                Telephony.Mms.isPhoneNumber(name) ||
                contact.isMe())) {
            return false;
        }
        return true;
    }

    private boolean isSpecialChar(char c) {
        return c == '*' || c == '%' || c == '$';
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
        if (TextUtils.isEmpty(msgItem.mBody)) {
            return;
        }
        SpannableString msg = new SpannableString(msgItem.mBody);
        Linkify.addLinks(msg, Linkify.ALL);
        ArrayList<String> uris =
            MessageUtils.extractUris(msg.getSpans(0, msg.length(), URLSpan.class));

        // Remove any dupes so they don't get added to the menu multiple times
        HashSet<String> collapsedUris = new HashSet<String>();
        for (String uri : uris) {
            collapsedUris.add(uri.toLowerCase());
        }
        for (String uriString : collapsedUris) {
            String prefix = null;
            int sep = uriString.indexOf(":");
            if (sep >= 0) {
                prefix = uriString.substring(0, sep);
                uriString = uriString.substring(sep + 1);
            }
            Uri contactUri = null;
            boolean knownPrefix = true;
            if ("mailto".equalsIgnoreCase(prefix))  {
                contactUri = getContactUriForEmail(uriString);
            } else if ("tel".equalsIgnoreCase(prefix)) {
                contactUri = getContactUriForPhoneNumber(uriString);
            } else {
                knownPrefix = false;
            }
            if (knownPrefix && contactUri == null) {
                Intent intent = ConversationList.createAddContactIntent(uriString);

                String addContactString = getString(R.string.menu_add_address_to_contacts,
                        uriString);
                menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, addContactString)
                    .setOnMenuItemClickListener(l)
                    .setIntent(intent);
            }
        }
    }

    private Uri getContactUriForEmail(String emailAddress) {
        Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
                Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(emailAddress)),
                new String[] { Email.CONTACT_ID, Contacts.DISPLAY_NAME }, null, null, null);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    if (!TextUtils.isEmpty(name)) {
                        return ContentUris.withAppendedId(Contacts.CONTENT_URI, cursor.getLong(0));
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    private Uri getContactUriForPhoneNumber(String phoneNumber) {
        Contact contact = Contact.get(phoneNumber, false);
        if (contact.existsInDatabase()) {
            return contact.getUri();
        }
        return null;
    }

    private final OnCreateContextMenuListener mMsgListMenuCreateListener =
        new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (!isCursorValid()) {
                return;
            }
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

            MsgListMenuClickListener l = new MsgListMenuClickListener(msgItem);

            // It is unclear what would make most sense for copying an MMS message
            // to the clipboard, so we currently do SMS only.
            if (msgItem.isSms()) {
                // Message type is sms. Only allow "edit" if the message has a single recipient
                if (getRecipients().size() == 1 &&
                        (msgItem.mBoxId == Sms.MESSAGE_TYPE_OUTBOX ||
                                msgItem.mBoxId == Sms.MESSAGE_TYPE_FAILED)) {
                    menu.add(0, MENU_EDIT_MESSAGE, 0, R.string.menu_edit)
                    .setOnMenuItemClickListener(l);
                }

                menu.add(0, MENU_COPY_MESSAGE_TEXT, 0, R.string.copy_message_text)
                .setOnMenuItemClickListener(l);
            }

            addCallAndContactMenuItems(menu, l, msgItem);

            // Forward is not available for undownloaded messages.
            if (msgItem.isDownloaded() && (msgItem.isSms() || isForwardable(msgId))
                    && mIsSmsEnabled) {
                menu.add(0, MENU_FORWARD_MESSAGE, 0, R.string.menu_forward)
                        .setOnMenuItemClickListener(l);
            }

            if (msgItem.isMms()) {
                switch (msgItem.mBoxId) {
                    case Mms.MESSAGE_BOX_INBOX:
                        break;
                    case Mms.MESSAGE_BOX_OUTBOX:
                        // Since we currently break outgoing messages to multiple
                        // recipients into one message per recipient, only allow
                        // editing a message for single-recipient conversations.
                        if (getRecipients().size() == 1) {
                            menu.add(0, MENU_EDIT_MESSAGE, 0, R.string.menu_edit)
                                    .setOnMenuItemClickListener(l);
                        }
                        break;
                }
                switch (msgItem.mAttachmentType) {
                    case WorkingMessage.TEXT:
                        break;
                    case WorkingMessage.VIDEO:
                    case WorkingMessage.IMAGE:
                        if (haveSomethingToCopyToSDCard(msgItem.mMsgId)) {
                            menu.add(0, MENU_COPY_TO_SDCARD, 0, R.string.copy_to_sdcard)
                            .setOnMenuItemClickListener(l);
                        }
                        break;
                    case WorkingMessage.SLIDESHOW:
                    default:
                        menu.add(0, MENU_VIEW_SLIDESHOW, 0, R.string.view_slideshow)
                        .setOnMenuItemClickListener(l);
                        if (haveSomethingToCopyToSDCard(msgItem.mMsgId)) {
                            menu.add(0, MENU_COPY_TO_SDCARD, 0, R.string.copy_to_sdcard)
                            .setOnMenuItemClickListener(l);
                        }
                        if (isDrmRingtoneWithRights(msgItem.mMsgId)) {
                            menu.add(0, MENU_SAVE_RINGTONE, 0,
                                    getDrmMimeMenuStringRsrc(msgItem.mMsgId))
                            .setOnMenuItemClickListener(l);
                        }
                        break;
                }
            }

            if (msgItem.mLocked && mIsSmsEnabled) {
                menu.add(0, MENU_UNLOCK_MESSAGE, 0, R.string.menu_unlock)
                    .setOnMenuItemClickListener(l);
            } else if (mIsSmsEnabled) {
                menu.add(0, MENU_LOCK_MESSAGE, 0, R.string.menu_lock)
                    .setOnMenuItemClickListener(l);
            }

            menu.add(0, MENU_VIEW_MESSAGE_DETAILS, 0, R.string.view_message_details)
                .setOnMenuItemClickListener(l);

            if (msgItem.mDeliveryStatus != MessageItem.DeliveryStatus.NONE || msgItem.mReadReport) {
                menu.add(0, MENU_DELIVERY_REPORT, 0, R.string.view_delivery_report)
                        .setOnMenuItemClickListener(l);
            }

            if (mIsSmsEnabled) {
                menu.add(0, MENU_DELETE_MESSAGE, 0, R.string.delete_message)
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
        if (msgItem.isFailedMessage() && mMsgListAdapter.getCount() <= 1) {
            // For messages with bad addresses, let the user re-edit the recipients.
            initRecipientsEditor();
        }
    }

    private void editSmsMessageItem(MessageItem msgItem) {
        // When the message being edited is the only message in the conversation, the delete
        // below does something subtle. The trigger "delete_obsolete_threads_pdu" sees that a
        // thread contains no messages and silently deletes the thread. Meanwhile, the mConversation
        // object still holds onto the old thread_id and code thinks there's a backing thread in
        // the DB when it really has been deleted. Here we try and notice that situation and
        // clear out the thread_id. Later on, when Conversation.ensureThreadId() is called, we'll
        // create a new thread if necessary.
        synchronized(mConversation) {
            if (mConversation.getMessageCount() <= 1) {
                mConversation.clearThreadId();
                MessagingNotification.setCurrentlyDisplayedThreadId(
                    MessagingNotification.THREAD_NONE);
            }
        }
        // Delete the old undelivered SMS and load its content.
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgItem.mMsgId);
        SqliteWrapper.delete(ComposeMessageActivity.this,
                mContentResolver, uri, null, null);

        mWorkingMessage.setText(msgItem.mBody);
    }

    private void editMmsMessageItem(MessageItem msgItem) {
        // Load the selected message in as the working message.
        WorkingMessage newWorkingMessage = WorkingMessage.load(this, msgItem.mMessageUri);
        if (newWorkingMessage == null) {
            return;
        }

        // Discard the current message in progress.
        mWorkingMessage.discard();

        mWorkingMessage = newWorkingMessage;
        mWorkingMessage.setConversation(mConversation);

        drawTopPanel(false);

        // WorkingMessage.load() above only loads the slideshow. Set the
        // subject here because we already know what it is and avoid doing
        // another DB lookup in load() just to get it.
        mWorkingMessage.setSubject(msgItem.mSubject, false);

        if (mWorkingMessage.hasSubject()) {
            showSubjectEditor(true);
        }
    }

    private void copyToClipboard(String str) {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(null, str));
    }

    private void forwardMessage(final MessageItem msgItem) {
        mTempThreadId = 0;
        // The user wants to forward the message. If the message is an mms message, we need to
        // persist the pdu to disk. This is done in a background task.
        // If the task takes longer than a half second, a progress dialog is displayed.
        // Once the PDU persisting is done, another runnable on the UI thread get executed to start
        // the ForwardMessageActivity.
        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                // This runnable gets run in a background thread.
                if (msgItem.mType.equals("mms")) {
                    SendReq sendReq = new SendReq();
                    String subject = getString(R.string.forward_prefix);
                    if (msgItem.mSubject != null) {
                        subject += msgItem.mSubject;
                    }
                    sendReq.setSubject(new EncodedStringValue(subject));
                    sendReq.setBody(msgItem.mSlideshow.makeCopy());

                    mTempMmsUri = null;
                    try {
                        PduPersister persister =
                                PduPersister.getPduPersister(ComposeMessageActivity.this);
                        // Copy the parts of the message here.
                        mTempMmsUri = persister.persist(sendReq, Mms.Draft.CONTENT_URI, true,
                                MessagingPreferenceActivity
                                    .getIsGroupMmsEnabled(ComposeMessageActivity.this), null);
                        mTempThreadId = MessagingNotification.getThreadId(
                                ComposeMessageActivity.this, mTempMmsUri);
                    } catch (MmsException e) {
                        Log.e(TAG, "Failed to copy message: " + msgItem.mMessageUri);
                        Toast.makeText(ComposeMessageActivity.this,
                                R.string.cannot_save_message, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
        }, new Runnable() {
            @Override
            public void run() {
                // Once the above background thread is complete, this runnable is run
                // on the UI thread.
                Intent intent = createIntent(ComposeMessageActivity.this, 0);

                intent.putExtra(KEY_EXIT_ON_SENT, true);
                intent.putExtra(KEY_FORWARDED_MESSAGE, true);
                if (mTempThreadId > 0) {
                    intent.putExtra(THREAD_ID, mTempThreadId);
                }

                if (msgItem.mType.equals("sms")) {
                    intent.putExtra("sms_body", msgItem.mBody);
                } else {
                    intent.putExtra("msg_uri", mTempMmsUri);
                    String subject = getString(R.string.forward_prefix);
                    if (msgItem.mSubject != null) {
                        subject += msgItem.mSubject;
                    }
                    intent.putExtra("subject", subject);
                }
                // ForwardMessageActivity is simply an alias in the manifest for
                // ComposeMessageActivity. We have to make an alias because ComposeMessageActivity
                // launch flags specify singleTop. When we forward a message, we want to start a
                // separate ComposeMessageActivity. The only way to do that is to override the
                // singleTop flag, which is impossible to do in code. By creating an alias to the
                // activity, without the singleTop flag, we can launch a separate
                // ComposeMessageActivity to edit the forward message.
                intent.setClassName(ComposeMessageActivity.this,
                        "com.android.mms.ui.ForwardMessageActivity");
                startActivity(intent);
            }
        }, R.string.building_slideshow_title);
    }

    /**
     * Context menu handlers for the message list view.
     */
    private final class MsgListMenuClickListener implements MenuItem.OnMenuItemClickListener {
        private MessageItem mMsgItem;

        public MsgListMenuClickListener(MessageItem msgItem) {
            mMsgItem = msgItem;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (mMsgItem == null) {
                return false;
            }

            switch (item.getItemId()) {
                case MENU_EDIT_MESSAGE:
                    editMessageItem(mMsgItem);
                    drawBottomPanel();
                    return true;

                case MENU_COPY_MESSAGE_TEXT:
                    copyToClipboard(mMsgItem.mBody);
                    return true;

                case MENU_FORWARD_MESSAGE:
                    forwardMessage(mMsgItem);
                    return true;

                case MENU_VIEW_SLIDESHOW:
                    MessageUtils.viewMmsMessageAttachment(ComposeMessageActivity.this,
                            ContentUris.withAppendedId(Mms.CONTENT_URI, mMsgItem.mMsgId), null,
                            getAsyncDialog());
                    return true;

                case MENU_VIEW_MESSAGE_DETAILS:
                    return showMessageDetails(mMsgItem);

                case MENU_DELETE_MESSAGE: {
                    DeleteMessageListener l = new DeleteMessageListener(mMsgItem);
                    confirmDeleteDialog(l, mMsgItem.mLocked);
                    return true;
                }
                case MENU_DELIVERY_REPORT:
                    showDeliveryReport(mMsgItem.mMsgId, mMsgItem.mType);
                    return true;

                case MENU_COPY_TO_SDCARD: {
                    int resId = copyMedia(mMsgItem.mMsgId) ? R.string.copy_to_sdcard_success :
                        R.string.copy_to_sdcard_fail;
                    Toast.makeText(ComposeMessageActivity.this, resId, Toast.LENGTH_SHORT).show();
                    return true;
                }

                case MENU_SAVE_RINGTONE: {
                    int resId = getDrmMimeSavedStringRsrc(mMsgItem.mMsgId,
                            saveRingtone(mMsgItem.mMsgId));
                    Toast.makeText(ComposeMessageActivity.this, resId, Toast.LENGTH_SHORT).show();
                    return true;
                }

                case MENU_LOCK_MESSAGE: {
                    lockMessage(mMsgItem, true);
                    return true;
                }

                case MENU_UNLOCK_MESSAGE: {
                    lockMessage(mMsgItem, false);
                    return true;
                }

                default:
                    return false;
            }
        }
    }

    private void lockMessage(MessageItem msgItem, boolean locked) {
        Uri uri;
        if ("sms".equals(msgItem.mType)) {
            uri = Sms.CONTENT_URI;
        } else {
            uri = Mms.CONTENT_URI;
        }
        final Uri lockUri = ContentUris.withAppendedId(uri, msgItem.mMsgId);

        final ContentValues values = new ContentValues(1);
        values.put("locked", locked ? 1 : 0);

        new Thread(new Runnable() {
            @Override
            public void run() {
                getContentResolver().update(lockUri,
                        values, null, null);
            }
        }, "ComposeMessageActivity.lockMessage").start();
    }

    /**
     * Looks to see if there are any valid parts of the attachment that can be copied to a SD card.
     * @param msgId
     */
    private boolean haveSomethingToCopyToSDCard(long msgId) {
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "haveSomethingToCopyToSDCard can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        boolean result = false;
        int partNum = body.getPartsNum();
        for(int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());

            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                log("[CMA] haveSomethingToCopyToSDCard: part[" + i + "] contentType=" + type);
            }

            if (ContentType.isImageType(type) || ContentType.isVideoType(type) ||
                    ContentType.isAudioType(type) || DrmUtils.isDrmType(type)) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Copies media from an Mms to the DrmProvider
     * @param msgId
     */
    private boolean saveRingtone(long msgId) {
        boolean result = true;
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "copyToDrmProvider can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for(int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());

            if (DrmUtils.isDrmType(type)) {
                // All parts (but there's probably only a single one) have to be successful
                // for a valid result.
                result &= copyPart(part, Long.toHexString(msgId));
            }
        }
        return result;
    }

    /**
     * Returns true if any part is drm'd audio with ringtone rights.
     * @param msgId
     * @return true if one of the parts is drm'd audio with rights to save as a ringtone.
     */
    private boolean isDrmRingtoneWithRights(long msgId) {
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "isDrmRingtoneWithRights can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for (int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());

            if (DrmUtils.isDrmType(type)) {
                String mimeType = MmsApp.getApplication().getDrmManagerClient()
                        .getOriginalMimeType(part.getDataUri());
                if (ContentType.isAudioType(mimeType) && DrmUtils.haveRightsForAction(part.getDataUri(),
                        DrmStore.Action.RINGTONE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if all drm'd parts are forwardable.
     * @param msgId
     * @return true if all drm'd parts are forwardable.
     */
    private boolean isForwardable(long msgId) {
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "getDrmMimeType can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for (int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);
            String type = new String(part.getContentType());

            if (DrmUtils.isDrmType(type) && !DrmUtils.haveRightsForAction(part.getDataUri(),
                        DrmStore.Action.TRANSFER)) {
                    return false;
            }
        }
        return true;
    }

    private int getDrmMimeMenuStringRsrc(long msgId) {
        if (isDrmRingtoneWithRights(msgId)) {
            return R.string.save_ringtone;
        }
        return 0;
    }

    private int getDrmMimeSavedStringRsrc(long msgId, boolean success) {
        if (isDrmRingtoneWithRights(msgId)) {
            return success ? R.string.saved_ringtone : R.string.saved_ringtone_fail;
        }
        return 0;
    }

    /**
     * Copies media from an Mms to the "download" directory on the SD card. If any of the parts
     * are audio types, drm'd or not, they're copied to the "Ringtones" directory.
     * @param msgId
     */
    private boolean copyMedia(long msgId) {
        boolean result = true;
        PduBody body = null;
        try {
            body = SlideshowModel.getPduBody(this,
                        ContentUris.withAppendedId(Mms.CONTENT_URI, msgId));
        } catch (MmsException e) {
            Log.e(TAG, "copyMedia can't load pdu body: " + msgId);
        }
        if (body == null) {
            return false;
        }

        int partNum = body.getPartsNum();
        for(int i = 0; i < partNum; i++) {
            PduPart part = body.getPart(i);

            // all parts have to be successful for a valid result.
            result &= copyPart(part, Long.toHexString(msgId));
        }
        return result;
    }

    private boolean copyPart(PduPart part, String fallback) {
        Uri uri = part.getDataUri();
        String type = new String(part.getContentType());
        boolean isDrm = DrmUtils.isDrmType(type);
        if (isDrm) {
            type = MmsApp.getApplication().getDrmManagerClient()
                    .getOriginalMimeType(part.getDataUri());
        }
        if (!ContentType.isImageType(type) && !ContentType.isVideoType(type) &&
                !ContentType.isAudioType(type)) {
            return true;    // we only save pictures, videos, and sounds. Skip the text parts,
                            // the app (smil) parts, and other type that we can't handle.
                            // Return true to pretend that we successfully saved the part so
                            // the whole save process will be counted a success.
        }
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

                String fileName;
                if (location == null) {
                    // Use fallback name.
                    fileName = fallback;
                } else {
                    // For locally captured videos, fileName can end up being something like this:
                    //      /mnt/sdcard/Android/data/com.android.mms/cache/.temp1.3gp
                    fileName = new String(location);
                }
                File originalFile = new File(fileName);
                fileName = originalFile.getName();  // Strip the full path of where the "part" is
                                                    // stored down to just the leaf filename.

                // Depending on the location, there may be an
                // extension already on the name or not. If we've got audio, put the attachment
                // in the Ringtones directory.
                String dir = Environment.getExternalStorageDirectory() + "/"
                                + (ContentType.isAudioType(type) ? Environment.DIRECTORY_RINGTONES :
                                    Environment.DIRECTORY_DOWNLOADS)  + "/";
                String extension;
                int index;
                if ((index = fileName.lastIndexOf('.')) == -1) {
                    extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
                } else {
                    extension = fileName.substring(index + 1, fileName.length());
                    fileName = fileName.substring(0, index);
                }
                if (isDrm) {
                    extension += DrmUtils.getConvertExtension(type);
                }
                // Remove leading periods. The gallery ignores files starting with a period.
                fileName = fileName.replaceAll("^.", "");

                File file = getUniqueDestination(dir + fileName, extension);

                // make sure the path is valid and directories created for this file.
                File parentFile = file.getParentFile();
                if (!parentFile.exists() && !parentFile.mkdirs()) {
                    Log.e(TAG, "[MMS] copyPart: mkdirs for " + parentFile.getPath() + " failed!");
                    return false;
                }

                fout = new FileOutputStream(file);

                byte[] buffer = new byte[8000];
                int size = 0;
                while ((size=fin.read(buffer)) != -1) {
                    fout.write(buffer, 0, size);
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
                if (token != mConversation.getThreadId()) {
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

    private static ContactList sEmptyContactList;

    private ContactList getRecipients() {
        // If the recipients editor is visible, the conversation has
        // not really officially 'started' yet.  Recipients will be set
        // on the conversation once it has been saved or sent.  In the
        // meantime, let anyone who needs the recipient list think it
        // is empty rather than giving them a stale one.
        if (isRecipientsEditorVisible()) {
            if (sEmptyContactList == null) {
                sEmptyContactList = new ContactList();
            }
            return sEmptyContactList;
        }
        return mConversation.getRecipients();
    }

    private void updateTitle(ContactList list) {
        String title = null;
        String subTitle = null;
        int cnt = list.size();
        switch (cnt) {
            case 0: {
                String recipient = null;
                if (mRecipientsEditor != null) {
                    recipient = mRecipientsEditor.getText().toString();
                }
                title = TextUtils.isEmpty(recipient) ? getString(R.string.new_message) : recipient;
                break;
            }
            case 1: {
                title = list.get(0).getName();      // get name returns the number if there's no
                                                    // name available.
                String number = list.get(0).getNumber();
                if (!title.equals(number)) {
                    subTitle = PhoneNumberUtils.formatNumber(number, number,
                            MmsApp.getApplication().getCurrentCountryIso());
                }
                break;
            }
            default: {
                // Handle multiple recipients
                title = list.formatNames(", ");
                subTitle = getResources().getQuantityString(R.plurals.recipient_count, cnt, cnt);
                break;
            }
        }
        mDebugRecipients = list.serialize();

        ActionBar actionBar = getActionBar();
        actionBar.setTitle(title);
        actionBar.setSubtitle(subTitle);
    }

    // Get the recipients editor ready to be displayed onscreen.
    private void initRecipientsEditor() {
        if (isRecipientsEditorVisible()) {
            return;
        }
        // Must grab the recipients before the view is made visible because getRecipients()
        // returns empty recipients when the editor is visible.
        ContactList recipients = getRecipients();

        ViewStub stub = (ViewStub)findViewById(R.id.recipients_editor_stub);
        if (stub != null) {
            View stubView = stub.inflate();
            mRecipientsEditor = (RecipientsEditor) stubView.findViewById(R.id.recipients_editor);
            mRecipientsPicker = (ImageButton) stubView.findViewById(R.id.recipients_picker);
        } else {
            mRecipientsEditor = (RecipientsEditor)findViewById(R.id.recipients_editor);
            mRecipientsEditor.setVisibility(View.VISIBLE);
            mRecipientsPicker = (ImageButton)findViewById(R.id.recipients_picker);
        }
        mRecipientsPicker.setOnClickListener(this);

        mRecipientsEditor.setAdapter(new ChipsRecipientAdapter(this));
        mRecipientsEditor.setText(null);
        mRecipientsEditor.populate(recipients);
        mRecipientsEditor.setOnCreateContextMenuListener(mRecipientsMenuCreateListener);
        mRecipientsEditor.addTextChangedListener(mRecipientsWatcher);
        // TODO : Remove the max length limitation due to the multiple phone picker is added and the
        // user is able to select a large number of recipients from the Contacts. The coming
        // potential issue is that it is hard for user to edit a recipient from hundred of
        // recipients in the editor box. We may redesign the editor box UI for this use case.
        // mRecipientsEditor.setFilters(new InputFilter[] {
        //         new InputFilter.LengthFilter(RECIPIENTS_MAX_LENGTH) });

        mRecipientsEditor.setOnSelectChipRunnable(new Runnable() {
            @Override
            public void run() {
                // After the user selects an item in the pop-up contacts list, move the
                // focus to the text editor if there is only one recipient.  This helps
                // the common case of selecting one recipient and then typing a message,
                // but avoids annoying a user who is trying to add five recipients and
                // keeps having focus stolen away.
                if (mRecipientsEditor.getRecipientCount() == 1) {
                    // if we're in extract mode then don't request focus
                    final InputMethodManager inputManager = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (inputManager == null || !inputManager.isFullscreenMode()) {
                        mTextEditor.requestFocus();
                    }
                }
            }
        });

        mRecipientsEditor.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    RecipientsEditor editor = (RecipientsEditor) v;
                    ContactList contacts = editor.constructContactsFromInput(false);
                    updateTitle(contacts);
                }
            }
        });

        PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(this, mRecipientsEditor);

        mTopPanel.setVisibility(View.VISIBLE);
    }

    //==========================================================
    // Activity methods
    //==========================================================

    public static boolean cancelFailedToDeliverNotification(Intent intent, Context context) {
        if (MessagingNotification.isFailedToDeliver(intent)) {
            // Cancel any failed message notifications
            MessagingNotification.cancelNotification(context,
                        MessagingNotification.MESSAGE_FAILED_NOTIFICATION_ID);
            return true;
        }
        return false;
    }

    public static boolean cancelFailedDownloadNotification(Intent intent, Context context) {
        if (MessagingNotification.isFailedToDownload(intent)) {
            // Cancel any failed download notifications
            MessagingNotification.cancelNotification(context,
                        MessagingNotification.DOWNLOAD_FAILED_NOTIFICATION_ID);
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mIsSmsEnabled = MmsConfig.isSmsEnabled(this);
        super.onCreate(savedInstanceState);

        resetConfiguration(getResources().getConfiguration());

        setContentView(R.layout.compose_message_activity);
        setProgressBarVisibility(false);

        // Initialize members for UI elements.
        initResourceRefs();

        mContentResolver = getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(mContentResolver);

        initialize(savedInstanceState, 0);

        if (TRACE) {
            android.os.Debug.startMethodTracing("compose");
        }
    }

    private void showSubjectEditor(boolean show) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("" + show);
        }

        if (mSubjectTextEditor == null) {
            // Don't bother to initialize the subject editor if
            // we're just going to hide it.
            if (show == false) {
                return;
            }
            mSubjectTextEditor = (EditText)findViewById(R.id.subject);
            mSubjectTextEditor.setFilters(new InputFilter[] {
                    new LengthFilter(MmsConfig.getMaxSubjectLength())});
        }

        mSubjectTextEditor.setOnKeyListener(show ? mSubjectKeyListener : null);

        if (show) {
            mSubjectTextEditor.addTextChangedListener(mSubjectEditorWatcher);
        } else {
            mSubjectTextEditor.removeTextChangedListener(mSubjectEditorWatcher);
        }

        mSubjectTextEditor.setText(mWorkingMessage.getSubject());
        mSubjectTextEditor.setVisibility(show ? View.VISIBLE : View.GONE);
        hideOrShowTopPanel();
    }

    private void hideOrShowTopPanel() {
        boolean anySubViewsVisible = (isSubjectEditorVisible() || isRecipientsEditorVisible());
        mTopPanel.setVisibility(anySubViewsVisible ? View.VISIBLE : View.GONE);
    }

    public void initialize(Bundle savedInstanceState, long originalThreadId) {
        // Create a new empty working message.
        mWorkingMessage = WorkingMessage.createEmpty(this);

        // Read parameters or previously saved state of this activity. This will load a new
        // mConversation
        initActivityState(savedInstanceState);

        if (LogTag.SEVERE_WARNING && originalThreadId != 0 &&
                originalThreadId == mConversation.getThreadId()) {
            LogTag.warnPossibleRecipientMismatch("ComposeMessageActivity.initialize: " +
                    " threadId didn't change from: " + originalThreadId, this);
        }

        log("savedInstanceState = " + savedInstanceState +
            " intent = " + getIntent() +
            " mConversation = " + mConversation);

        if (cancelFailedToDeliverNotification(getIntent(), this)) {
            // Show a pop-up dialog to inform user the message was
            // failed to deliver.
            undeliveredMessageDialog(getMessageDate(null));
        }
        cancelFailedDownloadNotification(getIntent(), this);

        // Set up the message history ListAdapter
        initMessageList();

        mShouldLoadDraft = true;

        // Load the draft for this thread, if we aren't already handling
        // existing data, such as a shared picture or forwarded message.
        boolean isForwardedMessage = false;
        // We don't attempt to handle the Intent.ACTION_SEND when saveInstanceState is non-null.
        // saveInstanceState is non-null when this activity is killed. In that case, we already
        // handled the attachment or the send, so we don't try and parse the intent again.
        if (savedInstanceState == null && (handleSendIntent() || handleForwardedMessage())) {
            mShouldLoadDraft = false;
        }

        // Let the working message know what conversation it belongs to
        mWorkingMessage.setConversation(mConversation);

        // Show the recipients editor if we don't have a valid thread. Hide it otherwise.
        if (mConversation.getThreadId() <= 0) {
            // Hide the recipients editor so the call to initRecipientsEditor won't get
            // short-circuited.
            hideRecipientEditor();
            initRecipientsEditor();
        } else {
            hideRecipientEditor();
        }

        updateSendButtonState();

        drawTopPanel(false);
        if (!mShouldLoadDraft) {
            // We're not loading a draft, so we can draw the bottom panel immediately.
            drawBottomPanel();
        }

        onKeyboardStateChanged();

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("update title, mConversation=" + mConversation.toString());
        }

        updateTitle(mConversation.getRecipients());

        if (isForwardedMessage && isRecipientsEditorVisible()) {
            // The user is forwarding the message to someone. Put the focus on the
            // recipient editor rather than in the message editor.
            mRecipientsEditor.requestFocus();
        }

        mMsgListAdapter.setIsGroupConversation(mConversation.getRecipients().size() > 1);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        Conversation conversation = null;
        mSentMessage = false;

        // If we have been passed a thread_id, use that to find our
        // conversation.

        // Note that originalThreadId might be zero but if this is a draft and we save the
        // draft, ensureThreadId gets called async from WorkingMessage.asyncUpdateDraftSmsMessage
        // the thread will get a threadId behind the UI thread's back.
        long originalThreadId = mConversation.getThreadId();
        long threadId = intent.getLongExtra(THREAD_ID, 0);
        Uri intentUri = intent.getData();

        boolean sameThread = false;
        if (threadId > 0) {
            conversation = Conversation.get(this, threadId, false);
        } else {
            if (mConversation.getThreadId() == 0) {
                // We've got a draft. Make sure the working recipients are synched
                // to the conversation so when we compare conversations later in this function,
                // the compare will work.
                mWorkingMessage.syncWorkingRecipients();
            }
            // Get the "real" conversation based on the intentUri. The intentUri might specify
            // the conversation by a phone number or by a thread id. We'll typically get a threadId
            // based uri when the user pulls down a notification while in ComposeMessageActivity and
            // we end up here in onNewIntent. mConversation can have a threadId of zero when we're
            // working on a draft. When a new message comes in for that same recipient, a
            // conversation will get created behind CMA's back when the message is inserted into
            // the database and the corresponding entry made in the threads table. The code should
            // use the real conversation as soon as it can rather than finding out the threadId
            // when sending with "ensureThreadId".
            conversation = Conversation.get(this, intentUri, false);
        }

        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("onNewIntent: data=" + intentUri + ", thread_id extra is " + threadId +
                    ", new conversation=" + conversation + ", mConversation=" + mConversation);
        }

        // this is probably paranoid to compare both thread_ids and recipient lists,
        // but we want to make double sure because this is a last minute fix for Froyo
        // and the previous code checked thread ids only.
        // (we cannot just compare thread ids because there is a case where mConversation
        // has a stale/obsolete thread id (=1) that could collide against the new thread_id(=1),
        // even though the recipient lists are different)
        sameThread = ((conversation.getThreadId() == mConversation.getThreadId() ||
                mConversation.getThreadId() == 0) &&
                conversation.equals(mConversation));

        if (sameThread) {
            log("onNewIntent: same conversation");
            if (mConversation.getThreadId() == 0) {
                mConversation = conversation;
                mWorkingMessage.setConversation(mConversation);
                updateThreadIdIfRunning();
                invalidateOptionsMenu();
            }
        } else {
            if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                log("onNewIntent: different conversation");
            }
            saveDraft(false);    // if we've got a draft, save it first

            initialize(null, originalThreadId);
        }
        loadMessagesAndDraft(0);
    }

    private void sanityCheckConversation() {
        if (mWorkingMessage.getConversation() != mConversation) {
            LogTag.warnPossibleRecipientMismatch(
                    "ComposeMessageActivity: mWorkingMessage.mConversation=" +
                    mWorkingMessage.getConversation() + ", mConversation=" +
                    mConversation + ", MISMATCH!", this);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        // hide the compose panel to reduce jank when re-entering this activity.
        // if we don't hide it here, the compose panel will flash before the keyboard shows
        // (when keyboard is suppose to be shown).
        hideBottomPanel();

        if (mWorkingMessage.isDiscarded()) {
            // If the message isn't worth saving, don't resurrect it. Doing so can lead to
            // a situation where a new incoming message gets the old thread id of the discarded
            // draft. This activity can end up displaying the recipients of the old message with
            // the contents of the new message. Recognize that dangerous situation and bail out
            // to the ConversationList where the user can enter this in a clean manner.
            if (mWorkingMessage.isWorthSaving()) {
                if (LogTag.VERBOSE) {
                    log("onRestart: mWorkingMessage.unDiscard()");
                }
                mWorkingMessage.unDiscard();    // it was discarded in onStop().

                sanityCheckConversation();
            } else if (isRecipientsEditorVisible() && recipientCount() > 0) {
                if (LogTag.VERBOSE) {
                    log("onRestart: goToConversationList");
                }
                goToConversationList();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean isSmsEnabled = MmsConfig.isSmsEnabled(this);
        if (isSmsEnabled != mIsSmsEnabled) {
            mIsSmsEnabled = isSmsEnabled;
            invalidateOptionsMenu();
        }

        initFocus();

        // Register a BroadcastReceiver to listen on HTTP I/O process.
        registerReceiver(mHttpProgressReceiver, mHttpProgressFilter);

        // figure out whether we need to show the keyboard or not.
        // if there is draft to be loaded for 'mConversation', we'll show the keyboard;
        // otherwise we hide the keyboard. In any event, delay loading
        // message history and draft (controlled by DEFER_LOADING_MESSAGES_AND_DRAFT).
        int mode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        if (DraftCache.getInstance().hasDraft(mConversation.getThreadId())) {
            mode |= WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        } else if (mConversation.getThreadId() <= 0) {
            // For composing a new message, bring up the softkeyboard so the user can
            // immediately enter recipients. This call won't do anything on devices with
            // a hard keyboard.
            mode |= WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        } else {
            mode |= WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        }

        getWindow().setSoftInputMode(mode);

        // reset mMessagesAndDraftLoaded
        mMessagesAndDraftLoaded = false;

        if (!DEFER_LOADING_MESSAGES_AND_DRAFT) {
            loadMessagesAndDraft(1);
        } else {
            // HACK: force load messages+draft after max delay, if it's not already loaded.
            // this is to work around when coming out of sleep mode. WindowManager behaves
            // strangely and hides the keyboard when it should be shown, or sometimes initially
            // shows it when we want to hide it. In that case, we never get the onSizeChanged()
            // callback w/ keyboard shown, so we wouldn't know to load the messages+draft.
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    loadMessagesAndDraft(2);
                }
            }, LOADING_MESSAGES_AND_DRAFT_MAX_DELAY_MS);
        }

        // Update the fasttrack info in case any of the recipients' contact info changed
        // while we were paused. This can happen, for example, if a user changes or adds
        // an avatar associated with a contact.
        mWorkingMessage.syncWorkingRecipients();

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("update title, mConversation=" + mConversation.toString());
        }

        updateTitle(mConversation.getRecipients());

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    public void loadMessageContent() {
        // Don't let any markAsRead DB updates occur before we've loaded the messages for
        // the thread. Unblocking occurs when we're done querying for the conversation
        // items.
        mConversation.blockMarkAsRead(true);
        mConversation.markAsRead();         // dismiss any notifications for this convo
        startMsgListQuery();
        updateSendFailedNotification();
    }

    /**
     * Load message history and draft. This method should be called from main thread.
     * @param debugFlag shows where this is being called from
     */
    private void loadMessagesAndDraft(int debugFlag) {
        if (!mSendDiscreetMode && !mMessagesAndDraftLoaded) {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.v(TAG, "### CMA.loadMessagesAndDraft: flag=" + debugFlag);
            }
            loadMessageContent();
            boolean drawBottomPanel = true;
            if (mShouldLoadDraft) {
                if (loadDraft()) {
                    drawBottomPanel = false;
                }
            }
            if (drawBottomPanel) {
                drawBottomPanel();
            }
            mMessagesAndDraftLoaded = true;
        }
    }

    private void updateSendFailedNotification() {
        final long threadId = mConversation.getThreadId();
        if (threadId <= 0)
            return;

        // updateSendFailedNotificationForThread makes a database call, so do the work off
        // of the ui thread.
        new Thread(new Runnable() {
            @Override
            public void run() {
                MessagingNotification.updateSendFailedNotificationForThread(
                        ComposeMessageActivity.this, threadId);
            }
        }, "ComposeMessageActivity.updateSendFailedNotification").start();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(RECIPIENTS, getRecipients().serialize());

        mWorkingMessage.writeStateToBundle(outState);

        if (mSendDiscreetMode) {
            outState.putBoolean(KEY_EXIT_ON_SENT, mSendDiscreetMode);
        }
        if (mForwardMessageMode) {
            outState.putBoolean(KEY_FORWARDED_MESSAGE, mForwardMessageMode);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // OLD: get notified of presence updates to update the titlebar.
        // NEW: we are using ContactHeaderWidget which displays presence, but updating presence
        //      there is out of our control.
        //Contact.startPresenceObserver();

        addRecipientsListeners();

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("update title, mConversation=" + mConversation.toString());
        }

        // There seems to be a bug in the framework such that setting the title
        // here gets overwritten to the original title.  Do this delayed as a
        // workaround.
        mMessageListItemHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ContactList recipients = isRecipientsEditorVisible() ?
                        mRecipientsEditor.constructContactsFromInput(false) : getRecipients();
                updateTitle(recipients);
            }
        }, 100);

        mIsRunning = true;
        updateThreadIdIfRunning();
        mConversation.markAsRead();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (DEBUG) {
            Log.v(TAG, "onPause: setCurrentlyDisplayedThreadId: " +
                        MessagingNotification.THREAD_NONE);
        }
        MessagingNotification.setCurrentlyDisplayedThreadId(MessagingNotification.THREAD_NONE);

        // OLD: stop getting notified of presence updates to update the titlebar.
        // NEW: we are using ContactHeaderWidget which displays presence, but updating presence
        //      there is out of our control.
        //Contact.stopPresenceObserver();

        removeRecipientsListeners();

        // remove any callback to display a progress spinner
        if (mAsyncDialog != null) {
            mAsyncDialog.clearPendingProgressDialog();
        }

        // Remember whether the list is scrolled to the end when we're paused so we can rescroll
        // to the end when resumed.
        if (mMsgListAdapter != null &&
                mMsgListView.getLastVisiblePosition() >= mMsgListAdapter.getCount() - 1) {
            mSavedScrollPosition = Integer.MAX_VALUE;
        } else {
            mSavedScrollPosition = mMsgListView.getFirstVisiblePosition();
        }
        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "onPause: mSavedScrollPosition=" + mSavedScrollPosition);
        }

        mConversation.markAsRead();
        mIsRunning = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        // No need to do the querying when finished this activity
        mBackgroundQueryHandler.cancelOperation(MESSAGE_LIST_QUERY_TOKEN);

        // Allow any blocked calls to update the thread's read status.
        mConversation.blockMarkAsRead(false);

        if (mMsgListAdapter != null) {
            // Close the cursor in the ListAdapter if the activity stopped.
            Cursor cursor = mMsgListAdapter.getCursor();

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            mMsgListAdapter.changeCursor(null);
            mMsgListAdapter.cancelBackgroundLoading();
        }

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("save draft");
        }
        saveDraft(true);

        // set 'mShouldLoadDraft' to true, so when coming back to ComposeMessageActivity, we would
        // load the draft, unless we are coming back to the activity after attaching a photo, etc,
        // in which case we should set 'mShouldLoadDraft' to false.
        mShouldLoadDraft = true;

        // Cleanup the BroadcastReceiver.
        unregisterReceiver(mHttpProgressReceiver);
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

        if (resetConfiguration(newConfig)) {
            // Have to re-layout the attachment editor because we have different layouts
            // depending on whether we're portrait or landscape.
            drawTopPanel(isSubjectEditorVisible());
        }
        if (LOCAL_LOGV) {
            Log.v(TAG, "CMA.onConfigurationChanged: " + newConfig +
                    ", mIsKeyboardOpen=" + mIsKeyboardOpen);
        }
        onKeyboardStateChanged();
    }

    // returns true if landscape/portrait configuration has changed
    private boolean resetConfiguration(Configuration config) {
        mIsKeyboardOpen = config.keyboardHidden == KEYBOARDHIDDEN_NO;
        boolean isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (mIsLandscape != isLandscape) {
            mIsLandscape = isLandscape;
            return true;
        }
        return false;
    }

    private void onKeyboardStateChanged() {
        // If the keyboard is hidden, don't show focus highlights for
        // things that cannot receive input.
        mTextEditor.setEnabled(mIsSmsEnabled);
        if (!mIsSmsEnabled) {
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setFocusableInTouchMode(false);
            }
            if (mSubjectTextEditor != null) {
                mSubjectTextEditor.setFocusableInTouchMode(false);
            }
            mTextEditor.setFocusableInTouchMode(false);
            mTextEditor.setHint(R.string.sending_disabled_not_default_app);
        } else if (mIsKeyboardOpen) {
            if (mRecipientsEditor != null) {
                mRecipientsEditor.setFocusableInTouchMode(true);
            }
            if (mSubjectTextEditor != null) {
                mSubjectTextEditor.setFocusableInTouchMode(true);
            }
            mTextEditor.setFocusableInTouchMode(true);
            mTextEditor.setHint(R.string.type_to_compose_text_enter_to_send);
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
                        String type = cursor.getString(COLUMN_MSG_TYPE);
                        long msgId = cursor.getLong(COLUMN_ID);
                        MessageItem msgItem = mMsgListAdapter.getCachedMessageItem(type, msgId,
                                cursor);
                        if (msgItem != null) {
                            DeleteMessageListener l = new DeleteMessageListener(msgItem);
                            confirmDeleteDialog(l, msgItem.mLocked);
                        }
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
                    @Override
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
        if (!mWorkingMessage.isWorthSaving()) {
            exit.run();
            return;
        }

        if (isRecipientsEditorVisible() &&
                !mRecipientsEditor.hasValidRecipient(mWorkingMessage.requiresMms())) {
            MessageUtils.showDiscardDraftConfirmDialog(this, new DiscardDraftListener());
            return;
        }

        mToastForDraftSave = true;
        exit.run();
    }

    private void goToConversationList() {
        finish();
        startActivity(new Intent(this, ConversationList.class));
    }

    private void hideRecipientEditor() {
        if (mRecipientsEditor != null) {
            mRecipientsEditor.removeTextChangedListener(mRecipientsWatcher);
            mRecipientsEditor.setVisibility(View.GONE);
            hideOrShowTopPanel();
        }
    }

    private boolean isRecipientsEditorVisible() {
        return (null != mRecipientsEditor)
                    && (View.VISIBLE == mRecipientsEditor.getVisibility());
    }

    private boolean isSubjectEditorVisible() {
        return (null != mSubjectTextEditor)
                    && (View.VISIBLE == mSubjectTextEditor.getVisibility());
    }

    @Override
    public void onAttachmentChanged() {
        // Have to make sure we're on the UI thread. This function can be called off of the UI
        // thread when we're adding multi-attachments
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                drawBottomPanel();
                updateSendButtonState();
                drawTopPanel(isSubjectEditorVisible());
            }
        });
    }

    @Override
    public void onProtocolChanged(final boolean convertToMms) {
        // Have to make sure we're on the UI thread. This function can be called off of the UI
        // thread when we're adding multi-attachments
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showSmsOrMmsSendButton(convertToMms);

                if (convertToMms) {
                    // In the case we went from a long sms with a counter to an mms because
                    // the user added an attachment or a subject, hide the counter --
                    // it doesn't apply to mms.
                    mTextCounter.setVisibility(View.GONE);

                    showConvertToMmsToast();
                }
            }
        });
    }

    // Show or hide the Sms or Mms button as appropriate. Return the view so that the caller
    // can adjust the enableness and focusability.
    private View showSmsOrMmsSendButton(boolean isMms) {
        View showButton;
        View hideButton;
        if (isMms) {
            showButton = mSendButtonMms;
            hideButton = mSendButtonSms;
        } else {
            showButton = mSendButtonSms;
            hideButton = mSendButtonMms;
        }
        showButton.setVisibility(View.VISIBLE);
        hideButton.setVisibility(View.GONE);

        return showButton;
    }

    Runnable mResetMessageRunnable = new Runnable() {
        @Override
        public void run() {
            resetMessage();
        }
    };

    @Override
    public void onPreMessageSent() {
        runOnUiThread(mResetMessageRunnable);
    }

    @Override
    public void onMessageSent() {
        // This callback can come in on any thread; put it on the main thread to avoid
        // concurrency problems
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // If we already have messages in the list adapter, it
                // will be auto-requerying; don't thrash another query in.
                // TODO: relying on auto-requerying seems unreliable when priming an MMS into the
                // outbox. Need to investigate.
//                if (mMsgListAdapter.getCount() == 0) {
                    if (LogTag.VERBOSE) {
                        log("onMessageSent");
                    }
                    startMsgListQuery();
//                }

                // The thread ID could have changed if this is a new message that we just inserted
                // into the database (and looked up or created a thread for it)
                updateThreadIdIfRunning();
            }
        });
    }

    @Override
    public void onMaxPendingMessagesReached() {
        saveDraft(false);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ComposeMessageActivity.this, R.string.too_many_unsent_mms,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onAttachmentError(final int error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handleAddAttachmentError(error, R.string.type_picture);
                onMessageSent();        // now requery the list of messages
            }
        });
    }

    // We don't want to show the "call" option unless there is only one
    // recipient and it's a phone number.
    private boolean isRecipientCallable() {
        ContactList recipients = getRecipients();
        return (recipients.size() == 1 && !recipients.containsEmail());
    }

    private void dialRecipient() {
        if (isRecipientCallable()) {
            String number = getRecipients().get(0).getNumber();
            Intent dialIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
            startActivity(dialIntent);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu) ;

        menu.clear();

        if (mSendDiscreetMode && !mForwardMessageMode) {
            // When we're in send-a-single-message mode from the lock screen, don't show
            // any menus.
            return true;
        }

        // Don't show the call icon if the device don't support voice calling.
        boolean voiceCapable =
                getResources().getBoolean(com.android.internal.R.bool.config_voice_capable);
        if (isRecipientCallable() && voiceCapable) {
            MenuItem item = menu.add(0, MENU_CALL_RECIPIENT, 0, R.string.menu_call)
                .setIcon(R.drawable.ic_menu_call)
                .setTitle(R.string.menu_call);
            if (!isRecipientsEditorVisible()) {
                // If we're not composing a new message, show the call icon in the actionbar
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        }

        if (MmsConfig.getMmsEnabled() && mIsSmsEnabled) {
            if (!isSubjectEditorVisible()) {
                menu.add(0, MENU_ADD_SUBJECT, 0, R.string.add_subject).setIcon(
                        R.drawable.ic_menu_edit);
            }
            if (!mWorkingMessage.hasAttachment()) {
                menu.add(0, MENU_ADD_ATTACHMENT, 0, R.string.add_attachment)
                        .setIcon(R.drawable.ic_menu_attachment)
                    .setTitle(R.string.add_attachment)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);    // add to actionbar
            }
        }

        if (isPreparedForSending() && mIsSmsEnabled) {
            menu.add(0, MENU_SEND, 0, R.string.send).setIcon(android.R.drawable.ic_menu_send);
        }

        if (getRecipients().size() > 1) {
            menu.add(0, MENU_GROUP_PARTICIPANTS, 0, R.string.menu_group_participants);
        }

        if (mMsgListAdapter.getCount() > 0 && mIsSmsEnabled) {
            // Removed search as part of b/1205708
            //menu.add(0, MENU_SEARCH, 0, R.string.menu_search).setIcon(
            //        R.drawable.ic_menu_search);
            Cursor cursor = mMsgListAdapter.getCursor();
            if ((null != cursor) && (cursor.getCount() > 0)) {
                menu.add(0, MENU_DELETE_THREAD, 0, R.string.delete_thread).setIcon(
                    android.R.drawable.ic_menu_delete);
            }
        } else if (mIsSmsEnabled) {
            menu.add(0, MENU_DISCARD, 0, R.string.discard).setIcon(android.R.drawable.ic_menu_delete);
        }

        buildAddAddressToContactMenuItem(menu);

        menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences).setIcon(
                android.R.drawable.ic_menu_preferences);

        if (LogTag.DEBUG_DUMP) {
            menu.add(0, MENU_DEBUG_DUMP, 0, R.string.menu_debug_dump);
        }

        return true;
    }

    private void buildAddAddressToContactMenuItem(Menu menu) {
        // bug #7087793: for group of recipients, remove "Add to People" action. Rely on
        // individually creating contacts for unknown phone numbers by touching the individual
        // sender's avatars, one at a time
        ContactList contacts = getRecipients();
        if (contacts.size() != 1) {
            return;
        }

        // if we don't have a contact for the recipient, create a menu item to add the number
        // to contacts.
        Contact c = contacts.get(0);
        if (!c.existsInDatabase() && canAddToContacts(c)) {
            Intent intent = ConversationList.createAddContactIntent(c.getNumber());
            menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, R.string.menu_add_to_contacts)
                .setIcon(android.R.drawable.ic_menu_add)
                .setIntent(intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD_SUBJECT:
                showSubjectEditor(true);
                mWorkingMessage.setSubject("", true);
                updateSendButtonState();
                mSubjectTextEditor.requestFocus();
                break;
            case MENU_ADD_ATTACHMENT:
                // Launch the add-attachment list dialog
                showAddAttachmentDialog(false);
                break;
            case MENU_DISCARD:
                mWorkingMessage.discard();
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
                confirmDeleteThread(mConversation.getThreadId());
                break;

            case android.R.id.home:
            case MENU_CONVERSATION_LIST:
                exitComposeMessageActivity(new Runnable() {
                    @Override
                    public void run() {
                        goToConversationList();
                    }
                });
                break;
            case MENU_CALL_RECIPIENT:
                dialRecipient();
                break;
            case MENU_GROUP_PARTICIPANTS:
            {
                Intent intent = new Intent(this, RecipientListActivity.class);
                intent.putExtra(THREAD_ID, mConversation.getThreadId());
                startActivity(intent);
                break;
            }
            case MENU_VIEW_CONTACT: {
                // View the contact for the first (and only) recipient.
                ContactList list = getRecipients();
                if (list.size() == 1 && list.get(0).existsInDatabase()) {
                    Uri contactUri = list.get(0).getUri();
                    Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    startActivity(intent);
                }
                break;
            }
            case MENU_ADD_ADDRESS_TO_CONTACTS:
                mAddContactIntent = item.getIntent();
                startActivityForResult(mAddContactIntent, REQUEST_CODE_ADD_CONTACT);
                break;
            case MENU_PREFERENCES: {
                Intent intent = new Intent(this, MessagingPreferenceActivity.class);
                startActivityIfNeeded(intent, -1);
                break;
            }
            case MENU_DEBUG_DUMP:
                mWorkingMessage.dump();
                Conversation.dump();
                LogTag.dumpInternalTables(this);
                break;
        }

        return true;
    }

    private void confirmDeleteThread(long threadId) {
        Conversation.startQueryHaveLockedMessages(mBackgroundQueryHandler,
                threadId, ConversationList.HAVE_LOCKED_MESSAGES_TOKEN);
    }

//    static class SystemProperties { // TODO, temp class to get unbundling working
//        static int getInt(String s, int value) {
//            return value;       // just return the default value or now
//        }
//    }

    private void addAttachment(int type, boolean replace) {
        // Calculate the size of the current slide if we're doing a replace so the
        // slide size can optionally be used in computing how much room is left for an attachment.
        int currentSlideSize = 0;
        SlideshowModel slideShow = mWorkingMessage.getSlideshow();
        if (replace && slideShow != null) {
            WorkingMessage.removeThumbnailsFromCache(slideShow);
            SlideModel slide = slideShow.get(0);
            currentSlideSize = slide.getSlideSize();
        }
        switch (type) {
            case AttachmentTypeSelectorAdapter.ADD_IMAGE:
                MessageUtils.selectImage(this, REQUEST_CODE_ATTACH_IMAGE);
                break;

            case AttachmentTypeSelectorAdapter.TAKE_PICTURE: {
                MessageUtils.capturePicture(this, REQUEST_CODE_TAKE_PICTURE);
                break;
            }

            case AttachmentTypeSelectorAdapter.ADD_VIDEO:
                MessageUtils.selectVideo(this, REQUEST_CODE_ATTACH_VIDEO);
                break;

            case AttachmentTypeSelectorAdapter.RECORD_VIDEO: {
                long sizeLimit = computeAttachmentSizeLimit(slideShow, currentSlideSize);
                if (sizeLimit > 0) {
                    MessageUtils.recordVideo(this, REQUEST_CODE_TAKE_VIDEO, sizeLimit);
                } else {
                    Toast.makeText(this,
                            getString(R.string.message_too_big_for_video),
                            Toast.LENGTH_SHORT).show();
                }
            }
            break;

            case AttachmentTypeSelectorAdapter.ADD_SOUND:
                MessageUtils.selectAudio(this, REQUEST_CODE_ATTACH_SOUND);
                break;

            case AttachmentTypeSelectorAdapter.RECORD_SOUND:
                long sizeLimit = computeAttachmentSizeLimit(slideShow, currentSlideSize);
                MessageUtils.recordSound(this, REQUEST_CODE_RECORD_SOUND, sizeLimit);
                break;

            case AttachmentTypeSelectorAdapter.ADD_SLIDESHOW:
                editSlideshow();
                break;

            default:
                break;
        }
    }

    public static long computeAttachmentSizeLimit(SlideshowModel slideShow, int currentSlideSize) {
        // Computer attachment size limit. Subtract 1K for some text.
        long sizeLimit = MmsConfig.getMaxMessageSize() - SlideshowModel.SLIDESHOW_SLOP;
        if (slideShow != null) {
            sizeLimit -= slideShow.getCurrentMessageSize();

            // We're about to ask the camera to capture some video (or the sound recorder
            // to record some audio) which will eventually replace the content on the current
            // slide. Since the current slide already has some content (which was subtracted
            // out just above) and that content is going to get replaced, we can add the size of the
            // current slide into the available space used to capture a video (or audio).
            sizeLimit += currentSlideSize;
        }
        return sizeLimit;
    }

    private void showAddAttachmentDialog(final boolean replace) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_dialog_attach);
        builder.setTitle(R.string.add_attachment);

        if (mAttachmentTypeSelectorAdapter == null) {
            mAttachmentTypeSelectorAdapter = new AttachmentTypeSelectorAdapter(
                    this, AttachmentTypeSelectorAdapter.MODE_WITH_SLIDESHOW);
        }
        builder.setAdapter(mAttachmentTypeSelectorAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                addAttachment(mAttachmentTypeSelectorAdapter.buttonToCommand(which), replace);
                dialog.dismiss();
            }
        });

        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (LogTag.VERBOSE) {
            log("onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode +
                    ", data=" + data);
        }
        mWaitingForSubActivity = false;          // We're back!
        mShouldLoadDraft = false;
        if (mWorkingMessage.isFakeMmsForDraft()) {
            // We no longer have to fake the fact we're an Mms. At this point we are or we aren't,
            // based on attachments and other Mms attrs.
            mWorkingMessage.removeFakeMmsForDraft();
        }

        if (requestCode == REQUEST_CODE_PICK) {
            mWorkingMessage.asyncDeleteDraftSmsMessage(mConversation);
        }

        if (requestCode == REQUEST_CODE_ADD_CONTACT) {
            // The user might have added a new contact. When we tell contacts to add a contact
            // and tap "Done", we're not returned to Messaging. If we back out to return to
            // messaging after adding a contact, the resultCode is RESULT_CANCELED. Therefore,
            // assume a contact was added and get the contact and force our cached contact to
            // get reloaded with the new info (such as contact name). After the
            // contact is reloaded, the function onUpdate() in this file will get called
            // and it will update the title bar, etc.
            if (mAddContactIntent != null) {
                String address =
                    mAddContactIntent.getStringExtra(ContactsContract.Intents.Insert.EMAIL);
                if (address == null) {
                    address =
                        mAddContactIntent.getStringExtra(ContactsContract.Intents.Insert.PHONE);
                }
                if (address != null) {
                    Contact contact = Contact.get(address, false);
                    if (contact != null) {
                        contact.reload();
                    }
                }
            }
        }

        if (resultCode != RESULT_OK){
            if (LogTag.VERBOSE) log("bail due to resultCode=" + resultCode);
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_CREATE_SLIDESHOW:
                if (data != null) {
                    WorkingMessage newMessage = WorkingMessage.load(this, data.getData());
                    if (newMessage != null) {
                        mWorkingMessage = newMessage;
                        mWorkingMessage.setConversation(mConversation);
                        updateThreadIdIfRunning();
                        drawTopPanel(false);
                        updateSendButtonState();
                    }
                }
                break;

            case REQUEST_CODE_TAKE_PICTURE: {
                // create a file based uri and pass to addImage(). We want to read the JPEG
                // data directly from file (using UriImage) instead of decoding it into a Bitmap,
                // which takes up too much memory and could easily lead to OOM.
                File file = new File(TempFileProvider.getScrapPath(this));
                Uri uri = Uri.fromFile(file);

                // Remove the old captured picture's thumbnail from the cache
                MmsApp.getApplication().getThumbnailManager().removeThumbnail(uri);

                addImageAsync(uri, false);
                break;
            }

            case REQUEST_CODE_ATTACH_IMAGE: {
                if (data != null) {
                    addImageAsync(data.getData(), false);
                }
                break;
            }

            case REQUEST_CODE_TAKE_VIDEO:
                Uri videoUri = TempFileProvider.renameScrapFile(".3gp", null, this);
                // Remove the old captured video's thumbnail from the cache
                MmsApp.getApplication().getThumbnailManager().removeThumbnail(videoUri);

                addVideoAsync(videoUri, false);      // can handle null videoUri
                break;

            case REQUEST_CODE_ATTACH_VIDEO:
                if (data != null) {
                    addVideoAsync(data.getData(), false);
                }
                break;

            case REQUEST_CODE_ATTACH_SOUND: {
                Uri uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (Settings.System.DEFAULT_RINGTONE_URI.equals(uri)) {
                    break;
                }
                addAudio(uri);
                break;
            }

            case REQUEST_CODE_RECORD_SOUND:
                if (data != null) {
                    addAudio(data.getData());
                }
                break;

            case REQUEST_CODE_ECM_EXIT_DIALOG:
                boolean outOfEmergencyMode = data.getBooleanExtra(EXIT_ECM_RESULT, false);
                if (outOfEmergencyMode) {
                    sendMessage(false);
                }
                break;

            case REQUEST_CODE_PICK:
                if (data != null) {
                    processPickResult(data);
                }
                break;

            default:
                if (LogTag.VERBOSE) log("bail due to unknown requestCode=" + requestCode);
                break;
        }
    }

    private void processPickResult(final Intent data) {
        // The EXTRA_PHONE_URIS stores the phone's urls that were selected by user in the
        // multiple phone picker.
        final Parcelable[] uris =
            data.getParcelableArrayExtra(Intents.EXTRA_PHONE_URIS);

        final int recipientCount = uris != null ? uris.length : 0;

        final int recipientLimit = MmsConfig.getRecipientLimit();
        if (recipientLimit != Integer.MAX_VALUE && recipientCount > recipientLimit) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.too_many_recipients, recipientCount, recipientLimit))
                    .setPositiveButton(android.R.string.ok, null)
                    .create().show();
            return;
        }

        final Handler handler = new Handler();
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(getText(R.string.pick_too_many_recipients));
        progressDialog.setMessage(getText(R.string.adding_recipients));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);

        final Runnable showProgress = new Runnable() {
            @Override
            public void run() {
                progressDialog.show();
            }
        };
        // Only show the progress dialog if we can not finish off parsing the return data in 1s,
        // otherwise the dialog could flicker.
        handler.postDelayed(showProgress, 1000);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ContactList list;
                 try {
                    list = ContactList.blockingGetByUris(uris);
                } finally {
                    handler.removeCallbacks(showProgress);
                    progressDialog.dismiss();
                }
                // TODO: there is already code to update the contact header widget and recipients
                // editor if the contacts change. we can re-use that code.
                final Runnable populateWorker = new Runnable() {
                    @Override
                    public void run() {
                        mRecipientsEditor.populate(list);
                        updateTitle(list);
                    }
                };
                handler.post(populateWorker);
            }
        }, "ComoseMessageActivity.processPickResult").start();
    }

    private final ResizeImageResultCallback mResizeImageCallback = new ResizeImageResultCallback() {
        // TODO: make this produce a Uri, that's what we want anyway
        @Override
        public void onResizeResult(PduPart part, boolean append) {
            if (part == null) {
                handleAddAttachmentError(WorkingMessage.UNKNOWN_ERROR, R.string.type_picture);
                return;
            }

            Context context = ComposeMessageActivity.this;
            PduPersister persister = PduPersister.getPduPersister(context);
            int result;

            Uri messageUri = mWorkingMessage.saveAsMms(true);
            if (messageUri == null) {
                result = WorkingMessage.UNKNOWN_ERROR;
            } else {
                try {
                    Uri dataUri = persister.persistPart(part,
                            ContentUris.parseId(messageUri), null);
                    result = mWorkingMessage.setAttachment(WorkingMessage.IMAGE, dataUri, append);
                    if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                        log("ResizeImageResultCallback: dataUri=" + dataUri);
                    }
                } catch (MmsException e) {
                    result = WorkingMessage.UNKNOWN_ERROR;
                }
            }

            handleAddAttachmentError(result, R.string.type_picture);
        }
    };

    private void handleAddAttachmentError(final int error, final int mediaTypeStringId) {
        if (error == WorkingMessage.OK) {
            return;
        }
        Log.d(TAG, "handleAddAttachmentError: " + error);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Resources res = getResources();
                String mediaType = res.getString(mediaTypeStringId);
                String title, message;

                switch(error) {
                case WorkingMessage.UNKNOWN_ERROR:
                    message = res.getString(R.string.failed_to_add_media, mediaType);
                    Toast.makeText(ComposeMessageActivity.this, message, Toast.LENGTH_SHORT).show();
                    return;
                case WorkingMessage.UNSUPPORTED_TYPE:
                    title = res.getString(R.string.unsupported_media_format, mediaType);
                    message = res.getString(R.string.select_different_media, mediaType);
                    break;
                case WorkingMessage.MESSAGE_SIZE_EXCEEDED:
                    title = res.getString(R.string.exceed_message_size_limitation, mediaType);
                    message = res.getString(R.string.failed_to_add_media, mediaType);
                    break;
                case WorkingMessage.IMAGE_TOO_LARGE:
                    title = res.getString(R.string.failed_to_resize_image);
                    message = res.getString(R.string.resize_image_error_information);
                    break;
                default:
                    throw new IllegalArgumentException("unknown error " + error);
                }

                MessageUtils.showErrorDialog(ComposeMessageActivity.this, title, message);
            }
        });
    }

    private void addImageAsync(final Uri uri, final boolean append) {
        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                addImage(uri, append);
            }
        }, null, R.string.adding_attachments_title);
    }

    private void addImage(Uri uri, boolean append) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("addImage: append=" + append + ", uri=" + uri);
        }

        int result = mWorkingMessage.setAttachment(WorkingMessage.IMAGE, uri, append);

        if (result == WorkingMessage.IMAGE_TOO_LARGE ||
            result == WorkingMessage.MESSAGE_SIZE_EXCEEDED) {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                log("resize image " + uri);
            }
            MessageUtils.resizeImageAsync(ComposeMessageActivity.this,
                    uri, mAttachmentEditorHandler, mResizeImageCallback, append);
            return;
        }
        handleAddAttachmentError(result, R.string.type_picture);
    }

    private void addVideoAsync(final Uri uri, final boolean append) {
        getAsyncDialog().runAsync(new Runnable() {
            @Override
            public void run() {
                addVideo(uri, append);
            }
        }, null, R.string.adding_attachments_title);
    }

    private void addVideo(Uri uri, boolean append) {
        if (uri != null) {
            int result = mWorkingMessage.setAttachment(WorkingMessage.VIDEO, uri, append);
            handleAddAttachmentError(result, R.string.type_video);
        }
    }

    private void addAudio(Uri uri) {
        int result = mWorkingMessage.setAttachment(WorkingMessage.AUDIO, uri, false);
        handleAddAttachmentError(result, R.string.type_audio);
    }

    AsyncDialog getAsyncDialog() {
        if (mAsyncDialog == null) {
            mAsyncDialog = new AsyncDialog(this);
        }
        return mAsyncDialog;
    }

    private boolean handleForwardedMessage() {
        Intent intent = getIntent();

        // If this is a forwarded message, it will have an Intent extra
        // indicating so.  If not, bail out.
        if (!mForwardMessageMode) {
            return false;
        }

        Uri uri = intent.getParcelableExtra("msg_uri");

        if (Log.isLoggable(LogTag.APP, Log.DEBUG)) {
            log("" + uri);
        }

        if (uri != null) {
            mWorkingMessage = WorkingMessage.load(this, uri);
            mWorkingMessage.setSubject(intent.getStringExtra("subject"), false);
        } else {
            mWorkingMessage.setText(intent.getStringExtra("sms_body"));
        }

        // let's clear the message thread for forwarded messages
        mMsgListAdapter.changeCursor(null);

        return true;
    }

    // Handle send actions, where we're told to send a picture(s) or text.
    private boolean handleSendIntent() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return false;
        }

        final String mimeType = intent.getType();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            if (extras.containsKey(Intent.EXTRA_STREAM)) {
                final Uri uri = (Uri)extras.getParcelable(Intent.EXTRA_STREAM);
                getAsyncDialog().runAsync(new Runnable() {
                    @Override
                    public void run() {
                        addAttachment(mimeType, uri, false);
                    }
                }, null, R.string.adding_attachments_title);
                return true;
            } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
                mWorkingMessage.setText(extras.getString(Intent.EXTRA_TEXT));
                return true;
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) &&
                extras.containsKey(Intent.EXTRA_STREAM)) {
            SlideshowModel slideShow = mWorkingMessage.getSlideshow();
            final ArrayList<Parcelable> uris = extras.getParcelableArrayList(Intent.EXTRA_STREAM);
            int currentSlideCount = slideShow != null ? slideShow.size() : 0;
            int importCount = uris.size();
            if (importCount + currentSlideCount > SlideshowEditor.MAX_SLIDE_NUM) {
                importCount = Math.min(SlideshowEditor.MAX_SLIDE_NUM - currentSlideCount,
                        importCount);
                Toast.makeText(ComposeMessageActivity.this,
                        getString(R.string.too_many_attachments,
                                SlideshowEditor.MAX_SLIDE_NUM, importCount),
                                Toast.LENGTH_LONG).show();
            }

            // Attach all the pictures/videos asynchronously off of the UI thread.
            // Show a progress dialog if adding all the slides hasn't finished
            // within half a second.
            final int numberToImport = importCount;
            getAsyncDialog().runAsync(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < numberToImport; i++) {
                        Parcelable uri = uris.get(i);
                        addAttachment(mimeType, (Uri) uri, true);
                    }
                }
            }, null, R.string.adding_attachments_title);
            return true;
        }
        return false;
    }

    // mVideoUri will look like this: content://media/external/video/media
    private static final String mVideoUri = Video.Media.getContentUri("external").toString();
    // mImageUri will look like this: content://media/external/images/media
    private static final String mImageUri = Images.Media.getContentUri("external").toString();

    private void addAttachment(String type, Uri uri, boolean append) {
        if (uri != null) {
            // When we're handling Intent.ACTION_SEND_MULTIPLE, the passed in items can be
            // videos, and/or images, and/or some other unknown types we don't handle. When
            // a single attachment is "shared" the type will specify an image or video. When
            // there are multiple types, the type passed in is "*/*". In that case, we've got
            // to look at the uri to figure out if it is an image or video.
            boolean wildcard = "*/*".equals(type);
            if (type.startsWith("image/") || (wildcard && uri.toString().startsWith(mImageUri))) {
                addImage(uri, append);
            } else if (type.startsWith("video/") ||
                    (wildcard && uri.toString().startsWith(mVideoUri))) {
                addVideo(uri, append);
            }
        }
    }

    private String getResourcesString(int id, String mediaName) {
        Resources r = getResources();
        return r.getString(id, mediaName);
    }

    /**
     * draw the compose view at the bottom of the screen.
     */
    private void drawBottomPanel() {
        // Reset the counter for text editor.
        resetCounter();

        if (mWorkingMessage.hasSlideshow()) {
            mBottomPanel.setVisibility(View.GONE);
            mAttachmentEditor.requestFocus();
            return;
        }

        if (LOCAL_LOGV) {
            Log.v(TAG, "CMA.drawBottomPanel");
        }
        mBottomPanel.setVisibility(View.VISIBLE);

        CharSequence text = mWorkingMessage.getText();

        // TextView.setTextKeepState() doesn't like null input.
        if (text != null && mIsSmsEnabled) {
            mTextEditor.setTextKeepState(text);

            // Set the edit caret to the end of the text.
            mTextEditor.setSelection(mTextEditor.length());
        } else {
            mTextEditor.setText("");
        }
        onKeyboardStateChanged();
    }

    private void hideBottomPanel() {
        if (LOCAL_LOGV) {
            Log.v(TAG, "CMA.hideBottomPanel");
        }
        mBottomPanel.setVisibility(View.INVISIBLE);
    }

    private void drawTopPanel(boolean showSubjectEditor) {
        boolean showingAttachment = mAttachmentEditor.update(mWorkingMessage);
        mAttachmentEditorScrollView.setVisibility(showingAttachment ? View.VISIBLE : View.GONE);
        showSubjectEditor(showSubjectEditor || mWorkingMessage.hasSubject());

        invalidateOptionsMenu();
        onKeyboardStateChanged();
    }

    //==========================================================
    // Interface methods
    //==========================================================

    @Override
    public void onClick(View v) {
        if ((v == mSendButtonSms || v == mSendButtonMms) && isPreparedForSending()) {
            confirmSendMessageIfNeeded();
        } else if ((v == mRecipientsPicker)) {
            launchMultiplePhonePicker();
        }
    }

    private void launchMultiplePhonePicker() {
        Intent intent = new Intent(Intents.ACTION_GET_MULTIPLE_PHONES);
        intent.addCategory("android.intent.category.DEFAULT");
        intent.setType(Phone.CONTENT_TYPE);
        // We have to wait for the constructing complete.
        ContactList contacts = mRecipientsEditor.constructContactsFromInput(true);
        int urisCount = 0;
        Uri[] uris = new Uri[contacts.size()];
        urisCount = 0;
        for (Contact contact : contacts) {
            if (Contact.CONTACT_METHOD_TYPE_PHONE == contact.getContactMethodType()) {
                    uris[urisCount++] = contact.getPhoneUri();
            }
        }
        if (urisCount > 0) {
            intent.putExtra(Intents.EXTRA_PHONE_URIS, uris);
        }
        startActivityForResult(intent, REQUEST_CODE_PICK);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (event != null) {
            // if shift key is down, then we want to insert the '\n' char in the TextView;
            // otherwise, the default action is to send the message.
            if (!event.isShiftPressed() && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (isPreparedForSending()) {
                    confirmSendMessageIfNeeded();
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
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // This is a workaround for bug 1609057.  Since onUserInteraction() is
            // not called when the user touches the soft keyboard, we pretend it was
            // called when textfields changes.  This should be removed when the bug
            // is fixed.
            onUserInteraction();

            mWorkingMessage.setText(s);

            updateSendButtonState();

            updateCounter(s, start, before, count);

            ensureCorrectButtonHeight();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    /**
     * Ensures that if the text edit box extends past two lines then the
     * button will be shifted up to allow enough space for the character
     * counter string to be placed beneath it.
     */
    private void ensureCorrectButtonHeight() {
        int currentTextLines = mTextEditor.getLineCount();
        if (currentTextLines <= 2) {
            mTextCounter.setVisibility(View.GONE);
        }
        else if (currentTextLines > 2 && mTextCounter.getVisibility() == View.GONE) {
            // Making the counter invisible ensures that it is used to correctly
            // calculate the position of the send button even if we choose not to
            // display the text.
            mTextCounter.setVisibility(View.INVISIBLE);
        }
    }

    private final TextWatcher mSubjectEditorWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mWorkingMessage.setSubject(s, true);
            updateSendButtonState();
        }

        @Override
        public void afterTextChanged(Editable s) { }
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

        // called to enable us to show some padding between the message list and the
        // input field but when the message list is scrolled that padding area is filled
        // in with message content
        mMsgListView.setClipToPadding(false);

        mMsgListView.setOnSizeChangedListener(new OnSizeChangedListener() {
            public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.v(TAG, "onSizeChanged: w=" + width + " h=" + height +
                            " oldw=" + oldWidth + " oldh=" + oldHeight);
                }

                if (!mMessagesAndDraftLoaded && (oldHeight-height > SMOOTH_SCROLL_THRESHOLD)) {
                    // perform the delayed loading now, after keyboard opens
                    loadMessagesAndDraft(3);
                }


                // The message list view changed size, most likely because the keyboard
                // appeared or disappeared or the user typed/deleted chars in the message
                // box causing it to change its height when expanding/collapsing to hold more
                // lines of text.
                smoothScrollToEnd(false, height - oldHeight);
            }
        });

        mBottomPanel = findViewById(R.id.bottom_panel);
        mTextEditor = (EditText) findViewById(R.id.embedded_text_editor);
        mTextEditor.setOnEditorActionListener(this);
        mTextEditor.addTextChangedListener(mTextEditorWatcher);
        mTextEditor.setFilters(new InputFilter[] {
                new LengthFilter(MmsConfig.getMaxTextLimit())});
        mTextCounter = (TextView) findViewById(R.id.text_counter);
        mSendButtonMms = (TextView) findViewById(R.id.send_button_mms);
        mSendButtonSms = (ImageButton) findViewById(R.id.send_button_sms);
        mSendButtonMms.setOnClickListener(this);
        mSendButtonSms.setOnClickListener(this);
        mTopPanel = findViewById(R.id.recipients_subject_linear);
        mTopPanel.setFocusable(false);
        mAttachmentEditor = (AttachmentEditor) findViewById(R.id.attachment_editor);
        mAttachmentEditor.setHandler(mAttachmentEditorHandler);
        mAttachmentEditorScrollView = findViewById(R.id.attachment_editor_scroll_view);
    }

    private void confirmDeleteDialog(OnClickListener listener, boolean locked) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(locked ? R.string.confirm_delete_locked_message :
                    R.string.confirm_delete_message);
        builder.setPositiveButton(R.string.delete, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }

    void undeliveredMessageDialog(long date) {
        String body;

        if (date >= 0) {
            body = getString(R.string.undelivered_msg_dialog_body,
                    MessageUtils.formatTimeStampString(this, date));
        } else {
            // FIXME: we can not get sms retry time.
            body = getString(R.string.undelivered_sms_dialog_body);
        }

        Toast.makeText(this, body, Toast.LENGTH_LONG).show();
    }

    private void startMsgListQuery() {
        startMsgListQuery(MESSAGE_LIST_QUERY_TOKEN);
    }

    private void startMsgListQuery(int token) {
        if (mSendDiscreetMode) {
            return;
        }
        Uri conversationUri = mConversation.getUri();

        if (conversationUri == null) {
            log("##### startMsgListQuery: conversationUri is null, bail!");
            return;
        }

        long threadId = mConversation.getThreadId();
        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("startMsgListQuery for " + conversationUri + ", threadId=" + threadId +
                    " token: " + token + " mConversation: " + mConversation);
        }

        // Cancel any pending queries
        mBackgroundQueryHandler.cancelOperation(token);
        try {
            // Kick off the new query
            mBackgroundQueryHandler.startQuery(
                    token,
                    threadId /* cookie */,
                    conversationUri,
                    PROJECTION,
                    null, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void initMessageList() {
        if (mMsgListAdapter != null) {
            return;
        }

        String highlightString = getIntent().getStringExtra("highlight");
        Pattern highlight = highlightString == null
            ? null
            : Pattern.compile("\\b" + Pattern.quote(highlightString), Pattern.CASE_INSENSITIVE);

        // Initialize the list adapter with a null cursor.
        mMsgListAdapter = new MessageListAdapter(this, null, mMsgListView, true, highlight);
        mMsgListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
        mMsgListAdapter.setMsgListItemHandler(mMessageListItemHandler);
        mMsgListView.setAdapter(mMsgListAdapter);
        mMsgListView.setItemsCanFocus(false);
        mMsgListView.setVisibility(mSendDiscreetMode ? View.INVISIBLE : View.VISIBLE);
        mMsgListView.setOnCreateContextMenuListener(mMsgListMenuCreateListener);
        mMsgListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (view != null) {
                    ((MessageListItem) view).onMessageListItemClick();
                }
            }
        });
    }

    /**
     * Load the draft
     *
     * If mWorkingMessage has content in memory that's worth saving, return false.
     * Otherwise, call the async operation to load draft and return true.
     */
    private boolean loadDraft() {
        if (mWorkingMessage.isWorthSaving()) {
            Log.w(TAG, "CMA.loadDraft: called with non-empty working message, bail");
            return false;
        }

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("CMA.loadDraft");
        }

        mWorkingMessage = WorkingMessage.loadDraft(this, mConversation,
                new Runnable() {
                    @Override
                    public void run() {
                        drawTopPanel(false);
                        drawBottomPanel();
                        updateSendButtonState();
                    }
                });

        // WorkingMessage.loadDraft() can return a new WorkingMessage object that doesn't
        // have its conversation set. Make sure it is set.
        mWorkingMessage.setConversation(mConversation);

        return true;
    }

    private void saveDraft(boolean isStopping) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            LogTag.debug("saveDraft");
        }
        // TODO: Do something better here.  Maybe make discard() legal
        // to call twice and make isEmpty() return true if discarded
        // so it is caught in the clause above this one?
        if (mWorkingMessage.isDiscarded()) {
            return;
        }

        if (!mWaitingForSubActivity &&
                !mWorkingMessage.isWorthSaving() &&
                (!isRecipientsEditorVisible() || recipientCount() == 0)) {
            if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                log("not worth saving, discard WorkingMessage and bail");
            }
            mWorkingMessage.discard();
            return;
        }

        mWorkingMessage.saveDraft(isStopping);

        if (mToastForDraftSave) {
            Toast.makeText(this, R.string.message_saved_as_draft,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isPreparedForSending() {
        int recipientCount = recipientCount();

        return recipientCount > 0 &&
                recipientCount <= MmsConfig.getRecipientLimit() &&
                mIsSmsEnabled &&
                (mWorkingMessage.hasAttachment() || mWorkingMessage.hasText() ||
                    mWorkingMessage.hasSubject());
    }

    private int recipientCount() {
        int recipientCount;

        // To avoid creating a bunch of invalid Contacts when the recipients
        // editor is in flux, we keep the recipients list empty.  So if the
        // recipients editor is showing, see if there is anything in it rather
        // than consulting the empty recipient list.
        if (isRecipientsEditorVisible()) {
            recipientCount = mRecipientsEditor.getRecipientCount();
        } else {
            recipientCount = getRecipients().size();
        }
        return recipientCount;
    }

    private void sendMessage(boolean bCheckEcmMode) {
        if (bCheckEcmMode) {
            // TODO: expose this in telephony layer for SDK build
            String inEcm = SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE);
            if (Boolean.parseBoolean(inEcm)) {
                try {
                    startActivityForResult(
                            new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                            REQUEST_CODE_ECM_EXIT_DIALOG);
                    return;
                } catch (ActivityNotFoundException e) {
                    // continue to send message
                    Log.e(TAG, "Cannot find EmergencyCallbackModeExitDialog", e);
                }
            }
        }

        if (!mSendingMessage) {
            if (LogTag.SEVERE_WARNING) {
                String sendingRecipients = mConversation.getRecipients().serialize();
                if (!sendingRecipients.equals(mDebugRecipients)) {
                    String workingRecipients = mWorkingMessage.getWorkingRecipients();
                    if (!mDebugRecipients.equals(workingRecipients)) {
                        LogTag.warnPossibleRecipientMismatch("ComposeMessageActivity.sendMessage" +
                                " recipients in window: \"" +
                                mDebugRecipients + "\" differ from recipients from conv: \"" +
                                sendingRecipients + "\" and working recipients: " +
                                workingRecipients, this);
                    }
                }
                sanityCheckConversation();
            }

            // send can change the recipients. Make sure we remove the listeners first and then add
            // them back once the recipient list has settled.
            removeRecipientsListeners();

            mWorkingMessage.send(mDebugRecipients);

            mSentMessage = true;
            mSendingMessage = true;
            addRecipientsListeners();

            mScrollOnSend = true;   // in the next onQueryComplete, scroll the list to the end.
        }
        // But bail out if we are supposed to exit after the message is sent.
        if (mSendDiscreetMode) {
            finish();
        }
    }

    private void resetMessage() {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("resetMessage");
        }

        // Make the attachment editor hide its view.
        mAttachmentEditor.hideView();
        mAttachmentEditorScrollView.setVisibility(View.GONE);

        // Hide the subject editor.
        showSubjectEditor(false);

        // Focus to the text editor.
        mTextEditor.requestFocus();

        // We have to remove the text change listener while the text editor gets cleared and
        // we subsequently turn the message back into SMS. When the listener is listening while
        // doing the clearing, it's fighting to update its counts and itself try and turn
        // the message one way or the other.
        mTextEditor.removeTextChangedListener(mTextEditorWatcher);

        // Clear the text box.
        TextKeyListener.clear(mTextEditor.getText());

        mWorkingMessage.clearConversation(mConversation, false);
        mWorkingMessage = WorkingMessage.createEmpty(this);
        mWorkingMessage.setConversation(mConversation);

        hideRecipientEditor();
        drawBottomPanel();

        // "Or not", in this case.
        updateSendButtonState();

        // Our changes are done. Let the listener respond to text changes once again.
        mTextEditor.addTextChangedListener(mTextEditorWatcher);

        // Close the soft on-screen keyboard if we're in landscape mode so the user can see the
        // conversation.
        if (mIsLandscape) {
            hideKeyboard();
        }

        mLastRecipientCount = 0;
        mSendingMessage = false;
        invalidateOptionsMenu();
   }

    private void hideKeyboard() {
        InputMethodManager inputMethodManager =
            (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mTextEditor.getWindowToken(), 0);
    }

    private void updateSendButtonState() {
        boolean enable = false;
        if (isPreparedForSending()) {
            // When the type of attachment is slideshow, we should
            // also hide the 'Send' button since the slideshow view
            // already has a 'Send' button embedded.
            if (!mWorkingMessage.hasSlideshow()) {
                enable = true;
            } else {
                mAttachmentEditor.setCanSend(true);
            }
        } else if (null != mAttachmentEditor){
            mAttachmentEditor.setCanSend(false);
        }

        boolean requiresMms = mWorkingMessage.requiresMms();
        View sendButton = showSmsOrMmsSendButton(requiresMms);
        sendButton.setEnabled(enable);
        sendButton.setFocusable(enable);
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

    private void initActivityState(Bundle bundle) {
        Intent intent = getIntent();
        if (bundle != null) {
            setIntent(getIntent().setAction(Intent.ACTION_VIEW));
            String recipients = bundle.getString(RECIPIENTS);
            if (LogTag.VERBOSE) log("get mConversation by recipients " + recipients);
            mConversation = Conversation.get(this,
                    ContactList.getByNumbers(recipients,
                            false /* don't block */, true /* replace number */), false);
            addRecipientsListeners();
            mSendDiscreetMode = bundle.getBoolean(KEY_EXIT_ON_SENT, false);
            mForwardMessageMode = bundle.getBoolean(KEY_FORWARDED_MESSAGE, false);

            if (mSendDiscreetMode) {
                mMsgListView.setVisibility(View.INVISIBLE);
            }
            mWorkingMessage.readStateFromBundle(bundle);

            return;
        }

        // If we have been passed a thread_id, use that to find our conversation.
        long threadId = intent.getLongExtra(THREAD_ID, 0);
        if (threadId > 0) {
            if (LogTag.VERBOSE) log("get mConversation by threadId " + threadId);
            mConversation = Conversation.get(this, threadId, false);
        } else {
            Uri intentData = intent.getData();
            if (intentData != null) {
                // try to get a conversation based on the data URI passed to our intent.
                if (LogTag.VERBOSE) log("get mConversation by intentData " + intentData);
                mConversation = Conversation.get(this, intentData, false);
                mWorkingMessage.setText(getBody(intentData));
            } else {
                // special intent extra parameter to specify the address
                String address = intent.getStringExtra("address");
                if (!TextUtils.isEmpty(address)) {
                    if (LogTag.VERBOSE) log("get mConversation by address " + address);
                    mConversation = Conversation.get(this, ContactList.getByNumbers(address,
                            false /* don't block */, true /* replace number */), false);
                } else {
                    if (LogTag.VERBOSE) log("create new conversation");
                    mConversation = Conversation.createNew(this);
                }
            }
        }
        addRecipientsListeners();
        updateThreadIdIfRunning();

        mSendDiscreetMode = intent.getBooleanExtra(KEY_EXIT_ON_SENT, false);
        mForwardMessageMode = intent.getBooleanExtra(KEY_FORWARDED_MESSAGE, false);
        if (mSendDiscreetMode) {
            mMsgListView.setVisibility(View.INVISIBLE);
        }
        if (intent.hasExtra("sms_body")) {
            mWorkingMessage.setText(intent.getStringExtra("sms_body"));
        }
        mWorkingMessage.setSubject(intent.getStringExtra("subject"), false);
    }

    private void initFocus() {
        if (!mIsKeyboardOpen) {
            return;
        }

        // If the recipients editor is visible, there is nothing in it,
        // and the text editor is not already focused, focus the
        // recipients editor.
        if (isRecipientsEditorVisible()
                && TextUtils.isEmpty(mRecipientsEditor.getText())
                && !mTextEditor.isFocused()) {
            mRecipientsEditor.requestFocus();
            return;
        }

        // If we decided not to focus the recipients editor, focus the text editor.
        mTextEditor.requestFocus();
    }

    private final MessageListAdapter.OnDataSetChangedListener
                    mDataSetChangedListener = new MessageListAdapter.OnDataSetChangedListener() {
        @Override
        public void onDataSetChanged(MessageListAdapter adapter) {
        }

        @Override
        public void onContentChanged(MessageListAdapter adapter) {
            startMsgListQuery();
        }
    };

    /**
     * smoothScrollToEnd will scroll the message list to the bottom if the list is already near
     * the bottom. Typically this is called to smooth scroll a newly received message into view.
     * It's also called when sending to scroll the list to the bottom, regardless of where it is,
     * so the user can see the just sent message. This function is also called when the message
     * list view changes size because the keyboard state changed or the compose message field grew.
     *
     * @param force always scroll to the bottom regardless of current list position
     * @param listSizeChange the amount the message list view size has vertically changed
     */
    private void smoothScrollToEnd(boolean force, int listSizeChange) {
        int lastItemVisible = mMsgListView.getLastVisiblePosition();
        int lastItemInList = mMsgListAdapter.getCount() - 1;
        if (lastItemVisible < 0 || lastItemInList < 0) {
            if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.v(TAG, "smoothScrollToEnd: lastItemVisible=" + lastItemVisible +
                        ", lastItemInList=" + lastItemInList +
                        ", mMsgListView not ready");
            }
            return;
        }

        View lastChildVisible =
                mMsgListView.getChildAt(lastItemVisible - mMsgListView.getFirstVisiblePosition());
        int lastVisibleItemBottom = 0;
        int lastVisibleItemHeight = 0;
        if (lastChildVisible != null) {
            lastVisibleItemBottom = lastChildVisible.getBottom();
            lastVisibleItemHeight = lastChildVisible.getHeight();
        }

        if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "smoothScrollToEnd newPosition: " + lastItemInList +
                    " mLastSmoothScrollPosition: " + mLastSmoothScrollPosition +
                    " first: " + mMsgListView.getFirstVisiblePosition() +
                    " lastItemVisible: " + lastItemVisible +
                    " lastVisibleItemBottom: " + lastVisibleItemBottom +
                    " lastVisibleItemBottom + listSizeChange: " +
                    (lastVisibleItemBottom + listSizeChange) +
                    " mMsgListView.getHeight() - mMsgListView.getPaddingBottom(): " +
                    (mMsgListView.getHeight() - mMsgListView.getPaddingBottom()) +
                    " listSizeChange: " + listSizeChange);
        }
        // Only scroll if the list if we're responding to a newly sent message (force == true) or
        // the list is already scrolled to the end. This code also has to handle the case where
        // the listview has changed size (from the keyboard coming up or down or the message entry
        // field growing/shrinking) and it uses that grow/shrink factor in listSizeChange to
        // compute whether the list was at the end before the resize took place.
        // For example, when the keyboard comes up, listSizeChange will be negative, something
        // like -524. The lastChild listitem's bottom value will be the old value before the
        // keyboard became visible but the size of the list will have changed. The test below
        // add listSizeChange to bottom to figure out if the old position was already scrolled
        // to the bottom. We also scroll the list if the last item is taller than the size of the
        // list. This happens when the keyboard is up and the last item is an mms with an
        // attachment thumbnail, such as picture. In this situation, we want to scroll the list so
        // the bottom of the thumbnail is visible and the top of the item is scroll off the screen.
        int listHeight = mMsgListView.getHeight();
        boolean lastItemTooTall = lastVisibleItemHeight > listHeight;
        boolean willScroll = force ||
                ((listSizeChange != 0 || lastItemInList != mLastSmoothScrollPosition) &&
                lastVisibleItemBottom + listSizeChange <=
                    listHeight - mMsgListView.getPaddingBottom());
        if (willScroll || (lastItemTooTall && lastItemInList == lastItemVisible)) {
            if (Math.abs(listSizeChange) > SMOOTH_SCROLL_THRESHOLD) {
                // When the keyboard comes up, the window manager initiates a cross fade
                // animation that conflicts with smooth scroll. Handle that case by jumping the
                // list directly to the end.
                if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.v(TAG, "keyboard state changed. setSelection=" + lastItemInList);
                }
                if (lastItemTooTall) {
                    // If the height of the last item is taller than the whole height of the list,
                    // we need to scroll that item so that its top is negative or above the top of
                    // the list. That way, the bottom of the last item will be exposed above the
                    // keyboard.
                    mMsgListView.setSelectionFromTop(lastItemInList,
                            listHeight - lastVisibleItemHeight);
                } else {
                    mMsgListView.setSelection(lastItemInList);
                }
            } else if (lastItemInList - lastItemVisible > MAX_ITEMS_TO_INVOKE_SCROLL_SHORTCUT) {
                if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.v(TAG, "too many to scroll, setSelection=" + lastItemInList);
                }
                mMsgListView.setSelection(lastItemInList);
            } else {
                if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.v(TAG, "smooth scroll to " + lastItemInList);
                }
                if (lastItemTooTall) {
                    // If the height of the last item is taller than the whole height of the list,
                    // we need to scroll that item so that its top is negative or above the top of
                    // the list. That way, the bottom of the last item will be exposed above the
                    // keyboard. We should use smoothScrollToPositionFromTop here, but it doesn't
                    // seem to work -- the list ends up scrolling to a random position.
                    mMsgListView.setSelectionFromTop(lastItemInList,
                            listHeight - lastVisibleItemHeight);
                } else {
                    mMsgListView.smoothScrollToPosition(lastItemInList);
                }
                mLastSmoothScrollPosition = lastItemInList;
            }
        }
    }

    private final class BackgroundQueryHandler extends ConversationQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch(token) {
                case MESSAGE_LIST_QUERY_TOKEN:
                    mConversation.blockMarkAsRead(false);

                    // check consistency between the query result and 'mConversation'
                    long tid = (Long) cookie;

                    if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                        log("##### onQueryComplete: msg history result for threadId " + tid);
                    }
                    if (tid != mConversation.getThreadId()) {
                        log("onQueryComplete: msg history query result is for threadId " +
                                tid + ", but mConversation has threadId " +
                                mConversation.getThreadId() + " starting a new query");
                        if (cursor != null) {
                            cursor.close();
                        }
                        startMsgListQuery();
                        return;
                    }

                    // check consistency b/t mConversation & mWorkingMessage.mConversation
                    ComposeMessageActivity.this.sanityCheckConversation();

                    int newSelectionPos = -1;
                    long targetMsgId = getIntent().getLongExtra("select_id", -1);
                    if (targetMsgId != -1) {
                        if (cursor != null) {
                            cursor.moveToPosition(-1);
                            while (cursor.moveToNext()) {
                                long msgId = cursor.getLong(COLUMN_ID);
                                if (msgId == targetMsgId) {
                                    newSelectionPos = cursor.getPosition();
                                    break;
                                }
                            }
                        }
                    } else if (mSavedScrollPosition != -1) {
                        // mSavedScrollPosition is set when this activity pauses. If equals maxint,
                        // it means the message list was scrolled to the end. Meanwhile, messages
                        // could have been received. When the activity resumes and we were
                        // previously scrolled to the end, jump the list so any new messages are
                        // visible.
                        if (mSavedScrollPosition == Integer.MAX_VALUE) {
                            int cnt = mMsgListAdapter.getCount();
                            if (cnt > 0) {
                                // Have to wait until the adapter is loaded before jumping to
                                // the end.
                                newSelectionPos = cnt - 1;
                                mSavedScrollPosition = -1;
                            }
                        } else {
                            // remember the saved scroll position before the activity is paused.
                            // reset it after the message list query is done
                            newSelectionPos = mSavedScrollPosition;
                            mSavedScrollPosition = -1;
                        }
                    }

                    mMsgListAdapter.changeCursor(cursor);

                    if (newSelectionPos != -1) {
                        mMsgListView.setSelection(newSelectionPos);     // jump the list to the pos
                    } else {
                        int count = mMsgListAdapter.getCount();
                        long lastMsgId = 0;
                        if (cursor != null && count > 0) {
                            cursor.moveToLast();
                            lastMsgId = cursor.getLong(COLUMN_ID);
                        }
                        // mScrollOnSend is set when we send a message. We always want to scroll
                        // the message list to the end when we send a message, but have to wait
                        // until the DB has changed. We also want to scroll the list when a
                        // new message has arrived.
                        smoothScrollToEnd(mScrollOnSend || lastMsgId != mLastMessageId, 0);
                        mLastMessageId = lastMsgId;
                        mScrollOnSend = false;
                    }
                    // Adjust the conversation's message count to match reality. The
                    // conversation's message count is eventually used in
                    // WorkingMessage.clearConversation to determine whether to delete
                    // the conversation or not.
                    mConversation.setMessageCount(mMsgListAdapter.getCount());

                    // Once we have completed the query for the message history, if
                    // there is nothing in the cursor and we are not composing a new
                    // message, we must be editing a draft in a new conversation (unless
                    // mSentMessage is true).
                    // Show the recipients editor to give the user a chance to add
                    // more people before the conversation begins.
                    if (cursor != null && cursor.getCount() == 0
                            && !isRecipientsEditorVisible() && !mSentMessage) {
                        initRecipientsEditor();
                    }

                    // FIXME: freshing layout changes the focused view to an unexpected
                    // one, set it back to TextEditor forcely.
                    mTextEditor.requestFocus();

                    invalidateOptionsMenu();    // some menu items depend on the adapter's count
                    return;

                case ConversationList.HAVE_LOCKED_MESSAGES_TOKEN:
                    if (ComposeMessageActivity.this.isFinishing()) {
                        Log.w(TAG, "ComposeMessageActivity is finished, do nothing ");
                        if (cursor != null) {
                            cursor.close();
                        }
                        return ;
                    }
                    @SuppressWarnings("unchecked")
                    ArrayList<Long> threadIds = (ArrayList<Long>)cookie;
                    ConversationList.confirmDeleteThreadDialog(
                            new ConversationList.DeleteThreadListener(threadIds,
                                mBackgroundQueryHandler, ComposeMessageActivity.this),
                            threadIds,
                            cursor != null && cursor.getCount() > 0,
                            ComposeMessageActivity.this);
                    if (cursor != null) {
                        cursor.close();
                    }
                    break;

                case MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN:
                    // check consistency between the query result and 'mConversation'
                    tid = (Long) cookie;

                    if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                        log("##### onQueryComplete (after delete): msg history result for threadId "
                                + tid);
                    }
                    if (cursor == null) {
                        return;
                    }
                    if (tid > 0 && cursor.getCount() == 0) {
                        // We just deleted the last message and the thread will get deleted
                        // by a trigger in the database. Clear the threadId so next time we
                        // need the threadId a new thread will get created.
                        log("##### MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN clearing thread id: "
                                + tid);
                        Conversation conv = Conversation.get(ComposeMessageActivity.this, tid,
                                false);
                        if (conv != null) {
                            conv.clearThreadId();
                            conv.setDraftState(false);
                        }
                        // The last message in this converation was just deleted. Send the user
                        // to the conversation list.
                        exitComposeMessageActivity(new Runnable() {
                            @Override
                            public void run() {
                                goToConversationList();
                            }
                        });
                    }
                    cursor.close();
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onDeleteComplete(token, cookie, result);
            switch(token) {
                case ConversationList.DELETE_CONVERSATION_TOKEN:
                    mConversation.setMessageCount(0);
                    // fall through
                case DELETE_MESSAGE_TOKEN:
                    if (cookie instanceof Boolean && ((Boolean)cookie).booleanValue()) {
                        // If we just deleted the last message, reset the saved id.
                        mLastMessageId = 0;
                    }
                    // Update the notification for new messages since they
                    // may be deleted.
                    MessagingNotification.nonBlockingUpdateNewMessageIndicator(
                            ComposeMessageActivity.this, MessagingNotification.THREAD_NONE, false);
                    // Update the notification for failed messages since they
                    // may be deleted.
                    updateSendFailedNotification();
                    break;
            }
            // If we're deleting the whole conversation, throw away
            // our current working message and bail.
            if (token == ConversationList.DELETE_CONVERSATION_TOKEN) {
                ContactList recipients = mConversation.getRecipients();
                mWorkingMessage.discard();

                // Remove any recipients referenced by this single thread from the
                // contacts cache. It's possible for two or more threads to reference
                // the same contact. That's ok if we remove it. We'll recreate that contact
                // when we init all Conversations below.
                if (recipients != null) {
                    for (Contact contact : recipients) {
                        contact.removeFromCache();
                    }
                }

                // Make sure the conversation cache reflects the threads in the DB.
                Conversation.init(ComposeMessageActivity.this);
                finish();
            } else if (token == DELETE_MESSAGE_TOKEN) {
                // Check to see if we just deleted the last message
                startMsgListQuery(MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN);
            }

            MmsWidgetProvider.notifyDatasetChanged(getApplicationContext());
        }
    }

    @Override
    public void onUpdate(final Contact updated) {
        // Using an existing handler for the post, rather than conjuring up a new one.
        mMessageListItemHandler.post(new Runnable() {
            @Override
            public void run() {
                ContactList recipients = isRecipientsEditorVisible() ?
                        mRecipientsEditor.constructContactsFromInput(false) : getRecipients();
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    log("[CMA] onUpdate contact updated: " + updated);
                    log("[CMA] onUpdate recipients: " + recipients);
                }
                updateTitle(recipients);

                // The contact information for one (or more) of the recipients has changed.
                // Rebuild the message list so each MessageItem will get the last contact info.
                ComposeMessageActivity.this.mMsgListAdapter.notifyDataSetChanged();

                // Don't do this anymore. When we're showing chips, we don't want to switch from
                // chips to text.
//                if (mRecipientsEditor != null) {
//                    mRecipientsEditor.populate(recipients);
//                }
            }
        });
    }

    private void addRecipientsListeners() {
        Contact.addListener(this);
    }

    private void removeRecipientsListeners() {
        Contact.removeListener(this);
    }

    public static Intent createIntent(Context context, long threadId) {
        Intent intent = new Intent(context, ComposeMessageActivity.class);

        if (threadId > 0) {
            intent.setData(Conversation.getUri(threadId));
        }

        return intent;
    }

    private String getBody(Uri uri) {
        if (uri == null) {
            return null;
        }
        String urlStr = uri.getSchemeSpecificPart();
        if (!urlStr.contains("?")) {
            return null;
        }
        urlStr = urlStr.substring(urlStr.indexOf('?') + 1);
        String[] params = urlStr.split("&");
        for (String p : params) {
            if (p.startsWith("body=")) {
                try {
                    return URLDecoder.decode(p.substring(5), "UTF-8");
                } catch (UnsupportedEncodingException e) { }
            }
        }
        return null;
    }

    private void updateThreadIdIfRunning() {
        if (mIsRunning && mConversation != null) {
            if (DEBUG) {
                Log.v(TAG, "updateThreadIdIfRunning: threadId: " +
                        mConversation.getThreadId());
            }
            MessagingNotification.setCurrentlyDisplayedThreadId(mConversation.getThreadId());
        } else {
            if (DEBUG) {
                Log.v(TAG, "updateThreadIdIfRunning: mIsRunning: " + mIsRunning +
                        " mConversation: " + mConversation);
            }
        }
        // If we're not running, but resume later, the current thread ID will be set in onResume()
    }
}
