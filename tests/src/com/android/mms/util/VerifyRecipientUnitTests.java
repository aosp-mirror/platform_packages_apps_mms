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

package com.android.mms.util;

import android.content.Context;
import android.provider.Telephony.Threads;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.mms.data.Conversation;

/**
 * This is a series of unit tests for Conversation's verifyRecipients function.
 *
 * To run just this test:
 *       runtest --test-class=com.android.mms.util.VerifyRecipientUnitTests mms
 */
@SmallTest
public class VerifyRecipientUnitTests extends AndroidTestCase {
    private long mThreadId1;
    private long mThreadId2;
    private long mThreadId3;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Context context = getContext();
        mThreadId1 = Threads.getOrCreateThreadId(context, "232-4567");
        mThreadId2 = Threads.getOrCreateThreadId(context, "flintstone_fred@goofball.org");
        mThreadId3 = Threads.getOrCreateThreadId(context, "(801) 123-4567");
    }

    /**
     * Test of verifyRecipients.
     */
    public void testVerifyRecipients() {
        assertEquals("Numbers aren't equal",
                Conversation.verifySingleRecipient(getContext(), mThreadId1, "(415) 232-4567"),
                "(415) 232-4567");

        assertEquals("Numbers aren't equal",
                Conversation.verifySingleRecipient(getContext(), mThreadId1, " 232-4567"),
                "232-4567");
    }
}
