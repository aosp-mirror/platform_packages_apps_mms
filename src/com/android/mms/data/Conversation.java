package com.android.mms.data;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Sms.Conversations;
import android.text.TextUtils;
import android.util.Log;

import com.android.mms.R;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.DraftCache;

/**
 * An interface for finding information about conversations and/or creating new ones.
 */
public class Conversation {
    private static final String TAG = "Conversation";
    private static final boolean DEBUG = false;

    private static final Uri sAllThreadsUri =
        Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();

    private static final String[] ALL_THREADS_PROJECTION = {
        Threads._ID, Threads.DATE, Threads.MESSAGE_COUNT, Threads.RECIPIENT_IDS,
        Threads.SNIPPET, Threads.SNIPPET_CHARSET, Threads.READ, Threads.ERROR,
        Threads.HAS_ATTACHMENT
    };
    private static final int ID             = 0;
    private static final int DATE           = 1;
    private static final int MESSAGE_COUNT  = 2;
    private static final int RECIPIENT_IDS  = 3;
    private static final int SNIPPET        = 4;
    private static final int SNIPPET_CS     = 5;
    private static final int READ           = 6;
    private static final int ERROR          = 7;
    private static final int HAS_ATTACHMENT = 8;


    private final Context mContext;

    // The thread ID of this conversation.  Can be zero in the case of a
    // new conversation where the recipient set is changing as the user
    // types and we have not hit the database yet to create a thread.
    private long mThreadId;

    private ContactList mRecipients;    // The current set of recipients.
    private long mDate;                 // The last update time.
    private int mMessageCount;          // Number of messages.
    private String mSnippet;            // Text of the most recent message.
    private boolean mHasUnreadMessages; // True if there are unread messages.
    private boolean mHasAttachment;     // True if any message has an attachment.
    private boolean mHasError;          // True if any message is in an error state.

    private Conversation(Context context) {
        mContext = context;
        mRecipients = new ContactList();
        mThreadId = 0;
    }

    private Conversation(Context context, long threadId) {
        mContext = context;
        loadFromThreadId(threadId);
    }

    private Conversation(Context context, Cursor cursor, boolean allowQuery) {
        mContext = context;
        fillFromCursor(context, this, cursor, allowQuery);
    }

    /**
     * Create a new conversation with no recipients.  {@link setRecipients} can
     * be called as many times as you like; the conversation will not be
     * created in the database until {@link ensureThreadId} is called.
     */
    private static Conversation createNew(Context context) {
        return new Conversation(context);
    }

    /**
     * Find the conversation matching the provided thread ID.
     */
    public static Conversation get(Context context, long threadId) {
        synchronized (Cache.getInstance()) {
            Conversation conv = Cache.get(threadId);
            if (conv != null)
                return conv;

            conv = new Conversation(context, threadId);
            Cache.put(conv);
            return conv;
        }
    }

    /**
     * Find the conversation matching the provided recipient set.
     * When called with an empty recipient list, equivalent to {@link createEmpty}.
     */
    public static Conversation get(Context context, ContactList recipients) {
        // If there are no recipients in the list, make a new conversation.
        if (recipients.size() < 1) {
            return createNew(context);
        }

        synchronized (Cache.getInstance()) {
            Conversation conv = Cache.get(recipients);
            if (conv != null)
                return conv;

            long threadId = getOrCreateThreadId(context, recipients);
            conv = new Conversation(context, threadId);

            Cache.put(conv);
            return conv;
        }
    }

    /**
     * Find the conversation matching in the specified Uri.  Example
     * forms: {@value content://mms-sms/conversations/3} or
     * {@value sms:+12124797990}.
     * When called with a null Uri, equivalent to {@link createEmpty}.
     */
    public static Conversation get(Context context, Uri uri) {
        if (uri == null) {
            return createNew(context);
        }

        if (DEBUG) {
            Log.v(TAG, "Conversation get URI: " + uri);
        }
        // Handle a conversation URI
        if (uri.getPathSegments().size() >= 2) {
            try {
                long threadId = Long.parseLong(uri.getPathSegments().get(1));
                if (DEBUG) {
                    Log.v(TAG, "Conversation get threadId: " + threadId);
                }
                return get(context, threadId);
            } catch (NumberFormatException exception) {
                Log.e(TAG, "Invalid URI: " + uri);
            }
        }

        String recipient = uri.getSchemeSpecificPart();
        return get(context, ContactList.getByNumbers(recipient, false));
    }

