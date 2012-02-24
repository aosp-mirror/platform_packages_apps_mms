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

import static com.android.mms.ui.MessageListAdapter.COLUMN_ID;
import static com.android.mms.ui.MessageListAdapter.COLUMN_MSG_TYPE;

import com.android.mms.R;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.RecipientsEditor;
import com.android.mms.SmsTestRunner;

import android.database.Cursor;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewStub;
import android.widget.EditText;
import android.widget.ImageButton;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Base class for sms tests.
 */
public class SmsTest
    extends ActivityInstrumentationTestCase2<ComposeMessageActivity> {

    private final static String TAG = "SmsTest";
    protected ComposeMessageActivity mActivity;
    protected RecipientsEditor mRecipientsEditor;
    protected EditText mTextEditor;
    protected SmsTestRunner mInst;
    protected String mRecipient;
    protected List mRecipientsList = null;
    protected long mReceiveTimer = 5 * 60 * 1000 ; // 5 minutes

    // default message to sent
    protected String mMessage =
        "Is this a dagger which I see before me,"
        +" The handle toward my hand? Come, let me clutch thee."
        +" I have thee not, and yet I see thee still."
        +" Art thou not, fatal vision, sensible"
        +" To feeling as to sight? or art thou but"
        +" A dagger of the mind, a false creation,"
        +" Proceeding from the heat-oppressed brain?"
        +" I see thee yet, in form as palpable"
        +" As this which now I draw.";

    protected Long mThreadId;

    public SmsTest() {
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
            mRecipientsEditor = (RecipientsEditor) mActivity.findViewById(R.id.recipients_editor);
            mRecipientsEditor.setVisibility(View.VISIBLE);
        }
        mTextEditor = (EditText)mActivity.findViewById(R.id.embedded_text_editor);

        // parse input argument
        mInst = (SmsTestRunner)getInstrumentation();
        if (mInst.mRecipient != null) {
            mRecipient = mInst.mRecipient;
        } else {
            mRecipient = getLocalNumber();
        }
        if (mInst.mReceiveTimer > 0) {
            mReceiveTimer = mInst.mReceiveTimer;
        }
        loadRecipientsList();
        loadMessage();
        Log.v(TAG, String.format("mReceiveTimer: %d, mRecipient: %s, mMessage: ",
                                 mReceiveTimer, mRecipient, mMessage));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Load recipients from a file
     */
    private void loadRecipientsList() {
        String recipientFileName = mInst.mRecipientFileName;
        if (recipientFileName == null) {
            return;
        }
        // Read recipients from a file
        mRecipientsList = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        try {
            Log.v(TAG, "Loading recipients");
            FileInputStream f = mInst.getTargetContext().openFileInput(recipientFileName);
            int c;
            while ((c = f.read()) != -1) {
                if (c == '\r' || c == '\n' || c == ',') {
                    String recipient = sb.toString().trim();
                    if (recipient.length() > 0) {
                        mRecipientsList.add(recipient);
                    }
                    sb.setLength(0);
                } else {
                    sb.append((char)c);
                }
            }
            f.close();
        } catch (Exception e) {
            Log.e(TAG, "can't open recipients file " + recipientFileName);
            return;
        }
    }

    /**
     * Load messages from a file, save the message in mMessage
     */
    private void loadMessage() {
        String messageFileName = mInst.mMessageFileName;
        if (messageFileName == null) {
            return;
        }

        Context targetAppContext = mInst.getTargetContext().getApplicationContext();
        String filePath = String.format("%s/%s", targetAppContext.getFilesDir(), messageFileName);
        Log.v(TAG, "filePath: " + filePath);
        // Read messages from a file
        byte[] buffer = new byte[(int) new File(filePath).length()];
        BufferedInputStream bf = null;
        int numStrs = 0;
        try {
            Log.v(TAG, "Loading messages");
            bf = new BufferedInputStream(
                    mInst.getTargetContext().openFileInput(messageFileName));
            numStrs = bf.read(buffer);
        } catch (Exception e) {
            Log.e(TAG, "can't open message file at " +
                    targetAppContext.getFileStreamPath(messageFileName));
        } finally {
            if (bf != null) {
                try { bf.close(); } catch (IOException e) {
                    Log.v(TAG, "failed to close message file: " +
                            targetAppContext.getFileStreamPath(messageFileName));
                }
            }
        }
        if (numStrs > 0) {
            mMessage = new String(buffer);
        }
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
            mTextEditor.setText(mMessage);
            ImageButton send = (ImageButton)mActivity.findViewById(R.id.send_button_sms);
            send.performClick();
        }
    };

    protected void sleep(long sleepTime) {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {}
    }

    /**
     * @return the local number for this test device
     */
    protected String getLocalNumber() {
        return MessageUtils.getLocalNumber();
    }

    /**
     * send a message and verify the receiption using the local number and default timer
     * @return
     */
    protected boolean sendAndReceiveMessage() throws Throwable {
        return sendAndReceiveMessage(mRecipient, mReceiveTimer);
    }

    /**
     * @param recipientNumber the recipient number for this sms
     * @param receiveTimer timer to wait for the received message, if it is null, default timer
     *        is used.
     * @return true if received message is equal to what sent, otherwise, return false
     * @throws Throwable
     */
    protected boolean sendAndReceiveMessage(String recipientNumber, long timer)
        throws Throwable {
        long receiveTimer = mReceiveTimer;
        if (timer > 0) {
            receiveTimer = timer;
        }
        int msgCount = mActivity.mMsgListAdapter.getCount();
        Log.v(TAG, "msgCount: " + msgCount);
          mSendSmsMessage.setRecipient(recipientNumber);
        runTestOnUiThread(mSendSmsMessage);

        // Wait for maximum 5 minutes to send the long message
        // and then receive it. Make sure the sent and received messages are the same.
        boolean received = false;
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) <= receiveTimer) {
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
                MessageItem msgItem =
                    mActivity.mMsgListAdapter.getCachedMessageItem(type, msgId, cursor);
                assertNotNull("got a null last MessageItem", msgItem);
                assertEquals("The sent and received messages aren't the same",
                        mMessage,
                        msgItem.mBody);
                received = true;
                break;
            }
        }
        assertTrue("Never received the sent message", received);
        return received;
    }
}
