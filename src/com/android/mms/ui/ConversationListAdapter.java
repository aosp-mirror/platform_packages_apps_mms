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
import com.google.android.mms.util.SqliteWrapper;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Sms.Conversations;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

/**
 * The back-end data adapter for ConversationList.
 */
//TODO: This should be public class ConversationListAdapter extends ArrayAdapter<Conversation>
public class ConversationListAdapter extends CursorAdapter {
    static final String[] PROJECTION = new String[] {
        Threads._ID,                      // 0
        Threads.MESSAGE_COUNT,            // 1
        Threads.RECIPIENT_IDS,            // 2
        Threads.DATE,                     // 3
        Threads.READ,                     // 4
        Threads.SNIPPET,                  // 5
        Threads.SNIPPET_CHARSET,          // 6
        Threads.ERROR                     // 7
    };

    static final int COLUMN_ID             = 0;
    static final int COLUMN_MESSAGE_COUNT  = 1;
    static final int COLUMN_RECIPIENTS_IDS = 2;
    static final int COLUMN_DATE           = 3;
    static final int COLUMN_READ           = 4;
    static final int COLUMN_SNIPPET        = 5;
    static final int COLUMN_SNIPPET_CHARSET = 6;
    static final int COLUMN_ERROR          = 7;

    static final String[] DRAFT_PROJECTION = new String[] {
        Threads._ID,                      // 0
        Conversations.THREAD_ID           // 1
    };

    static final String[] SEARCH_PROJECTION = new String[] {
        MmsSms.TYPE_DISCRIMINATOR_COLUMN, // 0
        BaseColumns._ID,                  // 1
        Conversations.THREAD_ID,          // 2
        // For SMS
        Sms.ADDRESS,                      // 3
        Sms.BODY,                         // 4
        Sms.DATE,                         // 5
        Sms.READ,                         // 6
        Sms.TYPE,                         // 7
        // For MMS
        Mms.SUBJECT,                      // 8
        Mms.SUBJECT_CHARSET,              // 9
        Mms.DATE,                         // 10
        Mms.READ,                         // 11
        //Additional columns for searching
        Part.FILENAME,                    // 12
        Part.NAME,                        // 13
    };

    static final int COLUMN_MESSAGE_TYPE   = 0;
    static final int COLUMN_MESSAGE_ID     = 1;
    static final int COLUMN_THREAD_ID      = 2;
    static final int COLUMN_SMS_ADDRESS    = 3;
    static final int COLUMN_SMS_BODY       = 4;
    static final int COLUMN_SMS_DATE       = 5;
    static final int COLUMN_SMS_READ       = 6;
    static final int COLUMN_SMS_TYPE       = 7;
    static final int COLUMN_MMS_SUBJECT    = 8;
    static final int COLUMN_MMS_SUBJECT_CHARSET = 9;
    static final int COLUMN_MMS_DATE       = 10;
    static final int COLUMN_MMS_READ       = 11;

    private final LayoutInflater mFactory;
    private final boolean mSimpleMode;

    public ConversationListAdapter(Context context, Cursor cursor, boolean simple) {
        super(context, cursor);
        mSimpleMode = simple;
        mFactory = LayoutInflater.from(context);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof ConversationHeaderView) {
            String address, subject;
            long threadId, date;
            boolean read, error;
            int messageCount = 0;

            if (mSimpleMode) {
                threadId = cursor.getLong(COLUMN_ID);
                address = MessageUtils.getRecipientsByIds(
                        context, cursor.getString(COLUMN_RECIPIENTS_IDS));
                subject = MessageUtils.extractEncStrFromCursor(
                        cursor, COLUMN_SNIPPET, COLUMN_SNIPPET_CHARSET);
                date = cursor.getLong(COLUMN_DATE);
                read = cursor.getInt(COLUMN_READ) != 0;
                error = cursor.getInt(COLUMN_ERROR) != 0;
                messageCount = cursor.getInt(COLUMN_MESSAGE_COUNT);
            } else {
                threadId = cursor.getLong(COLUMN_THREAD_ID);
                String msgType = cursor.getString(COLUMN_MESSAGE_TYPE);
                if (msgType.equals("sms")) {
                    address = cursor.getString(COLUMN_SMS_ADDRESS);
                    subject = cursor.getString(COLUMN_SMS_BODY);
                    date = cursor.getLong(COLUMN_SMS_DATE);
                    // FIXME: This is wrong! We cannot determine whether a
                    // thread is read or not by the read flag of the latest
                    // message in the thread.
                    read = cursor.getInt(COLUMN_SMS_READ) != 0;
                } else {
                    address = MessageUtils.getAddressByThreadId(
                            context, threadId);
                    subject = MessageUtils.extractEncStrFromCursor(
                            cursor, COLUMN_MMS_SUBJECT, COLUMN_MMS_SUBJECT_CHARSET);
                    date = cursor.getLong(COLUMN_MMS_DATE) * 1000;
                    read = cursor.getInt(COLUMN_MMS_READ) != 0;
                }
                error = false;
            }

            String timestamp = MessageUtils.formatTimeStampString(
                    context, date);

            if (TextUtils.isEmpty(address)) {
                address = mContext.getString(R.string.anonymous_recipient);
            }
            if (TextUtils.isEmpty(subject)) {
                subject = mContext.getString(R.string.no_subject_view);
            }

            ConversationHeader ch = new ConversationHeader(
                    threadId, address, subject, timestamp,
                    read, error, hasDraft(threadId), messageCount);

            ((ConversationHeaderView) view).bind(context, ch);
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mFactory.inflate(R.layout.conversation_header, parent, false);
    }

    public boolean isSimpleMode() {
        return mSimpleMode;
    }

    public void registerObservers() {
        if (mCursor != null) {
            try {
                mCursor.registerContentObserver(mChangeObserver);
            } catch (IllegalStateException e) {
                // FIXME: should use more graceful method to check whether the
                // mChangeObserver have been registered before register it.
                // Ignore IllegalStateException.
            }
            try {
                mCursor.registerDataSetObserver(mDataSetObserver);
            } catch (IllegalStateException e) {
                // FIXME: should use more graceful method to check whether the
                // mDataSetObserver have been registered before register it.
                // Ignore IllegalStateException.
            }
        }
    }

    public void unregisterObservers() {
        if (mCursor != null) {
            try {
                mCursor.unregisterContentObserver(mChangeObserver);
            } catch (IllegalStateException e) {
                // FIXME: should use more graceful method to check whether the
                // mChangeObserver have been unregistered before unregister it.
                // Ignore IllegalStateException.
            }
            try {
                mCursor.unregisterDataSetObserver(mDataSetObserver);
            } catch (IllegalStateException e) {
                // FIXME: should use more graceful method to check whether the
                // mDataSetObserver have been unregistered before unregister it.
                // Ignore IllegalStateException.
            }
        }
    }

    private boolean hasDraft(long threadId) {
        String selection = Conversations.THREAD_ID + "=" + threadId;
        Cursor cursor = SqliteWrapper.query(mContext,
                            mContext.getContentResolver(), MmsSms.CONTENT_DRAFT_URI,
                            DRAFT_PROJECTION, selection, null, null);
        return (null != cursor) && cursor.moveToFirst();
    }

    @Override
    public void changeCursor(Cursor cursor) {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }

        super.changeCursor(cursor);
    }
}
