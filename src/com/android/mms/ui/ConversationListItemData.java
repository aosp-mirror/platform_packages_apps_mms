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

import android.content.Context;

import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;

/**
 * A holder class for a conversation header.
 */
public class ConversationListItemData {
    private Conversation mConversation;
    private long mThreadId;
    private String mSubject;
    private String mDate;
    private boolean mHasAttachment;
    private boolean mIsRead;
    private boolean mHasError;
    private boolean mHasDraft;
    private int mMessageCount;

    // The recipients in this conversation
    private ContactList mRecipients;
    private String mRecipientString;

    // the presence icon resource id displayed for the conversation thread.
    private int mPresenceResId;

    public ConversationListItemData(Context context, Conversation conv) {
        mConversation = conv;
        mThreadId = conv.getThreadId();
        mPresenceResId = 0;
        mSubject = conv.getSnippet();
        mDate = MessageUtils.formatTimeStampString(context, conv.getDate());
        mIsRead = !conv.hasUnreadMessages();
        mHasError = conv.hasError();
        mHasDraft = conv.hasDraft();
        mMessageCount = conv.getMessageCount();
        mHasAttachment = conv.hasAttachment();
        updateRecipients();
    }

    public void updateRecipients() {
        mRecipients = mConversation.getRecipients();
        mRecipientString = mRecipients.formatNames(", ");
    }

    /**
     * @return Returns the ID of the thread.
     */
    public long getThreadId() {
        return mThreadId;
    }

    /**
     * @return Returns the date.
     */
    public String getDate() {
        return mDate;
    }

    /**
     * @return Returns the from.  (formatted for display)
     */
    public String getFrom() {
        return mRecipientString;
    }

    public ContactList getContacts() {
        return mRecipients;
    }

    public int getPresenceResourceId() {
        return mPresenceResId;
    }

    /**
     * @return Returns the subject.
     */
    public String getSubject() {
        return mSubject;
    }

    /**
     * @return Returns the hasAttachment.
     */
    public boolean hasAttachment() {
        return mHasAttachment;
    }

    /**
     * @return Returns the isRead.
     */
    public boolean isRead() {
        return mIsRead;
    }

    /**
     * @return Whether the thread has a transmission error.
     */
    public boolean hasError() {
        return mHasError;
    }

    /**
     * @return Whether the thread has a draft.
     */
    public boolean hasDraft() {
        return mHasDraft;
    }

    /**
     * @return message count of the thread.
     */
    public int getMessageCount() {
        return mMessageCount;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "[ConversationHeader from:" + getFrom() + " subject:" + getSubject()
        + "]";
    }
}
