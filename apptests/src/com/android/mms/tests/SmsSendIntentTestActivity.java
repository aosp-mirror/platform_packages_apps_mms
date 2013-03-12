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

package com.android.mms.apptests;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.mms.apptests.R;

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

    private final int NOTIFICATION_MENU = 100;
    private final int NOTIFICATIONS_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sms_send_intent_test);

        mRecipient = (EditText)findViewById(R.id.sms_recipient);
        mMessage = (EditText)findViewById(R.id.sms_content);

        String line1Number = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE))
                .getLine1Number();
        mRecipient.setText(line1Number); // use this to prime a number

        Button sendButton = (Button) findViewById(R.id.sms_send_message);
        sendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessage(1, 1);
            }
        });

        Button sendUnlockButton = (Button) findViewById(R.id.sms_send_message_unlock_screen);
        sendUnlockButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessageUnlockScreen();
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

        Button notificationsButton = (Button) findViewById(R.id.turn_off_notification_message);
        notificationsButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent intent =
                    new Intent("com.android.mms.intent.action.MESSAGING_APP_NOTIFICATIONS");
                startActivityForResult(intent, NOTIFICATIONS_REQUEST_CODE);
            }
        });
    }

    private void sendMessageUnlockScreen() {
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

        Toast.makeText(SmsSendIntentTestActivity.this, "You have five seconds to lock the screen.",
                Toast.LENGTH_SHORT).show();

        try {
            Thread.sleep(5000);     // yeah, yeah, it's on the UI thread
        } catch (Exception e) {
        }

        Uri uri = Uri.fromParts("smsto", recipient, null);
        Intent intent = new Intent("com.android.mms.intent.action.SENDTO_NO_CONFIRMATION", uri);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        intent.putExtra("exit_on_sent", true);
        intent.putExtra("showUI", true);
        startService(intent);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == NOTIFICATIONS_REQUEST_CODE) {
            String msg = "result: ";
            if (resultCode == RESULT_OK) {
                msg += "ok";
            } else if (resultCode == RESULT_CANCELED) {
                msg += "canceled";
            } else {
                msg += resultCode;
            }
            Button notificationsButton = (Button) findViewById(R.id.turn_off_notification_message);
            notificationsButton.setText(msg);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, NOTIFICATION_MENU, Menu.NONE, R.string.menu_notifications);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case NOTIFICATION_MENU:
                Intent intent =
                    new Intent("com.android.mms.intent.action.MESSAGING_APP_NOTIFICATIONS");
                startActivity(intent);
                break;
        }
        return true;
    }
}
