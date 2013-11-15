/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

/*****************************************************************************
* Copyright 2007 - 2009 Broadcom Corporation.  All rights reserved.
*
* This program is the proprietary software of Broadcom Corporation and/or
* its licensors, and may only be used, duplicated, modified or distributed
* pursuant to the terms and conditions of a separate, written license
* agreement executed between you and Broadcom (an "Authorized License").
*
* Except as set forth in an Authorized License, Broadcom grants no license
* (express or implied), right to use, or waiver of any kind with respect to
* the Software, and Broadcom expressly reserves all rights in and to the
* Software and all intellectual property rights therein.  IF YOU HAVE NO
* AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS SOFTWARE IN ANY
* WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE ALL USE OF
* THE SOFTWARE.
*
* Except as expressly set forth in the Authorized License,
* 1. This program, including its structure, sequence and organization,
*    constitutes the valuable trade secrets of Broadcom, and you shall use
*    all reasonable efforts to protect the confidentiality thereof, and to
*    use this information only in connection with your use of Broadcom
*    integrated circuit products.
*
* 2. TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED "AS IS"
*    AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES, REPRESENTATIONS OR
*    WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE, WITH
*    RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY DISCLAIMS ANY AND ALL
*    IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY, NONINFRINGEMENT, FITNESS
*    FOR A PARTICULAR PURPOSE, LACK OF VIRUSES, ACCURACY OR COMPLETENESS,
*    QUIET ENJOYMENT, QUIET POSSESSION OR CORRESPONDENCE TO DESCRIPTION. YOU
*    ASSUME THE ENTIRE RISK ARISING OUT OF USE OR PERFORMANCE OF THE SOFTWARE.
*
* 3. TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR ITS
*    LICENSORS BE LIABLE FOR (i) CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT,
*    OR EXEMPLARY DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
*    YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM HAS BEEN
*    ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR (ii) ANY AMOUNT IN EXCESS
*    OF THE AMOUNT ACTUALLY PAID FOR THE SOFTWARE ITSELF OR U.S. $1, WHICHEVER
*    IS GREATER. THESE LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
*    ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
*/

package com.android.mms.transaction;

import static android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION;

import com.google.android.mms.ContentType;
import com.google.android.mms.pdu.WapPushPdu;
import com.google.android.mms.pdu.WapPushParser;
import android.database.sqlite.SqliteWrapper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Inbox;
import android.util.Config;
import android.util.Log;

import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RILConstants.SimCardID;

import com.android.mms.util.Recycler;
/**
 * Receives Intent.WAP_PUSH_RECEIVED_ACTION intents and starts the
 * TransactionService by passing the push-data to it.
 */
public class WapPushReceiver extends BroadcastReceiver {
    private static final String TAG = "WapPushReceiver";
    private static final boolean DEBUG = true;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    /**
     * Converts a byte array into a String hexidecimal characters
     *
     * null returns null
     */
    public static String
    bytesToHexString(byte[] bytes) {
        if (bytes == null) return null;

        StringBuilder ret = new StringBuilder(2*bytes.length);

        for (int i = 0 ; i < bytes.length ; i++) {
            int b;

            b = 0x0f & (bytes[i] >> 4);

            ret.append("0123456789abcdef".charAt(b));

            b = 0x0f & bytes[i];

            ret.append("0123456789abcdef".charAt(b));
        }

        return ret.toString();
    }


