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


package com.android.mms;

import com.android.mms.R;
import com.android.mms.ui.ComposeMessageActivity;

import android.app.Instrumentation;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.ActivityInstrumentationTestCase2;

import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;

/**
 *
 * Junit / Instrumentation test case for mms stability test
 *
 */

public class MmsStability extends ActivityInstrumentationTestCase2 <ComposeMessageActivity> {
    private static String TAG = "MmsStability"; 
    private static int NO_OF_MESSAGE_SEND = 5; //Total number of messages
    private static String MESSAGE_CONTENT = "This is a system stability " +
                             "test for MMS. This test case send 5 message " +
                             "to the number which will reply automatically";
    private static int WAIT_TIME = 2000; //Set the short wait time for 2 sec.
    private static String RECIPIENT_NUMBER = "46645";

    public MmsStability() {
        super("com.android.mms", ComposeMessageActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        getActivity();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

 // Create the object with the run() method
    Runnable runnable = new sendMms();

    class sendMms implements Runnable {
        // This method is called when the thread runs
        public void run() {
            Instrumentation inst = getInstrumentation();

            Button mSendButton = (Button) getActivity().getWindow().findViewById(R.id.send_button);
            mSendButton.performClick();

            boolean messageSend = mSendButton.performClick();
            if (!messageSend) {
                assertTrue("Fails to send mms", false);
                Log.v(TAG, "messageSend is true");
            }
        }
    }

    // Send 5 mms to the same contact.
    @LargeTest
    public void testSend5MMS(){
        try{
            Instrumentation inst = getInstrumentation();
            //This number will send automatic reply
            inst.sendStringSync(RECIPIENT_NUMBER);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);

            for (int i = 0; i < NO_OF_MESSAGE_SEND; i++) {
                // Enter the message
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                inst.sendStringSync(MESSAGE_CONTENT);
                // Send the mms message
                inst.runOnMainSync(runnable);
                Thread.sleep(WAIT_TIME);
            }
            assertTrue("Send MMS", true);
        } catch (Exception e){
            assertTrue("Fails to send mms", false);
            Log.v(TAG, e.toString());
        }
    }

    @LargeTest
    public void testLaunchMMS() {
        // Added a do nothing test case to capture
        // the mms power usage base line.
        try {
            Thread.sleep(WAIT_TIME);
        } catch (Exception e) {
            assertTrue("MMS do nothing", false);
        }
        assertTrue("MMS do nothing", true);
    }
}
