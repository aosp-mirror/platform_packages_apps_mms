/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.android.mms.data.Conversation;
import com.android.mms.transaction.SmsMessageSender;

/**
 * Respond to a special intent and send an SMS message without the user's intervention.
 */
public class NoConfirmationSendService extends Service {
    public static final String SEND_NO_CONFIRM_INTENT_ACTION =
        "com.android.mms.intent.action.SENDTO_NO_CONFIRMATION";
    private static final String TAG = "Mms/NoConfirmationSendService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        ComposeMessageActivity.log("NoConfirmationSendService onStartCommand");

        String action = intent.getAction();
        if (!SEND_NO_CONFIRM_INTENT_ACTION.equals(action)) {
            ComposeMessageActivity.log("NoConfirmationSendService onStartCommand wrong action: " +
                    action);
            stopSelf();
            return START_NOT_STICKY;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            ComposeMessageActivity.log("Called to send SMS but no extras");
            stopSelf();
            return START_NOT_STICKY;
        }

        String message = extras.getString(Intent.EXTRA_TEXT);

        Uri intentUri = intent.getData();
        String recipients = Conversation.getRecipients(intentUri);

        if (TextUtils.isEmpty(recipients) || TextUtils.isEmpty(message)) {
            ComposeMessageActivity.log("Recipient(s) and/or message cannot be empty");
            stopSelf();
            return START_NOT_STICKY;
        }
        String[] dests = TextUtils.split(recipients, ";");

        // Using invalid threadId 0 here. When the message is inserted into the db, the
        // provider looks up the threadId based on the recipient(s).
        long threadId = 0;
        SmsMessageSender smsMessageSender = new SmsMessageSender(this, dests,
                message, threadId);
        try {
            // This call simply puts the message on a queue and sends a broadcast to start
            // a service to send the message. In queing up the message, however, it does
            // insert the message into the DB.
            smsMessageSender.sendMessage(threadId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS message, threadId=" + threadId, e);
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
