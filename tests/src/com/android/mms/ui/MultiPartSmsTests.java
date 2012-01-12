/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.mms.ui.MessageListAdapter.COLUMN_ID;
import static com.android.mms.ui.MessageListAdapter.COLUMN_MSG_TYPE;

import com.android.mms.R;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.RecipientsEditor;
import android.os.SystemClock;
import android.database.Cursor;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneNumberUtils;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.view.ViewStub;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.util.Log;
import android.text.TextUtils;

/**
 * Test threads with thousands of messages
 * To run just this test:
 *       runtest --test-class=com.android.mms.ui.MultiPartSmsTests mms
 */
public class MultiPartSmsTests extends SmsTest {
    private static final String TAG = "MultiPartSmsTests";

    /* (non-Javadoc)
     * @see com.android.mms.ui.SmsTest#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // NOTE: the longer the message, the longer is takes to send and get back the
        // received message. You'll have to adjust the timeout in testLongSmsMessage().
        // I eventually paired down the message to make the test more reasonable to test.
        mMessage =
            "Is this a dagger which I see before me,"
            +" The handle toward my hand? Come, let me clutch thee."
            +" I have thee not, and yet I see thee still."
            +" Art thou not, fatal vision, sensible"
            +" To feeling as to sight? or art thou but"
            +" A dagger of the mind, a false creation,"
            +" Proceeding from the heat-oppressed brain?"
            +" I see thee yet, in form as palpable"
            +" As this which now I draw.";
//            +" Thou marshall'st me the way that I was going;"
//            +" And such an instrument I was to use."
//            +" Mine eyes are made the fools o' the other senses,"
//            +" Or else worth all the rest; I see thee still,"
//            +" And on thy blade and dudgeon gouts of blood,"
//            +" Which was not so before. There's no such thing:"
//            +" It is the bloody business which informs"
//            +" Thus to mine eyes. Now o'er the one halfworld"
//            +" Nature seems dead, and wicked dreams abuse"
//            +" The curtain'd sleep; witchcraft celebrates"
//            +" Pale Hecate's offerings, and wither'd murder,"
//            +" Alarum'd by his sentinel, the wolf,"
//            +" Whose howl's his watch, thus with his stealthy pace."
//            +" With Tarquin's ravishing strides, towards his design"
//            +" Moves like a ghost. Thou sure and firm-set earth,"
//            +" Hear not my steps, which way they walk, for fear"
//            +" Thy very stones prate of my whereabout,"
//            +" And take the present horror from the time,"
//            +" Which now suits with it. Whiles I threat, he lives:"
//            +" Words to the heat of deeds too cold breath gives."
//            +" A bell rings"
//            +" I go, and it is done; the bell invites me."
//            +" Hear it not, Duncan; for it is a knell"
//            +" That summons thee to heaven or to hell.";
    }

    /**
     * Send a a long multi-part SMS message
     */
    @LargeTest
    public void testLongSmsMessage() throws Throwable {
        assertTrue("send & receive message failed",
                sendAndReceiveMessage());
    }
}