    private class ReceivePushTask extends AsyncTask<Intent,Void,Void> {
        private Context mContext;
        public ReceivePushTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Intent... intents) {
            Intent intent = intents[0];
            // Get raw PDU push-data from the message and parse it
            int simId = ((SimCardID)(intent.getExtra("simId", SimCardID.ID_ZERO))).toInt();
            if (LOCAL_LOGV) {
                Log.v(TAG, "simId = " + simId + " intent.getType = " + intent.getType());
            }

            byte[] pushData = intent.getByteArrayExtra("data");
            if (LOCAL_LOGV) {
                Log.v(TAG, "data:"+bytesToHexString(pushData));
            }

            if (ContentType.WAP_PUSH_SI.equals(intent.getType())) {
                Log.d(TAG, "parsing WAP PUSH SI WBXML");
                WapPushParser wapPushParser = new WapPushParser(pushData);
                WapPushPdu wapPushPdu = wapPushParser.parse();
                if (wapPushPdu == null) {
                    return null;
                }

                if (DEBUG)
                    dumpWapPushPdu(wapPushPdu);

                if (WapPushPdu.WAP_PUSH_SI_ACTION_DELETE == wapPushPdu.getAction()) {
                    Log.d(TAG, "delete");
                    return null;
                }

                Uri messageUri = storeMessage(mContext, wapPushPdu, 0);
                Log.d(TAG, "messageUri = " + messageUri);
                if (messageUri != null) {
                    // Called off of the UI thread so ok to block.
                    long threadId = MessagingNotification.getSmsThreadId(mContext, messageUri);
                    MessagingNotification.blockingUpdateNewMessageIndicator(mContext, threadId, false);
                }
            } else if (ContentType.WAP_PUSH_SL.equals(intent.getType())) {
                Log.d(TAG, "parsing WAP PUSH SL WBXML");
                WapPushParser wapPushParser = new WapPushParser(pushData);
                WapPushPdu wapPushPdu = wapPushParser.parse();
                if (wapPushPdu == null) {
                    return null;
                }

                if (DEBUG)
                    dumpWapPushPdu(wapPushPdu);

                if (WapPushPdu.WAP_PUSH_SL_ACTION_CACHE == wapPushPdu.getAction()) {
                    Log.d(TAG, "cache");
                    return null;
                }

                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(wapPushPdu.getHref()));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(i);
            }

            if (LOCAL_LOGV) {
                Log.v(TAG, " WAP PUSH Intent processed.");
            }
            return null;
        }
    }

    private static void dumpWapPushPdu(WapPushPdu wapPushPdu) {
        Log.d(TAG, "wapPushPdu.getWapPushType() = " + ((wapPushPdu.getWapPushType() == WapPushPdu.WAP_PUSH_SI) ? "SI" : "SL"));
        Log.d(TAG, "wapPushPdu.getText() = " + wapPushPdu.getText());
        Log.d(TAG, "wapPushPdu.getHref() = " + wapPushPdu.getHref());
        Log.d(TAG, "wapPushPdu.getSiId() = " + wapPushPdu.getSiId());
        Log.d(TAG, "wapPushPdu.getCreatedDateStr() = " + wapPushPdu.getCreatedDateStr());
    }

    /**
     * Use the values in SMS as the skeleton for saving WAP push
     * Some values are discarded.
     */
    private ContentValues extractContentValues(WapPushPdu pdu) {
        // Store the message in the content provider.
        ContentValues values = new ContentValues();

        // leave ADDRESS empty for WAP push
        values.put(android.provider.Telephony.Sms.Inbox.ADDRESS, "");

        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        values.put(android.provider.Telephony.Sms.Inbox.DATE, new Long(System.currentTimeMillis()));
        values.put(android.provider.Telephony.Sms.Inbox.READ, 0);
        values.put(android.provider.Telephony.Sms.Inbox.SEEN, 0);
        values.put(android.provider.Telephony.Sms.Inbox.SUBJECT, (pdu.getWapPushType() == WapPushPdu.WAP_PUSH_SI) ? "WAP Push SI" : "WAP Push SL");
        values.put(android.provider.Telephony.Sms.Inbox.REPLY_PATH_PRESENT, 0);
        return values;
    }

    private Uri storeMessage(Context context, WapPushPdu pdu, int error) {
        // Store the message in the content provider.
        ContentValues values = extractContentValues(pdu);

        // There is only one part, so grab the body directly.
        if (pdu.getText() != null)
            values.put(android.provider.Telephony.Sms.Inbox.BODY, pdu.getText() + " " + pdu.getHref());
        else
            values.put(android.provider.Telephony.Sms.Inbox.BODY, pdu.getHref());

        ContentResolver resolver = context.getContentResolver();
        Uri insertedUri = SqliteWrapper.insert(context, resolver, android.provider.Telephony.Sms.Inbox.CONTENT_URI, values);

        return insertedUri;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SystemProperties.get("ro.wap.push").equals("0")) {
            Log.d(TAG, "WAP Push is not supported.");
            return;
        }
        if (intent.getAction().equals(WAP_PUSH_RECEIVED_ACTION)) {
            if (ContentType.WAP_PUSH_SI.equals(intent.getType())) {
                Log.d(TAG, "WAP Push SI");
            }
            else if (ContentType.WAP_PUSH_SL.equals(intent.getType())) {
                Log.d(TAG, "WAP Push SL");
            } else {
                Log.d(TAG, "Unsupported WAP Push Type");
            }

            // Hold a wake lock for 5 seconds, enough to give any
            // services we start time to take their own wake locks.
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                       "WAP PUSH SI PushReceiver");
            wl.acquire(5000);
            new ReceivePushTask(context).execute(intent);
        }
    }
}