    /**
     * Returns a temporary Conversation (not representing one on disk) wrapping
     * the contents of the provided cursor.  The cursor should be the one
     * returned to your AsyncQueryHandler passed in to {@link startQueryForAll}.
     * The recipient list of this conversation can be empty if the results
     * were not in cache.
     */
    public static Conversation from(Context context, Cursor cursor) {
        return new Conversation(context, cursor, false);
    }

    /**
     * Marks all messages in this conversation as read and updates
     * relevant notifications.  This method returns immediately;
     * work is dispatched to a background thread.
     */
    public synchronized void markAsRead() {
        // If this thread has no unread messages, there's nothing to do.
        if (!hasUnreadMessages()) {
            return;
        }

        // If we have no Uri to mark (as in the case of a conversation that
        // has not yet made its way to disk), there's nothing to do.
        final Uri threadUri = getUri();
        if (threadUri == null)
            return;

        // TODO: make this once as a static?
        final ContentValues values = new ContentValues(1);
        values.put("read", 1);

        new Thread(new Runnable() {
            public void run() {
                mContext.getContentResolver().update(threadUri, values, "read=0", null);
                MessagingNotification.updateAllNotifications(mContext);
            }
        }).start();
    }

    /**
     * Returns a content:// URI referring to this conversation,
     * or null if it does not exist on disk yet.
     */
    public synchronized Uri getUri() {
        if (mThreadId <= 0)
            return null;

        return ContentUris.withAppendedId(Threads.CONTENT_URI, mThreadId);
    }

    /**
     * Return the Uri for all messages in the given thread ID.
     * @deprecated
     */
    public static Uri getUri(long threadId) {
        // TODO: Callers using this should really just have a Conversation
        // and call getUri() on it, but this guarantees no blocking.
        return ContentUris.withAppendedId(Threads.CONTENT_URI, threadId);
    }

    /**
     * Returns the thread ID of this conversation.  Can be zero if
     * {@link ensureThreadId} has not been called yet.
     */
    public synchronized long getThreadId() {
        return mThreadId;
    }

    /**
     * Guarantees that the conversation has been created in the database.
     * This will make a blocking database call if it hasn't.
     *
     * @return The thread ID of this conversation in the database
     */
    public synchronized long ensureThreadId() {
        if (DEBUG) {
            Log.v("TAG", "ensureThreadId before: " + mThreadId);
        }
        if (mThreadId <= 0) {
            mThreadId = getOrCreateThreadId(mContext, mRecipients);
        }
        if (DEBUG) {
            Log.v("TAG", "ensureThreadId after: " + mThreadId);
        }

        return mThreadId;
    }

    public synchronized void clearThreadId() {
        // remove ourself from the cache
        Cache.remove(mThreadId);

        mThreadId = 0;
    }

    /**
     * Sets the list of recipients associated with this conversation.
     * If called, {@link ensureThreadId} must be called before the next
     * operation that depends on this conversation existing in the
     * database (e.g. storing a draft message to it).
     */
    public synchronized void setRecipients(ContactList list) {
        mRecipients = list;

        // Invalidate thread ID because the recipient set has changed.
        mThreadId = 0;
    }

    /**
     * Returns the recipient set of this conversation.
     */
    public synchronized ContactList getRecipients() {
        return mRecipients;
    }

    /**
     * Returns true if a draft message exists in this conversation.
     */
    public synchronized boolean hasDraft() {
        if (mThreadId <= 0)
            return false;

        return DraftCache.getInstance().hasDraft(mThreadId);
    }

    /**
     * Sets whether or not this conversation has a draft message.
     */
    public synchronized void setDraftState(boolean hasDraft) {
        if (mThreadId <= 0)
            return;

        DraftCache.getInstance().setDraftState(mThreadId, hasDraft);
    }

    /**
     * Returns the time of the last update to this conversation in milliseconds,
     * on the {@link System.currentTimeMillis} timebase.
     */
    public synchronized long getDate() {
        return mDate;
    }

    /**
     * Returns the number of messages in this conversation, excluding the draft
     * (if it exists).
     */
    public synchronized int getMessageCount() {
        return mMessageCount;
    }

    /**
     * Returns a snippet of text from the most recent message in the conversation.
     */
    public synchronized String getSnippet() {
        return mSnippet;
    }

    /**
     * Returns true if there are any unread messages in the conversation.
     */
    public synchronized boolean hasUnreadMessages() {
        return mHasUnreadMessages;
    }

    /**
     * Returns true if any messages in the conversation have attachments.
     */
    public synchronized boolean hasAttachment() {
        return mHasAttachment;
    }

