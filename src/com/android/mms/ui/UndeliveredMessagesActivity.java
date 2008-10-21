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

import com.android.mms.R;
import com.android.mms.transaction.MessageSender;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.SmsMessageSender;
import com.android.mms.transaction.TransactionService;
import com.android.mms.util.SendingProgressTokenManager;
import com.google.android.mms.MmsException;
import com.google.android.mms.util.SqliteWrapper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Allows user to manage the undelivered messages.
 */
public class UndeliveredMessagesActivity extends ListActivity {
    private static final String TAG = "UndeliveredMessagesActivity";
    private static final boolean DEBUG = true; //Config.LOGV && false;

    // IDs of the options menu items for the list of undelivered messages.
    private static final int MENU_RETRY_SENDING_ALL = 0;
    private static final int MENU_DELETE_MESSAGES   = 1;

    // IDs of the context menu items for the list of undelivered messages.
    private static final int MENU_EDIT          = 0;
    private static final int MENU_RETRY_SENDING = 1;
    private static final int MENU_DELETE        = 2;

    private static final String[] PROJECTION = {
        MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        BaseColumns._ID,
        "thread_id",
        // For SMS
        Sms.ADDRESS,
        Sms.BODY,
        Sms.DATE,
        Sms.READ,
        Sms.TYPE,
        Sms.STATUS,
        // For MMS
        Mms.SUBJECT,
        Mms.SUBJECT_CHARSET,
        Mms.DATE,
        Mms.READ,
        Mms.MESSAGE_TYPE,
        Mms.MESSAGE_BOX,
        PendingMessages.ERROR_TYPE
    };

    // Must be consistent with the PROJECTION.
    static final int COLUMN_MSG_TYPE         = 0;
    static final int COLUMN_ID               = 1;
    static final int COLUMN_THREAD_ID        = 2;
    static final int COLUMN_SMS_ADDRESS      = 3;
    static final int COLUMN_SMS_BODY         = 4;
    static final int COLUMN_SMS_DATE         = 5;
    static final int COLUMN_SMS_READ         = 6;
    static final int COLUMN_SMS_BOX          = 7;
    static final int COLUMN_SMS_STATUS       = 8;
    static final int COLUMN_MMS_SUBJECT      = 9;
    static final int COLUMN_MMS_SUBJECT_CHARSET = 10;
    static final int COLUMN_MMS_DATE         = 11;
    static final int COLUMN_MMS_READ         = 12;
    static final int COLUMN_MMS_MSG_TYPE     = 13;
    static final int COLUMN_MMS_BOX          = 14;
    static final int COLUMN_MMS_ERROR_TYPE   = 15;

