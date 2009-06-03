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

package com.android.mms.util;

import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.google.android.mms.util.SqliteWrapper;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Conversations;
import android.util.Log;

/**
 * The recycler is responsible for deleting old messages.
 */
public abstract class Recycler {
    private static final boolean LOCAL_DEBUG = false;
    private static final String TAG = "Recycler";

    // Default preference values
    private static final boolean DEFAULT_AUTO_DELETE  = false;

    private static Recycler sSmsRecycler;
    private static Recycler sMmsRecycler;
    
    public static Recycler getSmsRecycler() {
        if (sSmsRecycler == null) {
            sSmsRecycler = new SmsRecycler();
        }
        return sSmsRecycler;
    }

    public static Recycler getMmsRecycler() {
        if (sMmsRecycler == null) {
            sMmsRecycler = new MmsRecycler();
        }
        return sMmsRecycler;
    }
    
    public void deleteOldMessages(Context context) {
        if (LOCAL_DEBUG) {
            Log.v(TAG, "Recycler.deleteOldMessages this: " + this);
        }
        if (!isAutoDeleteEnabled(context)) {
            return;
        }

        Cursor cursor = getAllThreads(context);
        int limit = getMessageLimit(context);
        try {
            while (cursor.moveToNext()) {
                long threadId = getThreadId(cursor);
                deleteMessagesForThread(context, threadId, limit);
            }
        } finally {
            cursor.close();
        }
    }
    
    public void deleteOldMessagesByThreadId(Context context, long threadId) {
        if (LOCAL_DEBUG) {
            Log.v(TAG, "Recycler.deleteOldMessagesByThreadId this: " + this +
                    " threadId: " + threadId);
        }
        if (!isAutoDeleteEnabled(context)) {
            return;
        }
        
        deleteMessagesForThread(context, threadId, getMessageLimit(context));
    }

