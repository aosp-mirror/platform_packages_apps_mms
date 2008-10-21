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
    private String mFrom;
    private String mSubject;
    private String mDate;
    private boolean mHasAttachment;
    private boolean mIsRead;
    private boolean mHasError;
    private boolean mHasDraft;
    private int mMessageCount;

    // Needed because it's Parcelable
    public ConversationHeader() {
    }

    public ConversationHeader(
            long threadId,
            String from,
            String subject,
            String date,
            boolean isRead,
            boolean hasError,
            boolean hasDraft,
            int messageCount)
    {
        mThreadId = threadId;
        mFrom = from;
        mSubject = subject;
        mDate = date;
        mIsRead = isRead;
        mHasError = hasError;
        mHasDraft = hasDraft;
        mMessageCount = messageCount;
    }

    /**
     * @return Returns the ID of the thread.
     */
    public long getThreadId() {
        return mThreadId;
    }

    public void setThreadId(long threadId) {
        mThreadId = threadId;
    }

    /**
     * @return Returns the date.
     */
    public String getDate() {
        return mDate;
    }

    public void setDate(String date) {
        mDate = date;
    }

    /**
     * @return Returns the from.
     */
    public String getFrom() {
        return mFrom;
    }

    public void setFrom(String from) {
        mFrom = from;
    }

    /**
     * @return Returns the subject.
     */
    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String subject) {
        mSubject = subject;
    }

    /**
     * @return Returns the hasAttachment.
     */
    public boolean hasAttachment() {
        return mHasAttachment;
    }

    /**
     * @param hasAttachment The hasAttachment to set.
     */
    public void setHasAttachment(boolean hasAttachment) {
        mHasAttachment = hasAttachment;
    }

    /**
     * @return Returns the isRead.
     */
    public boolean isRead() {
        return mIsRead;
    }

    /**
     * @param isRead The isRead to set.
     */
    public void setRead(boolean isRead) {
        mIsRead = isRead;
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
