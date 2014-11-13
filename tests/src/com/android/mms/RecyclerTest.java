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

package com.android.mms;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Random;

import com.android.mms.data.Contact;
import com.android.mms.util.Recycler;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Sms.Inbox;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.provider.Telephony.Sms.Conversations;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

/**
 * Bang on the recycler and test it getting called simultaneously from two different threads
 * NOTE: you first have to put the unix words file on the device:
 *    example: adb push ~/words /data/data/com.android.mms/files
 * and then push a file that contains a comma separated list of numbers to send to.
 *    example: adb push ~/recipients /data/data/com.android.mms/files
 *
 */
/**
 * Bang on the recycler and test it getting called simultaneously from two different threads
 * NOTE: you first have to put the unix words file on the device:
 *    example: adb push ~/words /data/data/com.android.mms/files
 * and then push a file that contains a comma separated list of numbers to send to.
 *    example: adb push ~/recipients /data/data/com.android.mms/files
 *
 * To run just this test:
 *    runtest --test-class=com.android.mms.RecyclerTest mms
 */
public class RecyclerTest extends AndroidTestCase {
    static final String TAG = "RecyclerTest";
    private ArrayList<String> mWords;
    private ArrayList<String> mRecipients;
    private int mWordCount;
    private Random mRandom = new Random();
    private int mRecipientCnt;
    private static final Uri sAllThreadsUri =
        Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();
    private static final String[] ALL_THREADS_PROJECTION = {
        Threads._ID, Threads.DATE, Threads.MESSAGE_COUNT, Threads.RECIPIENT_IDS,
        Threads.SNIPPET, Threads.SNIPPET_CHARSET, Threads.READ, Threads.ERROR,
        Threads.HAS_ATTACHMENT
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();

        // Read in dictionary of words
        mWords = new ArrayList<String>(98568);      // count of words in words file
        StringBuilder sb = new StringBuilder();
        try {
            Log.v(TAG, "Loading dictionary of words");
            FileInputStream words = context.openFileInput("words");
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
            FileInputStream recipients = context.openFileInput("recipients");
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
        mRecipientCnt = mRecipients.size();
    }

    private String generateMessage() {
        int wordsInMessage = mRandom.nextInt(9) + 1;   // up to 10 words in the message
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < wordsInMessage; i++) {
            msg.append(mWords.get(mRandom.nextInt(mWordCount)) + " ");
        }
        return msg.toString();
    }

    private Uri storeMessage(Context context, String address, String message) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();
//        values.put(Sms.ERROR_CODE, 0);
        values.put(Inbox.ADDRESS, address);

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        values.put(Inbox.DATE, new Long(System.currentTimeMillis()));
        values.put(Inbox.PROTOCOL, 0);
        values.put(Inbox.READ, Integer.valueOf(0));
//        if (sms.getPseudoSubject().length() > 0) {
//            values.put(Inbox.SUBJECT, sms.getPseudoSubject());
//        }
        values.put(Inbox.REPLY_PATH_PRESENT, 0);
        values.put(Inbox.SERVICE_CENTER, 0);
        values.put(Inbox.BODY, message);

        // Make sure we've got a thread id so after the insert we'll be able to delete
        // excess messages.
        Long threadId = 0L;
        Contact cacheContact = Contact.get(address,true);
        if (cacheContact != null) {
            address = cacheContact.getNumber();
        }

        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            values.put(Sms.THREAD_ID, Threads.getOrCreateThreadId(
                               context, address));
        }

        ContentResolver resolver = context.getContentResolver();

        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);

        // Now make sure we're not over the limit in stored messages
        threadId = values.getAsLong(Sms.THREAD_ID);
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);

        return insertedUri;
    }

    Runnable mRecyclerBang = new Runnable() {
        public void run() {
            final int MAXSEND = Integer.MAX_VALUE;

            for (int i = 0; i < MAXSEND; i++) {
                // Put a random message to one of the random recipients in the SMS db.
                Uri uri = storeMessage(getContext(),
                        mRecipients.get(mRandom.nextInt(mRecipientCnt)),
                        generateMessage());
                Log.v(TAG, "Generating msg uri: " + uri);
                if (i > 100) {
                    // Wait until we've sent a bunch of messages to guarantee we've got
                    // some threads built up. Then check to make sure all the threads are there
                    // on each message. All these queries will provide additional stress on the
                    // sms db.
                    Cursor cursor = null;
                    try {
                        cursor = SqliteWrapper.query(getContext(),
                                getContext().getContentResolver(), sAllThreadsUri,
                                ALL_THREADS_PROJECTION, null, null,
                                Conversations.DEFAULT_SORT_ORDER);
                        assertNotNull("Cursor from thread query is null!", cursor);
                        int cnt = cursor.getCount();
                        assertTrue("The threads appeared to have been wiped out",
                            cursor.getCount() >= mRecipientCnt);
                    } catch (SQLiteException e) {
                        Log.v(TAG, "query for threads failed with exception: " + e);
                        fail("query for threads failed with exception: " + e);
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }
            }
        }
    };

    Runnable mSQLMemoryReleaser = new Runnable() {
        public void run() {
            while (true) {
                SQLiteDatabase.releaseMemory();
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {

                }
            }
        }
    };

    /**
     * Send a flurry of SMS and MMS messages
     */
    @LargeTest
    public void testRecycler() throws Throwable {
        // Start N simultaneous threads generating messages and running the recycler
        final int THREAD_COUNT = 3;
        ArrayList<Thread> threads = new ArrayList<Thread>(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.add(i, new Thread(mRecyclerBang));
            threads.get(i).start();
        }
        Thread memoryBanger = new Thread(mSQLMemoryReleaser);
        memoryBanger.start();

        // Wait for the threads to finish
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.get(i).join();
        }

        assertTrue(true);
    }
}