    private boolean isAutoDeleteEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(MessagingPreferenceActivity.AUTO_DELETE,
                DEFAULT_AUTO_DELETE);
    }

    abstract public int getMessageLimit(Context context);

    abstract protected long getThreadId(Cursor cursor);

    abstract protected Cursor getAllThreads(Context context);
    
    abstract protected void deleteMessagesForThread(Context context, long threadId, int keep);
    
    abstract protected void dumpMessage(Cursor cursor, Context context);

    static class SmsRecycler extends Recycler {
        private static final String[] ALL_SMS_THREADS_PROJECTION = {
            Telephony.Sms.Conversations.THREAD_ID, Telephony.Sms.Conversations.MESSAGE_COUNT
        };

        private static final int ID             = 0;
        private static final int MESSAGE_COUNT  = 1;

        static private final String[] SMS_MESSAGE_PROJECTION = new String[] {
            BaseColumns._ID,
            Conversations.THREAD_ID,
            Sms.ADDRESS,
            Sms.BODY,
            Sms.DATE,
            Sms.READ,
            Sms.TYPE,
            Sms.STATUS,
        };

        // The indexes of the default columns which must be consistent
        // with above PROJECTION.
        static private final int COLUMN_ID                  = 0;
        static private final int COLUMN_THREAD_ID           = 1;
        static private final int COLUMN_SMS_ADDRESS         = 2;
        static private final int COLUMN_SMS_BODY            = 3;
        static private final int COLUMN_SMS_DATE            = 4;
        static private final int COLUMN_SMS_READ            = 5;
        static private final int COLUMN_SMS_TYPE            = 6;
        static private final int COLUMN_SMS_STATUS          = 7;

        private final String MAX_SMS_MESSAGES_PER_THREAD = "MaxSmsMessagesPerThread";
        private final int MAX_SMS_MESSAGES_PER_THREAD_DEFAULT = 200;

        public int getMessageLimit(Context context) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return prefs.getInt(MAX_SMS_MESSAGES_PER_THREAD, MAX_SMS_MESSAGES_PER_THREAD_DEFAULT);   
        }

        protected long getThreadId(Cursor cursor) {
            return cursor.getLong(ID);
        }
        
        protected Cursor getAllThreads(Context context) {
            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = SqliteWrapper.query(context, resolver,
                    Telephony.Sms.Conversations.CONTENT_URI,
                    ALL_SMS_THREADS_PROJECTION, null, null, Conversations.DEFAULT_SORT_ORDER);

            return cursor;
        }

        protected void deleteMessagesForThread(Context context, long threadId, int keep) {
            if (LOCAL_DEBUG) {
                Log.v(TAG, "SMS: deleteMessagesForThread");
            }
            ContentResolver resolver = context.getContentResolver();
            // TODO: add check for locked column when added
            Cursor cursor = SqliteWrapper.query(context, resolver,
                    ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, threadId),
                    SMS_MESSAGE_PROJECTION,
                    "read=1",
                    null, "date DESC");     // get in newest to oldest order

            int count = cursor.getCount();
            int numberToDelete = count - keep;
            if (LOCAL_DEBUG) {
                Log.v(TAG, "SMS: deleteMessagesForThread keep: " + keep +
                        " count: " + count +
                        " numberToDelete: " + numberToDelete);
            }
            if (numberToDelete <= 0) {
                return;
            }
            try {
                // Move to the keep limit and then delete everything older than that one.
                cursor.move(keep);
                long latestDate = cursor.getLong(COLUMN_SMS_DATE);

                // TODO: add check for locked column when added
                long cntDeleted = SqliteWrapper.delete(context, resolver, 
                        ContentUris.withAppendedId(Sms.Conversations.CONTENT_URI, threadId),
                        "read=1 AND date<" + latestDate,
                        null);
                if (LOCAL_DEBUG) {
                    Log.v(TAG, "SMS: deleteMessagesForThread cntDeleted: " + cntDeleted);
                }
            } finally {
                cursor.close();
            }
        }

        protected void dumpMessage(Cursor cursor, Context context) {
            long date = cursor.getLong(COLUMN_SMS_DATE);
            String dateStr = MessageUtils.formatTimeStampString(context, date, true);
            if (LOCAL_DEBUG) {
                Log.v(TAG, "Recycler message " +
                        "\n    address: " + cursor.getString(COLUMN_SMS_ADDRESS) +
                        "\n    body: " + cursor.getString(COLUMN_SMS_BODY) +
                        "\n    date: " + dateStr +
                        "\n    date: " + date +
                        "\n    read: " + cursor.getInt(COLUMN_SMS_READ));
            }
        }
    }

    static class MmsRecycler extends Recycler {
        private static final String[] ALL_MMS_THREADS_PROJECTION = {
            "thread_id", "count(*) as msg_count"
        };

        private static final int ID             = 0;
        private static final int MESSAGE_COUNT  = 1;

        static private final String[] MMS_MESSAGE_PROJECTION = new String[] {
            BaseColumns._ID,
            Conversations.THREAD_ID,
            Mms.DATE,
        };

        // The indexes of the default columns which must be consistent
        // with above PROJECTION.
        static private final int COLUMN_ID                  = 0;
        static private final int COLUMN_THREAD_ID           = 1;
        static private final int COLUMN_MMS_DATE            = 2;
        static private final int COLUMN_MMS_READ            = 3;

        private final String MAX_MMS_MESSAGES_PER_THREAD = "MaxMmsMessagesPerThread";
        private final int MAX_MMS_MESSAGES_PER_THREAD_DEFAULT = 10;

        public int getMessageLimit(Context context) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return prefs.getInt(MAX_MMS_MESSAGES_PER_THREAD, MAX_MMS_MESSAGES_PER_THREAD_DEFAULT);   
        }

        protected long getThreadId(Cursor cursor) {
            return cursor.getLong(ID);
        }
        
        protected Cursor getAllThreads(Context context) {
            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = SqliteWrapper.query(context, resolver,
                    Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "threads"),
                    ALL_MMS_THREADS_PROJECTION, null, null, Conversations.DEFAULT_SORT_ORDER);

            return cursor;
        }

        protected void deleteMessagesForThread(Context context, long threadId, int keep) {
            if (LOCAL_DEBUG) {
                Log.v(TAG, "MMS: deleteMessagesForThread");
            }
            ContentResolver resolver = context.getContentResolver();
            // TODO: add check for locked column when added
            Cursor cursor = SqliteWrapper.query(context, resolver,
                    Telephony.Mms.CONTENT_URI,
                    MMS_MESSAGE_PROJECTION,
                    "thread_id=" + threadId + " AND read=1",
                    null, "date DESC");     // get in newest to oldest order

            int count = cursor.getCount();
            int numberToDelete = count - keep;
            if (LOCAL_DEBUG) {
                Log.v(TAG, "MMS: deleteMessagesForThread keep: " + keep +
                        " count: " + count +
                        " numberToDelete: " + numberToDelete);
            }
            if (numberToDelete <= 0) {
                return;
            }
            try {
                // Move to the keep limit and then delete everything older than that one.
                cursor.move(keep);
                long latestDate = cursor.getLong(COLUMN_MMS_DATE);

                // TODO: add check for locked column when added
                long cntDeleted = SqliteWrapper.delete(context, resolver, 
                        Telephony.Mms.CONTENT_URI,
                        "thread_id=" + threadId + " AND read=1 AND date<" + latestDate,
                        null);
                if (LOCAL_DEBUG) {
                    Log.v(TAG, "MMS: deleteMessagesForThread cntDeleted: " + cntDeleted);
                }
            } finally {
                cursor.close();
            }
        }
        
        protected void dumpMessage(Cursor cursor, Context context) {
            long id = cursor.getLong(COLUMN_ID);
            if (LOCAL_DEBUG) {
                Log.v(TAG, "Recycler message " +
                        "\n    id: " + id
                );
            }
        }
    }

}


