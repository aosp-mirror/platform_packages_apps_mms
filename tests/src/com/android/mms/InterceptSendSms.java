/*
 * Copyright (C) 2009 The Android Open Source Project
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


package src.com.android.mms;

import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.WorkingMessage;
import com.android.mms.ui.ComposeMessageActivity;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.ActivityInstrumentationTestCase2;

import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 *
 * Junit / Instrumentation test case for testing intercepting the send sms intent just
 * like a 3rd party might want to do.
 *
 */

public class InterceptSendSms extends ActivityInstrumentationTestCase2 <ComposeMessageActivity> {
    private static String TAG = "InterceptSendSms";
    private static int WAIT_TIME = 4000; //Set the short wait time for 4 sec.
    private static String RECIPIENTS = "4258365497,4258365496";
    private static String MESSAGE = "This is a test message of intercepting a SMS";

    private InterceptSmsReceiver mInterceptReceiver;
    private TextView mRecipientsView;
    private EditText mTextEditor;
    private boolean mInterceptedSend;

    public InterceptSendSms() {
        super("com.android.mms", ComposeMessageActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        Activity activity = getActivity();
        super.setUp();
        mRecipientsView = (TextView)activity.findViewById(R.id.recipients_editor);
        mTextEditor = (EditText)activity.findViewById(R.id.embedded_text_editor);

        // Setup our receiver to listen for SMS's about to be sent.
        mInterceptReceiver = new InterceptSmsReceiver();
        IntentFilter filter = new IntentFilter(WorkingMessage.ACTION_SENDING_SMS);
        activity.registerReceiver(mInterceptReceiver, filter);
    }

    @Override
    protected void tearDown() throws Exception {
        getActivity().unregisterReceiver(mInterceptReceiver);

        super.tearDown();
    }

 // Create the object with the run() method
    Runnable runnable = new sendMms();

    class sendMms implements Runnable {
        // This method is called when the thread runs
        public void run() {
            Instrumentation inst = getInstrumentation();

            mRecipientsView.setText(RECIPIENTS);
            mTextEditor.setText(MESSAGE);

            Button mSendButton = (Button) getActivity().getWindow().findViewById(R.id.send_button);
            mSendButton.performClick();

            Log.v(TAG, "sendMms hitting send now");
            boolean messageSend = mSendButton.performClick();
            if (!messageSend) {
                assertTrue("Fails to send mms", false);
                Log.v(TAG, "messageSend is true");
            }
        }
    }

    // Send sms and see if we get a chance to handle the send in our receiver.
    @LargeTest
    public void testInterceptSendSms(){
        try{
            Instrumentation inst = getInstrumentation();

            // Send the sms message
            inst.runOnMainSync(runnable);
            Thread.sleep(WAIT_TIME);
            assertTrue("Intercepted send SMS", mInterceptedSend);
        } catch (Exception e){
            assertTrue("Failed to send sms", false);
            Log.v(TAG, e.toString());
        }
    }

    /**
     * InterceptSmsReceiver catches the NEW_SENDING_SMS broadcast from the messaging
     * app when the app is about to send a SMS message. We pretend to be an app that
     * takes over and does the sending ourself. We set the result code RESULT_OK so
     * the message app doesn't actually send the message.
     */
    public class InterceptSmsReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "doReceive: " + intent);
            mInterceptedSend = true;

            final String msgText = intent.getStringExtra(WorkingMessage.EXTRA_SMS_MESSAGE);
            final String semiSepRecipients =
                intent.getStringExtra(WorkingMessage.EXTRA_SMS_RECIPIENTS);
            final long threadId = intent.getLongExtra(WorkingMessage.EXTRA_SMS_THREAD_ID, 0);

            assertEquals(msgText, MESSAGE);
            assertEquals(semiSepRecipients, RECIPIENTS.replace(',', ';'));
            assertTrue(threadId > 0);

            // Mark that we're handling the sending of the sms.
            setResultCode(android.app.Activity.RESULT_OK);
        }
    }

}
