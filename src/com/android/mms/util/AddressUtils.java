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
import android.database.sqlite.SqliteWrapper;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Addr;
import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;

public class AddressUtils {
    private static final String TAG = "AddressUtils";

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
}
