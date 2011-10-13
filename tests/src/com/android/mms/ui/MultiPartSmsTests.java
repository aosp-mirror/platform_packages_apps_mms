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
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.view.ViewStub;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.util.Log;


/**
 * Test threads with thousands of messages
 * To run just this test:
 *       runtest --test-class=com.android.mms.ui.MultiPartSmsTests mms
 */
public class MultiPartSmsTests
extends ActivityInstrumentationTestCase2<ComposeMessageActivity> {

    private static final String TAG = "MultiPartSmsTests";
    private static final int SMS_RECEIVE_TIMER = 5 * 60 * 1000; //5 minutes;
    private ComposeMessageActivity mActivity = null;
    private RecipientsEditor mRecipientsEditor;
    private EditText mTextEditor;

    // NOTE: the longer the message, the longer is takes to send and get back the
    // received message. You'll have to adjust the timeout in testLongSmsMessage().
    // I eventually pared down the message to make the test more reasonable to test.
    final String mLongMessage =
        "Is this a dagger which I see before me,"
        +" The handle toward my hand? Come, let me clutch thee."
        +" I have thee not, and yet I see thee still."
        +" Art thou not, fatal vision, sensible"
        +" To feeling as to sight? or art thou but"
        +" A dagger of the mind, a false creation,"
        +" Proceeding from the heat-oppressed brain?"
        +" I see thee yet, in form as palpable"
        +" As this which now I draw.";
//        +" Thou marshall'st me the way that I was going;"
//        +" And such an instrument I was to use."
//        +" Mine eyes are made the fools o' the other senses,"
//        +" Or else worth all the rest; I see thee still,"
//        +" And on thy blade and dudgeon gouts of blood,"
//        +" Which was not so before. There's no such thing:"
//        +" It is the bloody business which informs"
//        +" Thus to mine eyes. Now o'er the one halfworld"
//        +" Nature seems dead, and wicked dreams abuse"
//        +" The curtain'd sleep; witchcraft celebrates"
//        +" Pale Hecate's offerings, and wither'd murder,"
//        +" Alarum'd by his sentinel, the wolf,"
//        +" Whose howl's his watch, thus with his stealthy pace."
//        +" With Tarquin's ravishing strides, towards his design"
//        +" Moves like a ghost. Thou sure and firm-set earth,"
//        +" Hear not my steps, which way they walk, for fear"
//        +" Thy very stones prate of my whereabout,"
//        +" And take the present horror from the time,"
//        +" Which now suits with it. Whiles I threat, he lives:"
//        +" Words to the heat of deeds too cold breath gives."
//        +" A bell rings"
//        +" I go, and it is done; the bell invites me."
//        +" Hear it not, Duncan; for it is a knell"
//        +" That summons thee to heaven or to hell.";
    private String mMyNumber;

    public MultiPartSmsTests() {
        super(ComposeMessageActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mActivity = getActivity();
        ViewStub stub = (ViewStub)mActivity.findViewById(R.id.recipients_editor_stub);
        if (stub != null) {
            View stubView = stub.inflate();
            mRecipientsEditor = (RecipientsEditor) stubView.findViewById(R.id.recipients_editor);
        } else {
            mRecipientsEditor = (RecipientsEditor)mActivity.findViewById(R.id.recipients_editor);
            mRecipientsEditor.setVisibility(View.VISIBLE);
        }
        mTextEditor = (EditText)mActivity.findViewById(R.id.embedded_text_editor);

        mMyNumber = MessageUtils.getLocalNumber();
        assertNotNull("null number for this phone", mMyNumber);
        // WARNING: MessageUtils.getLocalNumber returned some 206 number as the number
        // of this phone, which is totally the wrong area code. Therefore, the test
        // ended up failing because it sent a gigantic message to some unknown number
        // and never received the number back. For now, I'm just hardwiring the number
        // of my phone.
//        mMyNumber = "6502782055";
        mMyNumber = "6509330537";
    }

    private abstract class MessageRunnable implements Runnable {
        protected String mRecipient;

        public void setRecipient(String recipient) {
            mRecipient = recipient;
        }
    }

    private MessageRunnable mSendSmsMessage = new MessageRunnable() {
        public void run() {
            // only on the first message will there be a recipients editor
            if (mRecipientsEditor.getVisibility() == View.VISIBLE) {
                mRecipientsEditor.setText(mRecipient);
            }
            mTextEditor.setText(mLongMessage);
            ImageButton send = (ImageButton)mActivity.findViewById(R.id.send_button_sms);
            send.performClick();
        }
    };

    private void sleep(long sleepTime) {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {}
    }

    /**
     * Send a a long multi-part SMS message
     */
    @LargeTest
    public void testLongSmsMessage() throws Throwable {
        final ComposeMessageActivity a = getActivity();
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mActivity.initialize(0);
                mActivity.loadMessageContent();
            }
        });

        // wait 5 seconds for the activity to run on UI thread and
        // mMsgListAdapter get updated with latest information.
        sleep(5 * 1000);
        int msgCount = mActivity.mMsgListAdapter.getCount();
        Log.v(TAG, "msgCount: " + msgCount);
        // Send out message to the recipient
        mSendSmsMessage.setRecipient(mMyNumber);
        runTestOnUiThread(mSendSmsMessage);

        // Wait for maximum 5 minutes to send the long message
        // and then receive it. Make sure the sent and received messages compare the same.
        boolean received = false;
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) <= SMS_RECEIVE_TIMER) {
            sleep( 5 * 1000);     // wait 5 seconds between checks
            Log.v(TAG, "Message Count: " + mActivity.mMsgListAdapter.getCount());
            if (msgCount + 2 == mActivity.mMsgListAdapter.getCount()) {
                // The "msgCount + 2" is to account for the sent and received message.
                // Other cases: 1) fail to send/receive sms message, test fail
                // 2) another message could be received by the target phone during this time
                //    test will falsely fail
                Cursor cursor = mActivity.mMsgListAdapter.getCursor();
                cursor.moveToLast();
                String type = cursor.getString(COLUMN_MSG_TYPE);
                long msgId = cursor.getLong(COLUMN_ID);
                MessageItem msgItem = mActivity.mMsgListAdapter.getCachedMessageItem(type, msgId, cursor);
                assertNotNull("got a null last MessageItem", msgItem);
                assertEquals("The sent and received messages aren't the same",
                        mLongMessage,
                        msgItem.mBody);
                received = true;
                break;
            }
        }
        assertTrue("Never received the sent message", received);
    }
}