    private Cursor mCursor;
    private UndeliveredMessagesListAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initActivity();
    }

    private void initActivity() {
        // Cancel any failed message notifications
        MessagingNotification.cancelNotification(getApplicationContext(),
                MessagingNotification.MESSAGE_FAILED_NOTIFICATION_ID);

        mCursor = SqliteWrapper.query(this, getContentResolver(),
                    MmsSms.CONTENT_UNDELIVERED_URI, PROJECTION, null, null, null);

        if (mCursor != null) {
            startManagingCursor(mCursor);
        } else {
            Log.e(TAG, "Cannot load undelivered messages.");
            finish();
        }

        // Initialize the list adapter.
        mAdapter = new UndeliveredMessagesListAdapter(this, mCursor, getListView());
        setListAdapter(mAdapter);

        // Initialize the context menu of the list.
        getListView().setOnCreateContextMenuListener(mOnCreateContextMenuListener);
        getListView().setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long threadId = mCursor.getLong(COLUMN_THREAD_ID);
                long msgId = mCursor.getLong(COLUMN_ID);
                String msgType = mCursor.getString(COLUMN_MSG_TYPE);
                editMessage(threadId, msgId, msgType);
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (mAdapter.getCount() > 0) {
            // Menu item 'Retry sending all'
            MenuItem mi = menu.add(0, MENU_RETRY_SENDING_ALL, 0,
                    R.string.menu_retry_sending_all);
            mi.setIcon(R.drawable.ic_menu_send);
            mi.setAlphabeticShortcut('a');

            // Menu item 'Delete Messages'
            mi = menu.add(0, MENU_DELETE_MESSAGES, 0,
                    R.string.menu_delete_messages);
            mi.setIcon(R.drawable.ic_menu_delete);
            mi.setAlphabeticShortcut('x');
            return true;
        } else {
            return super.onPrepareOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RETRY_SENDING_ALL:
                // Reset position of the cursor.
                mCursor.moveToPosition(-1);
                while (mCursor.moveToNext()) {
                    long threadId = mCursor.getLong(COLUMN_THREAD_ID);
                    long msgId = mCursor.getLong(COLUMN_ID);
                    String msgType = mCursor.getString(COLUMN_MSG_TYPE);
                    retryToSendMessage(threadId, msgId, msgType);
                }
                return true;
            case MENU_DELETE_MESSAGES:
                confirmDialog(R.string.confirm_delete_all_messages,
                        mConfirmDeleteAllMessagesListener);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private final OnCreateContextMenuListener mOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            menu.add(0, MENU_EDIT, 0, R.string.menu_edit);
            menu.add(0, MENU_RETRY_SENDING, 0, R.string.menu_retry_sending);
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete);
        }
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        long threadId = mCursor.getLong(COLUMN_THREAD_ID);
        long msgId = mCursor.getLong(COLUMN_ID);
        String msgType = mCursor.getString(COLUMN_MSG_TYPE);
        switch (item.getItemId()) {
            case MENU_EDIT: {
                editMessage(threadId, msgId, msgType);
                break;
            }
            case MENU_RETRY_SENDING: {
                retryToSendMessage(threadId, msgId, msgType);
                break;
            }
            case MENU_DELETE: {
                confirmDialog(R.string.confirm_delete_message,
                        mConfirmDeleteMessageListener);
                break;
            }
        }
        return super.onContextItemSelected(item);
    }

    private void editMessage(long threadId, long msgId, String msgType) {
        Intent intent = new Intent(this, ComposeMessageActivity.class);
        intent.putExtra("thread_id", threadId);

        if (msgType.equals("sms")) {
            Uri.Builder builder = Sms.Outbox.CONTENT_URI.buildUpon();
            Uri uri = builder.appendPath(Long.toString(msgId)).build();
            ContentValues values = new ContentValues(1);
            values.put(Sms.TYPE, Sms.MESSAGE_TYPE_DRAFT);
            SqliteWrapper.update(this, getContentResolver(), uri, values, null, null);
        } else {
            Uri.Builder builder = Mms.Outbox.CONTENT_URI.buildUpon();
            Uri uri = builder.appendPath(Long.toString(msgId)).build();
            ContentValues values = new ContentValues(1);
            values.put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_DRAFTS);
            SqliteWrapper.update(this, getContentResolver(), uri, values, null, null);
        }

        startActivityIfNeeded(intent, -1);
    }

    private void retryToSendMessage(long threadId, long msgId, String msgType) {
        if ("sms".equals(msgType)) {
            // Pending SMs should be resent in other way.
            resendShortMessage(threadId, msgId);
            return;
        }

        Uri.Builder uriBuilder = PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", msgType);
        uriBuilder.appendQueryParameter("message", Long.toString(msgId));

        Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
                            uriBuilder.build(), null, null, null, null);

        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    // Reset status of this message for retrying.
                    ContentValues values = new ContentValues(3);
                    values.put(PendingMessages.ERROR_TYPE,  0);
                    values.put(PendingMessages.RETRY_INDEX, 0);
                    values.put(PendingMessages.DUE_TIME,    0);

                    int columnIndex = cursor.getColumnIndexOrThrow(
                            PendingMessages._ID);
                    long id = cursor.getLong(columnIndex);
                    getContentResolver().update(PendingMessages.CONTENT_URI,
                            values, PendingMessages._ID + "=" + id, null);

                    SendingProgressTokenManager.put(msgId, threadId);

                    // Start transaction service to retry sending.
                    startService(new Intent(this, TransactionService.class));

                    // Show dialog to notify user the retrying is in progress.
                    LinearLayout dialog = (LinearLayout) LayoutInflater.from(this).inflate(
                            R.layout.retry_sending_dialog, null);

                    long last = cursor.getLong(cursor.getColumnIndexOrThrow(
                            PendingMessages.LAST_TRY));
                    String body = getString(R.string.retrying_dialog_body).replace(
                            "%s", MessageUtils.formatTimeStampString(this, last));
                    ((TextView) dialog.findViewById(R.id.body_text_view)).setText(body);
                }
            } finally {
                cursor.close();
            }
        }
    }

    private void resendShortMessage(long threadId, long msgId) {
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
        Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
                uri, new String[] { Sms.ADDRESS, Sms.BODY }, null, null, null);

        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    MessageSender sender = new SmsMessageSender(
                            this, new String[] { cursor.getString(0) },
                            cursor.getString(1), threadId);
                    sender.sendMessage(threadId);

                    // Delete the undelivered message since the sender will
                    // save a new one into database.
                    SqliteWrapper.delete(this, getContentResolver(), uri, null, null);
                }
            } catch (MmsException e) {
                Log.e(TAG, e.getMessage());
            } finally {
                cursor.close();
            }
        }
    }

    private final OnClickListener mConfirmDeleteMessageListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            deleteMessageByCursor(UndeliveredMessagesActivity.this, mCursor);
        }
    };

    private final OnClickListener mConfirmDeleteAllMessagesListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            // Reset position of the cursor.
            mCursor.moveToPosition(-1);
            while (mCursor.moveToNext()) {
                deleteMessageByCursor(UndeliveredMessagesActivity.this, mCursor);
            }

            finish(); // leave this activity.
        }
    };

    private void confirmDialog(int messageId, OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setCancelable(true);
        builder.setMessage(messageId);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);

        builder.show();
    }

    static void deleteMessageByCursor(Context context, Cursor cursor) {
        String type = cursor.getString(COLUMN_MSG_TYPE);
        long msgId = cursor.getLong(COLUMN_ID);
        Uri baseUri = type.equals("sms") ? Sms.CONTENT_URI: Mms.CONTENT_URI;
        SqliteWrapper.delete(context, context.getContentResolver(),
                    ContentUris.withAppendedId(baseUri, msgId), null, null);
    }
}

