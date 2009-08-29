/*
 * Copyright (C) 2008 Esmertec AG.
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
package com.android.mms.util;

import com.android.mms.R;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.util.SqliteWrapper;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Addr;
import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;

public class AddressUtils {
    private static final String TAG = "AddressUtils";

    // This is not really accurate. Most countries have SMS short code length between
    // 3 to 6 digits. The exceptions are

    // Australia: Short codes are six or eight digits in length, starting with the prefix "19"
    //            followed by an additional four or six digits and two.
    // Czech Republic: Codes are seven digits in length for MO and five (not billed) or
    //            eight (billed) for MT direction
    //
    // However, for our specific purpose of comparing two numbers, 6 is good enough.
    //
    // see http://en.wikipedia.org/wiki/Short_code#Regional_differences for reference    
    private final static int MAX_SMS_SHORTCODE_LENGTH = 6;

    private AddressUtils() {
        // Forbidden being instantiated.
    }

    public static String getFrom(Context context, Uri uri) {
        String msgId = uri.getLastPathSegment();
        Uri.Builder builder = Mms.CONTENT_URI.buildUpon();

        builder.appendPath(msgId).appendPath("addr");

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                            builder.build(), new String[] {Addr.ADDRESS, Addr.CHARSET},
                            Addr.TYPE + "=" + PduHeaders.FROM, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    String from = cursor.getString(0);

                    if (!TextUtils.isEmpty(from)) {
                        byte[] bytes = PduPersister.getBytes(from);
                        int charset = cursor.getInt(1);
                        return new EncodedStringValue(charset, bytes)
                                .getString();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return context.getString(R.string.hidden_sender_address);
    }

    /**
     * //TODO: just use PhoneNumberUtils.compare() when it can deal with comparing a SMS short
     * //TODO: code with another longer number whose last 5 digits are the same.
     * 
     * Compares two phone numbers. We'd normally just use PhoneNumberUtils.compare(), but
     * in its current form it's no good for comparing SMS short codes, because it would treat
     * two numbers who have the same last five digits as the same numbers, even if one of the
     * number is a full phone number (i.e. 1-650-222-1111 equals to 21111). So for now let's
     * do our own number comparison if either of the phone numbers is a short code
     *
     */
    public static boolean phoneNumbersEqual(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 <= MAX_SMS_SHORTCODE_LENGTH || len2 <= MAX_SMS_SHORTCODE_LENGTH) {
            if (len1 != len2) {
                return false;
            }
            return s1.equals(s2);
        }

        return PhoneNumberUtils.compare(s1, s2);
    }
}
