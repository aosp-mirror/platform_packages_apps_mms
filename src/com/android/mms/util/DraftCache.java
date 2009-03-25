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

import com.google.android.mms.util.SqliteWrapper;

import java.util.HashSet;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Sms.Conversations;
import android.util.Log;

/**
 * Cache for information about draft messages on conversations.
 */
public class DraftCache {
    private static final String TAG = "DraftCache";

    private static DraftCache sInstance;

    private final Context mContext;

    private final HashSet<Long> mDraftSet = new HashSet<Long>(4);
    
    private DraftCache(Context context) {
        mContext = context;
        refresh();
    }

    static final String[] DRAFT_PROJECTION = new String[] {
        Threads._ID,                      // 0
        Conversations.THREAD_ID           // 1
    };

    static final int COLUMN_DRAFT_THREAD_ID = 1;

    /** To be called whenever the draft state might have changed.
     *  Dispatches work to a thread and returns immediately.
     */
    public void refresh() {
        new Thread(new Runnable() {
            public void run() {
                rebuildCache();
            }
        }).start();
    }

    /** Does the actual work of rebuilding the draft cache.
     */
    private void rebuildCache() {
        synchronized (mDraftSet) {
            mDraftSet.clear();
            Cursor cursor = SqliteWrapper.query(
                    mContext,
                    mContext.getContentResolver(),
                    MmsSms.CONTENT_DRAFT_URI,
                    DRAFT_PROJECTION, null, null, null);

            try {
                if (cursor.moveToFirst()) {
                    for (; !cursor.isAfterLast(); cursor.moveToNext()) {
                        long threadId = cursor.getLong(COLUMN_DRAFT_THREAD_ID);
                        mDraftSet.add(threadId);
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }
    
    /** Updates the has-draft status of a particular thread on
     *  a piecemeal basis, to be called when a draft has appeared
     *  or disappeared.
     */
    public void setDraftState(long threadId, boolean hasDraft) {
        synchronized (mDraftSet) {
            if (hasDraft) {
                mDraftSet.add(threadId);
            } else {
                mDraftSet.remove(threadId);
            }
        }
    }

    /** Returns true if the given thread ID has a draft associated
     *  with it, false if not.
     */
    public boolean hasDraft(long threadId) {
        synchronized (mDraftSet) {
            return mDraftSet.contains(threadId);
        }
    }
    
    /**
     * Initialize the global instance. Should call only once.
     */
    public static void init(Context context) {
        sInstance = new DraftCache(context);
    }

    /**
     * Get the global instance.
     */
    public static DraftCache getInstance() {
        return sInstance;
    }
    
    private void log(String format, Object... args) {
        String s = String.format(format, args);
        Log.d(TAG, "[" + Thread.currentThread().getId() + "] " + s);
    }
}
