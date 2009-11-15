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

package com.android.mms.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.telephony.PhoneNumberUtils;
import com.android.mms.LogTag;

public class PhoneNumberComparisonTest extends AndroidTestCase {

    @SmallTest
    public void testCompareSmsShortcode() {
        Log.i(LogTag.APP, "testCompareSmsShortcode");

        // test the short codes themselves are compared correctly
        assertFalse(PhoneNumberUtils.compare("321", "54321"));
        assertFalse(PhoneNumberUtils.compare("4321", "54321"));
        assertFalse(PhoneNumberUtils.compare("54321", "654321"));

        // test comparing one shortcode to a regular phone number
        assertFalse(PhoneNumberUtils.compare("54321", "6505554321"));
        assertFalse(PhoneNumberUtils.compare("54321", "+16505554321"));
        assertFalse(PhoneNumberUtils.compare("654321", "6505654321"));
        assertFalse(PhoneNumberUtils.compare("654321", "+16505654321"));
    }


}