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
import java.util.Set;

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

    private HashSet<Long> mDraftSet = new HashSet<Long>(4);
    private final HashSet<OnDraftChangedListener> mChangeListeners
            = new HashSet<OnDraftChangedListener>(1);
    
    public interface OnDraftChangedListener {
        void onDraftChanged(long threadId, boolean hasDraft);
    }
    
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
    private synchronized void rebuildCache() {
        HashSet<Long> oldDraftSet = mDraftSet;
        HashSet<Long> newDraftSet = new HashSet<Long>(oldDraftSet.size());
        
        Cursor cursor = SqliteWrapper.query(
                mContext,
                mContext.getContentResolver(),
                MmsSms.CONTENT_DRAFT_URI,
                DRAFT_PROJECTION, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                for (; !cursor.isAfterLast(); cursor.moveToNext()) {
                    long threadId = cursor.getLong(COLUMN_DRAFT_THREAD_ID);
                    newDraftSet.add(threadId);
                }
            }
        } finally {
            cursor.close();
        }
        
        mDraftSet = newDraftSet;
        
        // If nobody's interested in finding out about changes,
        // just bail out early.
        if (mChangeListeners.size() < 1) {
            return;
        }
        
        // Find out which drafts were removed and added and notify
        // listeners.
        Set<Long> added = new HashSet<Long>(newDraftSet);
        added.removeAll(oldDraftSet);
        Set<Long> removed = new HashSet<Long>(oldDraftSet);
        removed.removeAll(newDraftSet);

        for (OnDraftChangedListener l : mChangeListeners) {
            for (long threadId : added) {
                l.onDraftChanged(threadId, true);
            }
            for (long threadId : removed) {
                l.onDraftChanged(threadId, false);
            }
        }
    }
    
    /** Updates the has-draft status of a particular thread on
     *  a piecemeal basis, to be called when a draft has appeared
     *  or disappeared.
     */
    public synchronized void setDraftState(long threadId, boolean hasDraft) {
        if (threadId <= 0) {
            return;
        }
        
        boolean changed;
        if (hasDraft) {
            changed = mDraftSet.add(threadId);
        } else {
            changed = mDraftSet.remove(threadId);
        }

        // Notify listeners if there was a change.
        if (changed) {
            for (OnDraftChangedListener l : mChangeListeners) {
                l.onDraftChanged(threadId, hasDraft);
            }
        }
    }

    /** Returns true if the given thread ID has a draft associated
     *  with it, false if not.
     */
    public synchronized boolean hasDraft(long threadId) {
        return mDraftSet.contains(threadId);
    }

    public synchronized void addOnDraftChangedListener(OnDraftChangedListener l) {
        mChangeListeners.add(l);
    }

    public synchronized void removeOnDraftChangedListener(OnDraftChangedListener l) {
        mChangeListeners.remove(l);
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
