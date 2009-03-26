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
import com.android.mms.util.ContactInfoCache;
import com.android.mms.util.DraftCache;

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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import java.util.HashSet;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * The back-end data adapter for ConversationList.
 */
//TODO: This should be public class ConversationListAdapter extends ArrayAdapter<Conversation>
public class ConversationListAdapter extends CursorAdapter {
    private static final String TAG = "ConversationListAdapter";
    private static final boolean LOCAL_LOGV = false;

    static final String[] PROJECTION = new String[] {
        Threads._ID,                      // 0
        Threads.MESSAGE_COUNT,            // 1
        Threads.RECIPIENT_IDS,            // 2
        Threads.DATE,                     // 3
        Threads.READ,                     // 4
        Threads.SNIPPET,                  // 5
        Threads.SNIPPET_CHARSET,          // 6
        Threads.ERROR,                    // 7
        Threads.HAS_ATTACHMENT            // 8
    };

    static final int COLUMN_ID             = 0;
    static final int COLUMN_MESSAGE_COUNT  = 1;
    static final int COLUMN_RECIPIENTS_IDS = 2;
    static final int COLUMN_DATE           = 3;
    static final int COLUMN_READ           = 4;
    static final int COLUMN_SNIPPET        = 5;
    static final int COLUMN_SNIPPET_CHARSET = 6;
    static final int COLUMN_ERROR          = 7;
    static final int COLUMN_HAS_ATTACHMENT = 8;

    static final String[] DRAFT_PROJECTION = new String[] {
        Threads._ID,                      // 0
        Conversations.THREAD_ID           // 1
    };

    static final int COLUMN_DRAFT_THREAD_ID = 1;

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

    // Cache of space-separated recipient ids of a thread to the final
    // display version.

    // TODO: if you rename a contact or something, it'll cache the old
    // name (or raw number) forever in here, never listening to
    // changes from the contacts provider.  We should instead move away
    // towards using only the CachingNameStore, which does respect
    // contacts provider updates.
    private final Map<String, String> mThreadDisplayFrom;

    // For async loading of display names.
    private final ScheduledThreadPoolExecutor mAsyncLoader;
    private final Stack<Runnable> mThingsToLoad = new Stack<Runnable>();
    // We execute things in LIFO order, so as users scroll around during loading,
    // they get the most recently-requested item.
    private final Runnable mPopStackRunnable = new Runnable() {
            public void run() {
                Runnable r = null;
                synchronized (mThingsToLoad) {
                    if (!mThingsToLoad.empty()) {
                        r = mThingsToLoad.pop();
                    }
                }
                if (r != null) {
                    r.run();
                }
            }
        };

    private final ConversationList.CachingNameStore mCachingNameStore;

    public ConversationListAdapter(Context context, Cursor cursor, boolean simple,
                                   ConversationList.CachingNameStore nameStore) {
        super(context, cursor, true /* auto-requery */);
        mSimpleMode = simple;
        mFactory = LayoutInflater.from(context);
        mCachingNameStore = nameStore;
        
        mThreadDisplayFrom = new ConcurrentHashMap<String, String>();
        // 1 thread.  SQLite can't do better anyway.
        mAsyncLoader = new ScheduledThreadPoolExecutor(1);
    }

    /**
     * Returns the from text using the CachingNameStore.
     */
    private String getFromTextFromCache(String spaceSeparatedRcptIds, String address) {
        // Potentially blocking call to Contacts provider, lookup up
        // names:  (should usually be cached, though)
        String value = mCachingNameStore.getContactNames(address);

        if (TextUtils.isEmpty(value)) {
            value = mContext.getString(R.string.anonymous_recipient);
        }

        mThreadDisplayFrom.put(spaceSeparatedRcptIds, value);
        return value;
    }