    /**
     * Returns true if any messages in the conversation are in an error state.
     */
    public synchronized boolean hasError() {
        return mHasError;
    }

    private static long getOrCreateThreadId(Context context, ContactList list) {
        HashSet<String> recipients = new HashSet<String>();
        for (Contact c : list) {
            recipients.add(c.getNumber());
        }
        return Threads.getOrCreateThreadId(context, recipients);
    }

    /*
     * The primary key of a conversation is its recipient set; override
     * equals() and hashCode() to just pass through to the internal
     * recipient sets.
     */
    @Override
    public synchronized boolean equals(Object obj) {
        try {
            Conversation other = (Conversation)obj;
            return (mRecipients.equals(other.mRecipients));
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public synchronized int hashCode() {
        return mRecipients.hashCode();
    }

    @Override
    public synchronized String toString() {
        return String.format("[%s] (tid %d)", mRecipients.serialize(), mThreadId);
    }

    /**
     * Remove any obsolete conversations sitting around on disk.
     * @deprecated
     */
    public static void cleanup(Context context) {
        // TODO: Get rid of this awful hack.
        context.getContentResolver().delete(Threads.OBSOLETE_THREADS_URI, null, null);
    }

    /**
     * Start a query for all conversations in the database on the specified
     * AsyncQueryHandler.
     *
     * @param handler An AsyncQueryHandler that will receive onQueryComplete
     *                upon completion of the query
     * @param token   The token that will be passed to onQueryComplete
     */
    public static void startQueryForAll(AsyncQueryHandler handler, int token) {
        handler.cancelOperation(token);
        handler.startQuery(token, null, sAllThreadsUri,
                ALL_THREADS_PROJECTION, null, null, Conversations.DEFAULT_SORT_ORDER);
    }

    /**
     * Start a delete of the conversation with the specified thread ID.
     *
     * @param handler An AsyncQueryHandler that will receive onDeleteComplete
     *                upon completion of the conversation being deleted
     * @param token   The token that will be passed to onDeleteComplete
     * @param threadId Thread ID of the conversation to be deleted
     */
    public static void startDelete(AsyncQueryHandler handler, int token, long threadId) {
        Uri uri = ContentUris.withAppendedId(Threads.CONTENT_URI, threadId);
        handler.startDelete(token, null, uri, "locked=0", null);
    }

    /**
     * Start deleting all conversations in the database.
     * @param handler An AsyncQueryHandler that will receive onDeleteComplete
     *                upon completion of all conversations being deleted
     * @param token   The token that will be passed to onDeleteComplete
     */
    public static void startDeleteAll(AsyncQueryHandler handler, int token) {
        handler.startDelete(token, null, Threads.CONTENT_URI, "locked=0", null);
    }

    /**
     * Fill the specified conversation with the values from the specified
     * cursor, possibly setting recipients to empty if {@value allowQuery}
     * is false and the recipient IDs are not in cache.  The cursor should
     * be one made via {@link startQueryForAll}.
     */
    private static void fillFromCursor(Context context, Conversation conv,
                                       Cursor c, boolean allowQuery) {
        synchronized (conv) {
            conv.mThreadId = c.getLong(ID);
            conv.mDate = c.getLong(DATE);
            conv.mMessageCount = c.getInt(MESSAGE_COUNT);

            // Replace the snippet with a default value if it's empty.
            String snippet = MessageUtils.extractEncStrFromCursor(c, SNIPPET, SNIPPET_CS);
            if (TextUtils.isEmpty(snippet)) {
                snippet = context.getString(R.string.no_subject_view);
            }
            conv.mSnippet = snippet;

            conv.mHasUnreadMessages = (c.getInt(READ) == 0);
            conv.mHasError = (c.getInt(ERROR) != 0);
            conv.mHasAttachment = (c.getInt(HAS_ATTACHMENT) != 0);

            String recipientIds = c.getString(RECIPIENT_IDS);
            conv.mRecipients = ContactList.getByIds(recipientIds, allowQuery);
        }
    }

    /**
     * Private cache for the use of the various forms of Conversation.get.
     */
    private static class Cache {
        private static Cache sInstance = new Cache();
        static Cache getInstance() { return sInstance; }
        private final HashSet<Conversation> mCache;
        private Cache() {
            mCache = new HashSet<Conversation>(10);
        }

        /**
         * Return the conversation with the specified thread ID, or
         * null if it's not in cache.
         */
        static Conversation get(long threadId) {
            synchronized (sInstance) {
                if (DEBUG) {
                    Log.v(TAG, "Conversation get with threadId: " + threadId);
                }
                dumpCache();
                for (Conversation c : sInstance.mCache) {
                    if (DEBUG) {
                        Log.v(TAG, "Conversation get() threadId: " + threadId +
                                " c.getThreadId(): " + c.getThreadId());
                    }
                    if (c.getThreadId() == threadId) {
                        return c;
                    }
                }
            }
            return null;
        }

        /**
         * Return the conversation with the specified recipient
         * list, or null if it's not in cache.
         */
        static Conversation get(ContactList list) {
            synchronized (sInstance) {
                if (DEBUG) {
                    Log.v(TAG, "Conversation get with ContactList: " + list);
                    dumpCache();
                }
                for (Conversation c : sInstance.mCache) {
                    if (c.getRecipients().equals(list)) {
                        return c;
                    }
                }
            }
            return null;
        }

        /**
         * Put the specified conversation in the cache.  The caller
         * should not place an already-existing conversation in the
         * cache, but rather update it in place.
         */
        static void put(Conversation c) {
            synchronized (sInstance) {
                // We update cache entries in place so people with long-
                // held references get updated.
                if (DEBUG) {
                    Log.v(TAG, "Conversation c: " + c + " put with threadid: " + c.getThreadId() +
                            " c.hash: " + c.hashCode());
                    dumpCache();
                }

                if (sInstance.mCache.contains(c)) {
                    throw new IllegalStateException("cache already contains " + c +
                            " threadId: " + c.mThreadId);
                }
                sInstance.mCache.add(c);
            }
        }

        static void remove(long threadId) {
            if (DEBUG) {
                Log.v(TAG, "remove threadid: " + threadId);
                dumpCache();
            }
            for (Conversation c : sInstance.mCache) {
                if (c.getThreadId() == threadId) {
                    sInstance.mCache.remove(c);
                    return;
                }
            }
        }

        static void dumpCache() {
            if (DEBUG) {
                synchronized (sInstance) {
                    Log.v(TAG, "Conversation dumpCache: ");
                    for (Conversation c : sInstance.mCache) {
                        Log.v(TAG, "   c: " + c + " c.getThreadId(): " + c.getThreadId() + " hash: " + c.hashCode());
                    }
                }
            }
        }

        /**
         * Remove all conversations from the cache that are not in
         * the provided set of thread IDs.
         */
        static void keepOnly(Set<Long> threads) {
            synchronized (sInstance) {
                Iterator<Conversation> iter = sInstance.mCache.iterator();
                while (iter.hasNext()) {
                    Conversation c = iter.next();
                    if (!threads.contains(c.getThreadId())) {
                        iter.remove();
                    }
                }
            }
        }
    }

    /**
     * Set up the conversation cache.  To be called once at application
     * startup time.
     */
    public static void init(final Context context) {
        new Thread(new Runnable() {
            public void run() {
                cacheAllThreads(context);
            }
        }).start();
    }

    private static void cacheAllThreads(Context context) {
        synchronized (Cache.getInstance()) {
            if (DEBUG) {
                Log.v(TAG, "cacheAllThreads called");
            }
            // Keep track of what threads are now on disk so we
            // can discard anything removed from the cache.
            HashSet<Long> threadsOnDisk = new HashSet<Long>();

            // Query for all conversations.
            Cursor c = context.getContentResolver().query(sAllThreadsUri,
                    ALL_THREADS_PROJECTION, null, null, null);
            try {
                while (c.moveToNext()) {
                    long threadId = c.getLong(ID);
                    threadsOnDisk.add(threadId);

                    // Try to find this thread ID in the cache.
                    Conversation conv = Cache.get(threadId);

                    if (conv == null) {
                        // Make a new Conversation and put it in
                        // the cache if necessary.
                        conv = new Conversation(context, c, true);
                        Cache.put(conv);
                    } else {
                        // Or update in place so people with references
                        // to conversations get updated too.
                        fillFromCursor(context, conv, c, true);
                    }
                }
            } finally {
                c.close();
            }

            // Purge the cache of threads that no longer exist on disk.
            Cache.keepOnly(threadsOnDisk);
        }
    }

    private void loadFromThreadId(long threadId) {
        Cursor c = mContext.getContentResolver().query(sAllThreadsUri, ALL_THREADS_PROJECTION,
                "_id=" + Long.toString(threadId), null, null);
        try {
            if (c.moveToFirst()) {
                fillFromCursor(mContext, this, c, true);
            } else {
                throw new IllegalArgumentException("Can't find thread ID " + threadId);
            }
        } finally {
            c.close();
        }
    }
}
