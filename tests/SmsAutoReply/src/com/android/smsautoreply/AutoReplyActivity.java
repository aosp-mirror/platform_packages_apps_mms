/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.smsautoreply;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A sms reply activity which auto-replies the received sms message to the sender
 * This is used as the receiver for 1:M sms stress test.
 * Keep the app in the foreground when running the test.
 */
public class AutoReplyActivity extends Activity {
    private static final String TAG = "AutoReplyActivity";
    private static final String LOG_FILE = "received_sms_log.txt";
    private SmsMessageReceiver mSmsMsgReceiver = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mSmsMsgReceiver = new SmsMessageReceiver();
        registerReceiver(mSmsMsgReceiver,
                new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
    }

    private class SmsMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras == null)
                return;

            Object[] pdus = (Object[]) extras.get("pdus");

            for (int i = 0; i < pdus.length; i++) {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) pdus[i]);
                Log.d(TAG, String.format("SMS received from %s, body: %s",
                        message.getOriginatingAddress(), message.getMessageBody()));
                logMessage(message);
                replyMessage(context, message);
            }
        }

        // Log received sms message into an output file
        private void logMessage(SmsMessage msg) {
            File logFile = new File(Environment.getExternalStorageDirectory(), LOG_FILE);

            String logMsg = String.format("SMS: from: %s body: %s",
                    msg.getOriginatingAddress(),msg.getMessageBody());
            try {
                String currentDateTimeString =
                    new SimpleDateFormat("MM-dd HH:mm:ss.SSS").format(new Date());
                logMsg = String.format("%s: %s\n", currentDateTimeString, logMsg);
                FileOutputStream fos = new FileOutputStream(logFile, true);
                fos.write(logMsg.getBytes());
                fos.flush();
                fos.close();
            } catch (IOException ioe) {
                Log.e(TAG, "failed to log SMS", ioe);
                Log.d(TAG, logMsg);
            }
        }

        private void replyMessage(Context context, SmsMessage msg) {
            SmsManager sms = SmsManager.getDefault();
            String message = msg.getMessageBody();
            sms.sendTextMessage(msg.getOriginatingAddress(), null, message, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSmsMsgReceiver != null) {
            unregisterReceiver(mSmsMsgReceiver);
        }
    }
}