    /**
     * Returns cached 'from' text of message thread (display form of list of recipients)
     */
    private String getFromTextFromMessageThread(String spaceSeparatedRcptIds) {
        // Thread IDs could in-theory be reassigned to different
        // recipients (if latest threadid was deleted and new
        // auto-increment was assigned), so our cache key is the
        // space-separated list of recipients IDs instead:
        String value = mThreadDisplayFrom.get(spaceSeparatedRcptIds);
        if (value != null) {
            return value;
        }

        return null;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof ConversationHeaderView) {
            ConversationHeaderView headerView = (ConversationHeaderView) view;
            String from, subject;
            long threadId, date;
            boolean read, error;
            int messageCount = 0;
            String spaceSeparatedRcptIds = null;
            int presenceIconResId = 0;
            boolean cacheEntryInvalid = false;
            boolean hasAttachment = false;

            if (mSimpleMode) {
                threadId = cursor.getLong(COLUMN_ID);
                spaceSeparatedRcptIds = cursor.getString(COLUMN_RECIPIENTS_IDS);
                from = getFromTextFromMessageThread(spaceSeparatedRcptIds);
                subject = MessageUtils.extractEncStrFromCursor(
                        cursor, COLUMN_SNIPPET, COLUMN_SNIPPET_CHARSET);
                date = cursor.getLong(COLUMN_DATE);
                read = cursor.getInt(COLUMN_READ) != 0;
                error = cursor.getInt(COLUMN_ERROR) != 0;
                messageCount = cursor.getInt(COLUMN_MESSAGE_COUNT);
                hasAttachment = cursor.getInt(COLUMN_HAS_ATTACHMENT) != 0;
                
                cacheEntryInvalid = true;

                // display the presence from the cache. The cache entry could be invalidated
                // in the activity's onResume(), but display the info anyways if it's in the cache.
                // If it's invalid, we'll force a refresh in the async thread.
                String address = MessageUtils.getRecipientsByIds(
                        context, spaceSeparatedRcptIds, false /* no query */);
                if (!TextUtils.isEmpty(address)) {
                    ContactInfoCache.CacheEntry entry = null;
                    ContactInfoCache cache = ContactInfoCache.getInstance();

                    if (Mms.isEmailAddress(address)) {
                        entry = cache.getContactInfoForEmailAddress(context, address,
                                false /* no query */);
                    } else {
                        entry = cache.getContactInfoForPhoneNumber(context, address,
                                false /* no query */);
                    }
                    
                    if (entry != null) {
                        presenceIconResId = entry.presenceResId;
                        cacheEntryInvalid = entry.isStale();
                        if (LOCAL_LOGV) {
                            Log.d(TAG, "ConvListAdapter.bindView: " + entry.name + ", presence=" +
                                presenceIconResId + ", cache invalid=" + cacheEntryInvalid);
                        }
                    }
                }
            } else {
                threadId = cursor.getLong(COLUMN_THREAD_ID);
                String msgType = cursor.getString(COLUMN_MESSAGE_TYPE);
                if (msgType.equals("sms")) {
                    from = cursor.getString(COLUMN_SMS_ADDRESS);
                    subject = cursor.getString(COLUMN_SMS_BODY);
                    date = cursor.getLong(COLUMN_SMS_DATE);
                    // FIXME: This is wrong! We cannot determine whether a
                    // thread is read or not by the read flag of the latest
                    // message in the thread.
                    read = cursor.getInt(COLUMN_SMS_READ) != 0;
                } else {
                    from = MessageUtils.getAddressByThreadId(
                            context, threadId);
                    subject = MessageUtils.extractEncStrFromCursor(
                            cursor, COLUMN_MMS_SUBJECT, COLUMN_MMS_SUBJECT_CHARSET);
                    date = cursor.getLong(COLUMN_MMS_DATE) * 1000;
                    read = cursor.getInt(COLUMN_MMS_READ) != 0;
                }
                error = false;
                if (TextUtils.isEmpty(from)) {
                    from = mContext.getString(R.string.anonymous_recipient);
                }
            }

            String timestamp = MessageUtils.formatTimeStampString(
                    context, date);

            if (TextUtils.isEmpty(subject)) {
                subject = mContext.getString(R.string.no_subject_view);
            }

            if (LOCAL_LOGV) Log.v(TAG, "pre-create ConversationHeader");
            boolean hasDraft = DraftCache.getInstance().hasDraft(threadId);
                
            ConversationHeader ch = new ConversationHeader(
                    threadId, from, subject, timestamp,
                    read, error, hasDraft, messageCount, hasAttachment);

            headerView.bind(context, ch);
            headerView.setPresenceIcon(presenceIconResId);

            // if the cache entry is invalid, or if we can't find the "from" field,
            // kick off an async op to refresh the name and presence
            if (cacheEntryInvalid || (from == null && spaceSeparatedRcptIds != null)) {
                startAsyncDisplayFromLoad(context, ch, headerView, spaceSeparatedRcptIds);
            }
            if (LOCAL_LOGV) Log.v(TAG, "post-bind ConversationHeader");
        } else {
            Log.e(TAG, "Unexpected bound view: " + view);
        }
    }

    private void startAsyncDisplayFromLoad(final Context context,
                                           final ConversationHeader ch,
                                           final ConversationHeaderView headerView,
                                           final String spaceSeparatedRcptIds) {
        synchronized (mThingsToLoad) {
            mThingsToLoad.push(new Runnable() {
                    public void run() {
                        String addresses = MessageUtils.getRecipientsByIds(
                                context, spaceSeparatedRcptIds, true /* allow query */);

                        // set from text
                        String fromText = getFromTextFromMessageThread(spaceSeparatedRcptIds);
                        if (TextUtils.isEmpty(fromText)) {
                            fromText = getFromTextFromCache(spaceSeparatedRcptIds, addresses);
                        }

                        int presenceIconResId = 0;

                        if (addresses != null && addresses.indexOf(';') < 0) {
                            // only set presence for single recipient
                            ContactInfoCache.CacheEntry entry = null;
                            ContactInfoCache cache = ContactInfoCache.getInstance();
                            String address = addresses;

                            if (Mms.isEmailAddress(address)) {
                                entry = cache.getContactInfoForEmailAddress(context, address,
                                        true /* allow query */);
                            } else {
                                entry = cache.getContactInfoForPhoneNumber(context, address,
                                        true /* allow query */);
                            }

                            if (entry != null) {
                                presenceIconResId = entry.presenceResId;
                            }

                            if (LOCAL_LOGV) {
                                Log.d(TAG, "ConvListAdapter.startAsyncDisplayFromLoad: " + fromText
                                    + ", presence=" + presenceIconResId + ", cacheEntry=" + entry);
                            }
                        }

                        // need to update the from text and presence icon using a callback, so
                        // they are done in the UI thread
                        ch.setFromAndPresence(fromText, presenceIconResId);
                    }
                });
        }
        mAsyncLoader.execute(mPopStackRunnable);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        if (LOCAL_LOGV) Log.v(TAG, "inflating new view");
        return mFactory.inflate(R.layout.conversation_header, parent, false);
    }

    public boolean isSimpleMode() {
        return mSimpleMode;
    }

    @Override
    public void changeCursor(Cursor cursor) {
        // Now that we are requerying, bindView will restart anything
        // that might have been pending in the async loader, so clear
        // out its job stack and let it start fresh.
        synchronized (mThingsToLoad) {
            mThingsToLoad.clear();
        }
 
        super.changeCursor(cursor);
    }
}
