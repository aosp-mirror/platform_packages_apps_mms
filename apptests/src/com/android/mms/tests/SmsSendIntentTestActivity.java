/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.mms.tests;

import java.util.List;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mms.tests.R;

// This is a manual test application for testing the Messaging app's ability to send Sms messages
// without the user having to confirm or press a send button. This app uses the intent:
//   com.android.mms.intent.action.SENDTO_NO_CONFIRMATION
// to tell the messaging app to send a message. This app tests that the required permissions
// are checked. It also has buttons for testing sending a long (i.e. greater than 140 char) message
// and for sending a number of messages in rapid fire succession.

public class SmsSendIntentTestActivity extends Activity {
    /** Tag string for our debug logs */
    private static final String TAG = "SmsSendIntentTestActivity";
    private EditText mRecipient;
    private EditText mMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sms_send_intent_test);

        mRecipient = (EditText)findViewById(R.id.sms_recipient);
        mMessage = (EditText)findViewById(R.id.sms_content);

        mRecipient.setText("650-278-2055"); // use this to prime a number

        Button sendButton = (Button) findViewById(R.id.sms_send_message);
        sendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessage(1, 1);
            }
        });

        Button sendMultiButton = (Button) findViewById(R.id.sms_send_multi_message);
        sendMultiButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessage(5, 1);
            }
        });

        Button sendLongButton = (Button) findViewById(R.id.sms_send_long_message);
        sendLongButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessage(1, 10);
            }
        });

        Button primeButton = (Button) findViewById(R.id.sms_prime_message);
        primeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mMessage.setText(R.string.sms_long_message);
            }
        });
    }

    private void sendMessage(int count, int dupeCount) {
        String recipient = mRecipient.getText().toString();
        if (TextUtils.isEmpty(recipient)) {
            Toast.makeText(SmsSendIntentTestActivity.this, "Please enter a message recipient.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String message = mMessage.getText().toString();
        if (TextUtils.isEmpty(message)) {
            Toast.makeText(SmsSendIntentTestActivity.this, "Please enter a message body.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (dupeCount > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dupeCount; i++) {
                sb.append(i).append(": ").append(message).append(' ');
            }
            message = sb.toString();
        }
        Uri uri = Uri.fromParts("smsto", recipient, null);
        for (int i = 0; i < count; i++) {
            Intent intent = new Intent("com.android.mms.intent.action.SENDTO_NO_CONFIRMATION", uri);
            intent.putExtra(Intent.EXTRA_TEXT, message);
            startService(intent);
        }
    }
}
