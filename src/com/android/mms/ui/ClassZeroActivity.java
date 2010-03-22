/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;
import android.util.Config;
import android.view.Window;

import com.android.mms.R;
import com.android.mms.transaction.SmsReceiverService;
import com.android.mms.transaction.MessagingNotification;

import android.database.sqlite.SqliteWrapper;

/**
 * Display a class-zero SMS message to the user. Wait for the user to dismiss
 * it.
 */
public class ClassZeroActivity extends Activity {
    private static final String BUFFER = "         ";
    private static final int BUFFER_OFFSET = BUFFER.length() * 2;
    private static final String TAG = "display_00";
    private static final int ON_AUTO_SAVE = 1;
    private static final String[] REPLACE_PROJECTION = new String[] { Sms._ID,
            Sms.ADDRESS, Sms.PROTOCOL };
    private static final int REPLACE_COLUMN_ID = 0;

    /** Default timer to dismiss the dialog. */
    private static final long DEFAULT_TIMER = 5 * 60 * 1000;

    /** To remember the exact time when the timer should fire. */
    private static final String TIMER_FIRE = "timer_fire";

    private SmsMessage mMessage = null;

    /** Is the message read. */
    private boolean mRead = false;

    /** The timer to dismiss the dialog automatically. */
    private long mTimerSet = 0;
    private AlertDialog mDialog = null;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Do not handle an invalid message.
            if (msg.what == ON_AUTO_SAVE) {
                mRead = false;
                mDialog.dismiss();
                saveMessage();
                finish();
            }
        }
    };

    private void saveMessage() {
        Uri messageUri = null;
        if (mMessage.isReplace()) {
            messageUri = replaceMessage(mMessage);
        } else {
            messageUri = storeMessage(mMessage);
        }
        if (!mRead && messageUri != null) {
            MessagingNotification.nonBlockingUpdateNewMessageIndicator(this, true, false);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawableResource(
                R.drawable.class_zero_background);

        byte[] pdu = getIntent().getByteArrayExtra("pdu");
        mMessage = SmsMessage.createFromPdu(pdu);
        CharSequence messageChars = mMessage.getMessageBody();
        String message = messageChars.toString();
        if (TextUtils.isEmpty(message)) {
            finish();
            return;
        }
        // TODO: The following line adds an emptry string before and after a message.
        // This is not the correct way to layout a message. This is more of a hack
        // to work-around a bug in AlertDialog. This needs to be fixed later when
        // Android fixes the bug in AlertDialog.
        if (message.length() < BUFFER_OFFSET) messageChars = BUFFER + message + BUFFER;
        long now = SystemClock.uptimeMillis();
        mDialog = new AlertDialog.Builder(this).setMessage(messageChars)
                .setPositiveButton(R.string.save, mSaveListener)
                .setNegativeButton(android.R.string.cancel, mCancelListener)
                .setCancelable(false).show();
        mTimerSet = now + DEFAULT_TIMER;
        if (icicle != null) {
            mTimerSet = icicle.getLong(TIMER_FIRE, mTimerSet);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        long now = SystemClock.uptimeMillis();
        if (mTimerSet <= now) {
            // Save the message if the timer already expired.
            mHandler.sendEmptyMessage(ON_AUTO_SAVE);
        } else {
            mHandler.sendEmptyMessageAtTime(ON_AUTO_SAVE, mTimerSet);
            if (Config.DEBUG) {
                Log.d(TAG, "onRestart time = " + Long.toString(mTimerSet) + " "
                        + this.toString());
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(TIMER_FIRE, mTimerSet);
        if (Config.DEBUG) {
            Log.d(TAG, "onSaveInstanceState time = " + Long.toString(mTimerSet)
                    + " " + this.toString());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.removeMessages(ON_AUTO_SAVE);
        if (Config.DEBUG) {
            Log.d(TAG, "onStop time = " + Long.toString(mTimerSet)
                    + " " + this.toString());
        }
    }

    private final OnClickListener mCancelListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            finish();
        }
    };

    private final OnClickListener mSaveListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            mRead = true;
            saveMessage();
            finish();
        }
    };

    private ContentValues extractContentValues(SmsMessage sms) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();

        values.put(Inbox.ADDRESS, sms.getDisplayOriginatingAddress());

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        values.put(Inbox.DATE, new Long(System.currentTimeMillis()));
        values.put(Inbox.PROTOCOL, sms.getProtocolIdentifier());
        values.put(Inbox.READ, Integer.valueOf(mRead ? 1 : 0));
        values.put(Inbox.SEEN, Integer.valueOf(mRead ? 1 : 0));

        if (sms.getPseudoSubject().length() > 0) {
            values.put(Inbox.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Inbox.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);
        values.put(Inbox.SERVICE_CENTER, sms.getServiceCenterAddress());
        return values;
    }

    private Uri replaceMessage(SmsMessage sms) {
        ContentValues values = extractContentValues(sms);

        values.put(Inbox.BODY, sms.getMessageBody());

        ContentResolver resolver = getContentResolver();
        String originatingAddress = sms.getOriginatingAddress();
        int protocolIdentifier = sms.getProtocolIdentifier();
        String selection = Sms.ADDRESS + " = ? AND " + Sms.PROTOCOL + " = ?";
        String[] selectionArgs = new String[] { originatingAddress,
                Integer.toString(protocolIdentifier) };

        Cursor cursor = SqliteWrapper.query(this, resolver, Inbox.CONTENT_URI,
                REPLACE_PROJECTION, selection, selectionArgs, null);

        try {
            if (cursor.moveToFirst()) {
                long messageId = cursor.getLong(REPLACE_COLUMN_ID);
                Uri messageUri = ContentUris.withAppendedId(
                        Sms.CONTENT_URI, messageId);

                SqliteWrapper.update(this, resolver, messageUri, values,
                        null, null);
                return messageUri;
            }
        } finally {
            cursor.close();
        }
        return storeMessage(sms);
    }

    private Uri storeMessage(SmsMessage sms) {
        // Store the message in the content provider.
        ContentValues values = extractContentValues(sms);
        values.put(Inbox.BODY, sms.getDisplayMessageBody());
        ContentResolver resolver = getContentResolver();
        if (Config.DEBUG) {
            Log.d(TAG, "storeMessage " + this.toString());
        }
        return SqliteWrapper.insert(this, resolver, Inbox.CONTENT_URI, values);
    }
}
