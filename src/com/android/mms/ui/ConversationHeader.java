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

/**
 * A holder class for a conversation header.
 */
public class ConversationHeader {
    private long mThreadId;
    private String mSubject;
    private String mDate;
    private boolean mHasAttachment;
    private boolean mIsRead;
    private boolean mHasError;
    private boolean mHasDraft;
    private int mMessageCount;

    // Guards access to both mViewWaitingForFromChange and mFrom:
    private final Object mFromLock = new Object();

    // The formatted "from" display that the user sees.  May be null
    // if the contact name(s) aren't loaded yet.
    private String mFrom;

    // the presence icon resource id displayed for the conversation thread.
    private int mPresenceResId;

    // Optional callback to run when mFrom changes.  This is used to
    // update ConversationHeaderView asynchronously.  The view registers
    // with the header using setOnFromChanged() below.
    private ConversationHeaderView mViewWaitingForFromChange;

    // Needed because it's Parcelable
    public ConversationHeader() {
    }

    public ConversationHeader(
            long threadId,
            String from,  // may be null to signal async loading
            String subject,
            String date,
            boolean isRead,
            boolean hasError,
            boolean hasDraft,
            int messageCount,
            boolean hasAttachment)
    {
        mThreadId = threadId;
        mFrom = from;  // may be null
        mPresenceResId = 0;
        mSubject = subject != null ? subject : "";
        mDate = date != null ? date : "";
        mIsRead = isRead;
        mHasError = hasError;
        mHasDraft = hasDraft;
        mMessageCount = messageCount;
        mHasAttachment = hasAttachment;
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
        synchronized (mFromLock) {
            return mFrom;
        }
    }

    public void setFrom(String from) {
        synchronized (mFromLock) {
            mFrom = from;
            conditionallyRunFromChangedCallback();
        }
    }

    public int getPresenceResourceId() {
        synchronized (mFromLock) {
            return mPresenceResId;
        }
    }

    public void setFromAndPresence(String from, int presenceResId) {
        synchronized (mFromLock) {
            mFrom = from;
            mPresenceResId = presenceResId;
            conditionallyRunFromChangedCallback();
        }
    }

    /**
     * Called by the {@link ConversationHeaderView} when it wants to
     * register for updates to the model (only the from name of which
     * is mutable.
     */
    public void setWaitingView(ConversationHeaderView headerView) {
        synchronized (mFromLock) {
            mViewWaitingForFromChange = headerView;
            conditionallyRunFromChangedCallback();
        }
    }

    private void conditionallyRunFromChangedCallback() {
        synchronized (mFromLock) {
            if (mViewWaitingForFromChange != null && mFrom != null) {
                mViewWaitingForFromChange.onHeaderLoaded(this);
                mViewWaitingForFromChange = null;
            }
        }
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
