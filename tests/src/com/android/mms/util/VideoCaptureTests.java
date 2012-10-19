/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.mms.ui.MessageUtils;

/**
 * Unit tests for Video Capture utilities.
 *
 * To run the test:
 *    runtest --test-class=com.android.mms.util.VideoCaptureTests mms
 */
@SmallTest
public class VideoCaptureTests extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Test the function that computes rounded video record times.
     */
    public void testVideoCaptureDurationLimit() {
        assertEquals(MessageUtils.getVideoCaptureDurationLimit(0), 0);          // 0 -> 0 secs
        assertEquals(MessageUtils.getVideoCaptureDurationLimit(100), 0);        // 0 -> 0 secs
        assertEquals(MessageUtils.getVideoCaptureDurationLimit(500000), 20);    // 28 -> 20 secs
        assertEquals(MessageUtils.getVideoCaptureDurationLimit(1000000), 50);   // 57 -> 50 secs
        assertEquals(MessageUtils.getVideoCaptureDurationLimit(10000000), 120); // 570 -> 120 secs
    }
}
