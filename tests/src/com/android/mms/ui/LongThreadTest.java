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

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Random;

import com.android.mms.R;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.MessageListView;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;

/**
 * Test threads with thousands of messages
 * NOTE: you first have to put the unix words file on the device:
 *    example: adb push ~/words /data/data/com.android.mms/files
 * and then push a file that contains a comma separated list of numbers to send to.
 *    example: adb push ~/recipients /data/data/com.android.mms/files
 *
 */
public class LongThreadTest
extends ActivityInstrumentationTestCase2<ComposeMessageActivity> {

    private TextView mRecipientsView;
    private EditText mTextEditor;
    private EditText mSubjectTextEditor;    // Text editor for MMS subject
    static final String TAG = "LongThreadTest";
    private ArrayList<String> mWords;
    private ArrayList<String> mRecipients;
    private int mWordCount;
    private Random mRandom = new Random();

    public LongThreadTest() {
        super("com.android.mms", ComposeMessageActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        ComposeMessageActivity a = getActivity();
        mRecipientsView = (TextView)a.findViewById(R.id.recipients_editor);
        mTextEditor = (EditText)a.findViewById(R.id.embedded_text_editor);
        mSubjectTextEditor = (EditText)a.findViewById(R.id.subject);

        // Read in dictionary of words
        mWords = new ArrayList<String>(98568);      // count of words in words file
        StringBuilder sb = new StringBuilder();
        try {
            Log.v(TAG, "Loading dictionary of words");
            FileInputStream words = a.openFileInput("words");
            int c;
            while ((c = words.read()) != -1) {
                if (c == '\r' || c == '\n') {
                    String word = sb.toString().trim();
                    if (word.length() > 0) {
                        mWords.add(word);
                    }
                    sb.setLength(0);
                } else {
                    sb.append((char)c);
                }
            }
            words.close();
            mWordCount = mWords.size();
            Log.v(TAG, "Loaded dictionary word count: " + mWordCount);
        } catch (Exception e) {
            Log.e(TAG, "can't open words file at /data/data/com.android.mms/files/words");
            return;
        }

        // Read in list of recipients
        mRecipients = new ArrayList<String>();
        try {
            Log.v(TAG, "Loading recipients");
            FileInputStream recipients = a.openFileInput("recipients");
            int c;
            while ((c = recipients.read()) != -1) {
                if (c == '\r' || c == '\n' || c == ',') {
                    String recipient = sb.toString().trim();
                    if (recipient.length() > 0) {
                        mRecipients.add(recipient);
                    }
                    sb.setLength(0);
                } else {
                    sb.append((char)c);
                }
            }
            recipients.close();
            Log.v(TAG, "Loaded recipients: " + mRecipients.size());
        } catch (Exception e) {
            Log.e(TAG, "can't open recipients file at /data/data/com.android.mms/files/recipients");
            return;
        }
    }

    private String generateMessage() {
        int wordsInMessage = mRandom.nextInt(9) + 1;   // up to 10 words in the message
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < wordsInMessage; i++) {
            msg.append(mWords.get(mRandom.nextInt(mWordCount)) + " ");
        }
        return msg.toString();
    }

    private class AddSubjectMenuItem implements MenuItem {
        private static final int MENU_ADD_SUBJECT = 0;

        public char getAlphabeticShortcut() {
            // TODO Auto-generated method stub
            return 0;
        }

        public int getGroupId() {
            // TODO Auto-generated method stub
            return 0;
        }

        public Drawable getIcon() {
            // TODO Auto-generated method stub
            return null;
        }

        public Intent getIntent() {
            // TODO Auto-generated method stub
            return null;
        }

        public int getItemId() {
            return MENU_ADD_SUBJECT;
        }

        public ContextMenuInfo getMenuInfo() {
            // TODO Auto-generated method stub
            return null;
        }

        public char getNumericShortcut() {
            // TODO Auto-generated method stub
            return 0;
        }

        public int getOrder() {
            // TODO Auto-generated method stub
            return 0;
        }

        public SubMenu getSubMenu() {
            // TODO Auto-generated method stub
            return null;
        }

        public CharSequence getTitle() {
            // TODO Auto-generated method stub
            return null;
        }

        public CharSequence getTitleCondensed() {
            // TODO Auto-generated method stub
            return null;
        }

        public boolean hasSubMenu() {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean isCheckable() {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean isChecked() {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean isEnabled() {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean isVisible() {
            // TODO Auto-generated method stub
            return false;
        }

        public MenuItem setAlphabeticShortcut(char alphaChar) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setCheckable(boolean checkable) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setChecked(boolean checked) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setEnabled(boolean enabled) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setIcon(Drawable icon) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setIcon(int iconRes) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setIntent(Intent intent) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setNumericShortcut(char numericChar) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setOnMenuItemClickListener(
                OnMenuItemClickListener menuItemClickListener) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setShortcut(char numericChar, char alphaChar) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setTitle(CharSequence title) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setTitle(int title) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setTitleCondensed(CharSequence title) {
            // TODO Auto-generated method stub
            return null;
        }

        public MenuItem setVisible(boolean visible) {
            // TODO Auto-generated method stub
            return null;
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
            if (mRecipientsView.getVisibility() == View.VISIBLE) {
                mRecipientsView.setText(mRecipient);
            }
            mTextEditor.setText(generateMessage());
            final ComposeMessageActivity a = getActivity();
            Button send = (Button)a.findViewById(R.id.send_button);
            send.performClick();
        }
    };

    private MessageRunnable mSendMmsMessage = new MessageRunnable() {
        public void run() {
            // only on the first message will there be a recipients editor
            if (mRecipientsView.getVisibility() == View.VISIBLE) {
                mRecipientsView.setText(mRecipient);
            }
            // Add a subject
            final ComposeMessageActivity a = getActivity();
            MenuItem item = new AddSubjectMenuItem();
            a.onOptionsItemSelected(item);
            mSubjectTextEditor.setText(generateMessage());

            mTextEditor.setText(generateMessage());
            Button send = (Button)a.findViewById(R.id.send_button);
            send.performClick();
        }
    };

    /**
     * Send a flurry of SMS and MMS messages
     */
    @LargeTest
    public void testSendManyMessages() throws Throwable {
        // BTW, sending 50 messages brings up the "Sending too many messages" alert so
        // backing down to a smaller number.
        final int MAXSEND = 30;
        final int MSG_PER_RECIPIENT = MAXSEND / mRecipients.size();
        final int MMS_FREQ = Math.min(MSG_PER_RECIPIENT / 10, 1);

        final ComposeMessageActivity a = getActivity();
        for (String recipient : mRecipients) {
            a.runOnUiThread(new Runnable() {
                public void run() {
                    a.initialize(null);
                    a.loadMessageContent();
                }
            });

            for (int i = 0; i < MSG_PER_RECIPIENT; i++) {
                Log.v(TAG, "Sending msg: " + i);
                if (i % MMS_FREQ == 0) {
                    mSendMmsMessage.setRecipient(recipient);
                    runTestOnUiThread(mSendMmsMessage);
                } else {
                    mSendSmsMessage.setRecipient(recipient);
                    runTestOnUiThread(mSendSmsMessage);
                }
                Thread.sleep(5000);     // wait 5 seconds between messages
            }
        }
        assertTrue(true);
    }
}
