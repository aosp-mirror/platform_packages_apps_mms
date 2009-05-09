/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

import com.google.android.mms.util.SqliteWrapper;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.telephony.gsm.SmsMessage;
import android.util.Log;

public class MessageStatusReceiver extends BroadcastReceiver {
    public static final String MESSAGE_STATUS_RECEIVED_ACTION =
            "com.android.mms.transaction.MessageStatusReceiver.MESSAGE_STATUS_RECEIVED";
    private static final String[] ID_PROJECTION = new String[] { Sms._ID };
    private static final String LOG_TAG = "MessageStatusReceiver";
    private static final Uri STATUS_URI =
            Uri.parse("content://sms/status");
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        if (MESSAGE_STATUS_RECEIVED_ACTION.equals(intent.getAction())) {

            Uri messageUri = intent.getData();
            byte[] pdu = (byte[]) intent.getExtra("pdu");

            SmsMessage message = SmsMessage.createFromPdu(pdu);
            if (message.isStatusReportMessage()) {
                Long messageId = getMessageId(context, messageUri);
                if (messageId == -1) {
                    error("Can't find message for status update: " + messageUri);
                } else {
                    updateMessageStatus(context, messageId, message);
                    MessagingNotification.showSmsDeliveryReportIndicator(context, messageId);
                }
            } else {  //  this should not happen
                MessagingNotification.updateNewMessageIndicator(context, true);
            }
       }
    }

    private void updateMessageStatus(Context context, Long messageId, SmsMessage message) {
        // Create a "status/#" URL and use it to update the
        // message's status in the database.
        Uri updateUri = ContentUris.withAppendedId(STATUS_URI, messageId);
        int status = message.getStatus();
        ContentValues contentValues = new ContentValues(1);

        contentValues.put(Sms.STATUS, status);
        SqliteWrapper.update(context, context.getContentResolver(),
                            updateUri, contentValues, null, null);
    }

    private Long getMessageId(Context context, Uri messageUri) {
        Long result = Long.valueOf(-1);
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                messageUri, ID_PROJECTION, null, null, null);

        if ((cursor != null) && cursor.moveToFirst()) {
            result = cursor.getLong(0);
            cursor.close();
        }
        return result;
    }

    private void error(String message) {
        Log.e(LOG_TAG, "[MessageStatusReceiver] " + message);
    }
}
