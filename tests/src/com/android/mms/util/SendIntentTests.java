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

import android.content.Intent;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

/**
 * Unit tests for the com.android.mms.intent.action.SENDTO_NO_CONFIRMATION intent.
 *
 * To run the test:
 *    runtest --test-class=com.android.mms.util.SendIntentTests mms
 */
@SmallTest
public class SendIntentTests extends AndroidTestCase {

    /**
     * This tests verifies that we have to have android.permission.SEND_SMS to send this intent.
     */
    public void testPermissionRequired() {
        testAndroidTestCaseSetupProperly();     // verify we have a context

        Uri uri = Uri.fromParts("smsto", "650-933-0884", null);
        Intent intent = new Intent("com.android.mms.intent.action.SENDTO_NO_CONFIRMATION", uri);
        intent.putExtra(Intent.EXTRA_TEXT, "This is a test");
        getContext().startService(intent);
        Log.d("SendIntentTests", "Started service for intent: " + intent);
    }
}
