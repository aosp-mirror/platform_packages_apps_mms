/*
 * Copyright (C) 2011, The Android Open Source Project
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

import android.os.Bundle;
import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;

import com.android.mms.ui.MultiPartSmsTests;
import com.android.mms.ui.SmsStressTest;

import junit.framework.TestSuite;

/**
 * TestRunner for Sms tests
 * To run the test type command
 * adb shell am instrument -e recipient 6509339530 -e messages 10
 * -e messagefile words -e recipientfile recipients -e receivetimer 180
 * -e sendinterval 10 -w com.android.mms.tests/com.android.mms.SmsTestRunner
 */
public class SmsTestRunner extends InstrumentationTestRunner{
    // a single recipient, default is the local number
    public String mRecipient = null;
    // number of messages to send
    public int mNumberMessages = 0;
    // file used to store a message (under /data/data/com.android.mms/files/)
    public String mMessageFileName = null;
    // file to store recipients separated by comma (/data/data/com.android.mms/files/)
    public String mRecipientFileName = null;
    // timer (in ms) to wait before checking receiving message
    public long mReceiveTimer = 0;
    // time interval (in ms) between two consecutive messages
    public long mSendInterval = 0;

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new InstrumentationTestSuite(this);
        // create a test suite
        suite.addTestSuite(MultiPartSmsTests.class);
        suite.addTestSuite(SmsStressTest.class);
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return SmsTestRunner.class.getClassLoader();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // parse all input arguments
        String recipientPhoneNumber = (String) icicle.get("recipient");
        if (recipientPhoneNumber != null) {
            mRecipient = recipientPhoneNumber;
        }
        String numMsgStr = (String) icicle.get("messages");
        if (numMsgStr != null) {
            mNumberMessages = Integer.parseInt(numMsgStr);
        }
        String msgFileNameStr = (String) icicle.get("messagefile");
        if (msgFileNameStr != null) {
            mMessageFileName = msgFileNameStr;
        }
        String recpFileNameStr = (String) icicle.get("recipientfile");
        if (recpFileNameStr != null) {
            mRecipientFileName = recpFileNameStr;
        }
        // user input is by seconds, convert to ms
        String receiveTimerStr = (String) icicle.get("receivetimer");
        if (receiveTimerStr != null) {
            mReceiveTimer = (long)1000 * Integer.parseInt(receiveTimerStr);
        }
        // user input is by seconds, convert to ms
        String sendIntervalStr = (String) icicle.get("sendinterval");
        if (sendIntervalStr != null) {
            mSendInterval = (long)1000 * Integer.parseInt(sendIntervalStr);
        }
    }
}
