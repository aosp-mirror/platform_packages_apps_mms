package com.android.mms.data;

import java.util.HashSet;
import java.util.Iterator;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.Telephony.Threads;
import android.text.TextUtils;
import android.util.Log;

import com.android.mms.transaction.MessagingNotification;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.RecipientList;
import com.android.mms.ui.RecipientList.Recipient;
import com.android.mms.util.DraftCache;

/**
 * An interface for finding information about conversations and/or creating new ones.
 */
public class Conversation {
    private static final String TAG = "Conversation";

    private final Context mContext;

    // The current set of recipients for this conversation.
    private RecipientList mRecipients;
    
    // The thread ID of this conversation.  Can be zero in the case of a
    // new conversation where the recipient set is changing as the user
    // types and we have not hit the database yet to create a thread.
    private long mThreadId;
    
    private Conversation(Context context) {
        mContext = context;
    }
    
    /**
     * Create a new conversation with no recipients.  {@link setRecipients} can
     * be called as many times as you like; the conversation will not be
     * created in the database until {@link ensureThreadId} is called.
     */
    public static Conversation createNew(Context context) {
        Conversation conv = new Conversation(context);
        conv.mRecipients = new RecipientList();
        conv.mThreadId = 0;
        return conv;
    }

    /**
     * Find the conversation matching the provided thread ID.
     */
    public static Conversation get(Context context, long threadId) {
        Conversation conv = new Conversation(context);
        conv.mThreadId = threadId;
        
        String recipients = MessageUtils.getAddressByThreadId(context, threadId);
        conv.mRecipients = RecipientList.from(recipients, context);
        
        return conv;
    }
    
    /**
     * Find the conversation matching the provided recipient set.
     * When called with an empty recipient list, equivalent to {@link createEmpty}.
     */
    public static Conversation get(Context context, RecipientList recipients) {
        // If there are no recipients in the list, make a new conversation.
        if (recipients.size() < 1) {
            return createNew(context);
        }

        Conversation conv = new Conversation(context);
        conv.mRecipients = recipients;
        conv.mThreadId = getOrCreateThreadId(context, recipients);
        
        return conv;
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
        
        // Handle a conversation URI
        if (uri.getPathSegments().size() >= 2) {
            try {
                long threadId = Long.parseLong(uri.getPathSegments().get(1));
                return Conversation.get(context, threadId);
            } catch (NumberFormatException exception) {
                Log.e(TAG, "Invalid URI: " + uri);
            }
        }
        
        String recipient = uri.getSchemeSpecificPart();
        RecipientList list = RecipientList.from(recipient, context);
        return get(context, list);
    }

    /**
     * Marks all messages in this conversation as read and updates
     * relevant notifications.  This method returns immediately;
     * work is dispatched to a background thread.
     */
    public void markAsRead() {
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
    public Uri getUri() {
        if (mThreadId <= 0)
            return null;
        
        return ContentUris.withAppendedId(Threads.CONTENT_URI, mThreadId);
    }
    
    /**
     * Returns the thread ID of this conversation.  Can be zero if
     * {@link ensureThreadId} has not been called yet.
     */
    public long getThreadId() {
        return mThreadId;
    }

    /**
     * Guarantees that the conversation has been created in the database.
     * This will make a blocking database call if it hasn't.
     * 
     * @return The thread ID of this conversation in the database
     */
    public long ensureThreadId() {
        if (mThreadId <= 0) {
            mThreadId = getOrCreateThreadId(mContext, mRecipients);
        }
        
        return mThreadId;
    }
    
    /**
     * Sets the list of recipients associated with this conversation.
     * If called, {@link ensureThreadId} must be called before the next
     * operation that depends on this conversation existing in the
     * database (e.g. storing a draft message to it).
     */
    public void setRecipients(RecipientList list) {
        mRecipients = list;

        // Invalidate thread ID because the recipient set has changed.
        mThreadId = 0;
    }
    
    /**
     * Returns the recipient set of this conversation.
     */
    public RecipientList getRecipients() {
        return mRecipients;
    }
    
    /**
     * Returns true if this conversation has only one recipient.
     */
    public boolean isSingleRecipient() {
        return mRecipients.size() == 1;
    }

    /**
     * Returns true if a draft message exists in this conversation.
     */
    public boolean hasDraft() {
        if (mThreadId <= 0)
            return false;
        
        return DraftCache.getInstance().hasDraft(mThreadId);
    }
    
    /**
     * Sets whether or not this conversation has a draft message.
     */
    public void setDraftState(boolean hasDraft) {
        if (mThreadId <= 0)
            return;
        
        DraftCache.getInstance().setDraftState(mThreadId, hasDraft);
    }
    
    private static long getOrCreateThreadId(Context context, RecipientList list) {
        HashSet<String> recipients = new HashSet<String>();
        Iterator<Recipient> iter = list.iterator();
        while (iter.hasNext()) {
            Recipient r = iter.next();
            if (!TextUtils.isEmpty(r.number)) {
                recipients.add(r.number);
            }
        }
        return Threads.getOrCreateThreadId(context, recipients);
    }

    @Override
    public String toString() {
        return String.format("[%s] (tid %d)", mRecipients.serialize(), mThreadId);
    }

}
