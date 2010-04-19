/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.mms.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import com.android.mms.transaction.SmsReceiverService;

/**
 * Unit tests for SmsReceiverService.
 *
 * To run the test:
 *    runtest --test-class=com.android.mms.util.SmsReceiverServiceTests mms
 */
@SmallTest
public class SmsReceiverServiceTests extends AndroidTestCase {

    final String mTestMessage =
        "This is a formfeed\fthat should get converted to a linefeed";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * This tests the behavior of received messages containing formfeeds are correctly
     * converted into linefeeds.
     */
    public void testFormFeeds() {
        String ffString = "Test\fmessage\fwith\fform\ffeeds";
        String lfString = "Test\nmessage\nwith\nform\nfeeds";
        String fixedMsg = SmsReceiverService.replaceFormFeeds(ffString);
        assertEquals("Messages with form feeds not coverted to line feeds",
                fixedMsg, lfString);
    }

}
