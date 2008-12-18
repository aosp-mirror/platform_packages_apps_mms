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

package com.android.mms.transaction;

import com.android.mms.ui.MessagingPreferenceActivity;
import com.google.android.mms.MmsException;
import com.google.android.mms.util.SqliteWrapper;

import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Sms.Conversations;
import android.telephony.gsm.SmsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class SmsMessageSender implements MessageSender {
    private final Context mContext;
    private final int mNumberOfDests;
    private final String[] mDests;
    private final String mMessageText;
    private final String mServiceCenter;
    private final long mThreadId;
    private long mTimestamp;
    
    // Default preference values
    private static final boolean DEFAULT_DELIVERY_REPORT_MODE  = false;

    private static final String[] SERVICE_CENTER_PROJECTION = new String[] {
        Sms.Conversations.REPLY_PATH_PRESENT,
        Sms.Conversations.SERVICE_CENTER,
    };

    private static final String[] DATE_PROJECTION = new String[] {
        Sms.DATE
    };
    
    private static final int COLUMN_REPLY_PATH_PRESENT = 0;
    private static final int COLUMN_SERVICE_CENTER     = 1;

    public SmsMessageSender(Context context, String[] dests, String msgText,
            long threadId) {
        mContext = context;
        mMessageText = msgText;
        mNumberOfDests = dests.length;
        mDests = new String[mNumberOfDests];
        System.arraycopy(dests, 0, mDests, 0, mNumberOfDests);
        mTimestamp = System.currentTimeMillis();
        mThreadId = threadId > 0 ? threadId
                        : Threads.getOrCreateThreadId(context,
                                    new HashSet<String>(Arrays.asList(dests)));
        mServiceCenter = getOutgoingServiceCenter(mThreadId);
    }

    public boolean sendMessage(long token) throws MmsException {
        if ((mMessageText == null) || (mNumberOfDests == 0)) {
            // Don't try to send an empty message.
            throw new MmsException("Null message body or dest.");
        }

        SmsManager smsManager = SmsManager.getDefault();

        for (int i = 0; i < mNumberOfDests; i++) {
            ArrayList<String> messages = smsManager.divideMessage(mMessageText);
            int messageCount = messages.size();
            ArrayList<PendingIntent> deliveryIntents =
                    new ArrayList<PendingIntent>(messageCount);
            ArrayList<PendingIntent> sentIntents =
                    new ArrayList<PendingIntent>(messageCount);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            boolean requestDeliveryReport = prefs.getBoolean(
                    MessagingPreferenceActivity.SMS_DELIVERY_REPORT_MODE,
                    DEFAULT_DELIVERY_REPORT_MODE);
            Uri uri = null;
            try {
                uri = Sms.Outbox.addMessage(mContext.getContentResolver(), mDests[i],
                            mMessageText, null, mTimestamp, requestDeliveryReport, mThreadId);
            } catch (SQLiteException e) {
                SqliteWrapper.checkSQLiteException(mContext, e);
            }

            for (int j = 0; j < messageCount; j++) {
                if (requestDeliveryReport) {
                    // TODO: Fix: It should not be necessary to
                    // specify the class in this intent.  Doing that
                    // unnecessarily limits customizability.
                    deliveryIntents.add(PendingIntent.getBroadcast(
                            mContext, 0,
                            new Intent(
                                    MessageStatusReceiver.MESSAGE_STATUS_RECEIVED_ACTION,
                                    uri,
                                    mContext,
                                    MessageStatusReceiver.class),
                            0));
                }
                sentIntents.add(PendingIntent.getBroadcast(
                        mContext, 0,
                        new Intent(SmsReceiverService.MESSAGE_SENT_ACTION,
                                uri,
                                mContext,
                                SmsReceiver.class),
                        0));
            }
            smsManager.sendMultipartTextMessage(
                    mDests[i], mServiceCenter, messages, sentIntents,
                    deliveryIntents);
        }
        return false;
    }

    /**
     * Get the service center to use for a reply.
     *
     * The rule from TS 23.040 D.6 is that we send reply messages to
     * the service center of the message to which we're replying, but
     * only if we haven't already replied to that message and only if
     * <code>TP-Reply-Path</code> was set in that message.
     *
     * Therefore, return the service center from the most recent
     * message in the conversation, but only if it is a message from
     * the other party, and only if <code>TP-Reply-Path</code> is set.
     * Otherwise, return null.
     */
    private String getOutgoingServiceCenter(long threadId) {
        Cursor cursor = null;

        try {
            cursor = SqliteWrapper.query(mContext, mContext.getContentResolver(),
                            Sms.CONTENT_URI, SERVICE_CENTER_PROJECTION,
                            "thread_id = " + threadId, null, "date DESC");

            if ((cursor == null) || !cursor.moveToFirst()) {
                return null;
            }

            boolean replyPathPresent = (1 == cursor.getInt(COLUMN_REPLY_PATH_PRESENT));
            return replyPathPresent ? cursor.getString(COLUMN_SERVICE_CENTER) : null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
