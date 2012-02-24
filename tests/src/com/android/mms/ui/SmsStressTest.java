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

package com.android.mms.ui;

import android.os.Environment;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Sms stress test. Send muliptle sms each with multiple segments.
 * To run this test
 * adb shell am instrument -e class com.android.mms.ui.SmsStressTest
 *  -w com.android.mms.tests/com.android.mms.SmsTestRunner
 */
public class SmsStressTest extends SmsTest {
    private final static String TAG = "SmsStressTest";
    private final static String OUTPUT = "result.txt";
    private long mSendInterval = 10 * 1000; // 10 seconds
    protected int mIteration = 100;
    protected BufferedWriter mWriter = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Get input value for this test
        if (mInst.mNumberMessages > 0) {
            mIteration = mInst.mNumberMessages;
        }
        if (mInst.mSendInterval > 0) {
            mSendInterval = mInst.mSendInterval;
        }
        Log.v(TAG, String.format("mIteration: %d, mSendInterval: %d",
                                 mIteration, mSendInterval));
        mWriter = new BufferedWriter(new FileWriter(new File(
            Environment.getExternalStorageDirectory(), OUTPUT), true));
    }

    @Override
    protected void tearDown() throws Exception {
        Log.v(TAG, "tearDown");
        if (mWriter != null) {
            mWriter.close();
        }
        super.tearDown();
    }

    /**
     * Sending multiple sms over a single thread
     */
    @LargeTest
    public void testMultiMessageOverSingleThread() throws Throwable {
        int i;
        for (i = 0; i < mIteration; i++) {
            Log.v(TAG, "iteration: " + i);
            assertTrue("send & receive message failed",
                    sendAndReceiveMessage());
            sleep(mSendInterval);
            mWriter.write(String.format("send message %d out of %d\n",
                                        (i+1), mIteration));
        }
    }
}
