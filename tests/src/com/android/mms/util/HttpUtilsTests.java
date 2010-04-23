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

import java.util.Locale;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.mms.transaction.HttpUtils;

/**
 * Unit tests for HttpUtils.
 *
 * To run the test:
 *    runtest --test-class=com.android.mms.util.HttpUtilsTests mms
 */
@SmallTest
public class HttpUtilsTests extends AndroidTestCase {

    /**
     * This tests the standard behavior of HttpUtils.getCurrentAcceptLanguage with the
     * default locale.
     */
    public void testDefaultAcceptLanguage() {
        Locale defaultLocale = Locale.getDefault();
        String curAcceptLang = HttpUtils.getCurrentAcceptLanguage(defaultLocale);

        assertTrue("Accept language code doesn't match language",
                curAcceptLang.startsWith(defaultLocale.getLanguage()));
    }

    /**
     * This tests the  behavior of HttpUtils.getCurrentAcceptLanguage with a
     * deprecated language code. Check to make sure we're getting back the new language code.
     */
    public void testDeprecatedLanguage() {
        Locale hebrewLocale = new Locale("iw", "IW");
        String curAcceptLang = HttpUtils.getCurrentAcceptLanguage(hebrewLocale);

        assertTrue("Hewbrew language code wasn't converted to 'he'",
                curAcceptLang.startsWith("he"));
    }

}
