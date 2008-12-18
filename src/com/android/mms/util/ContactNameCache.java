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

import com.android.internal.telephony.CallerInfo;
import com.google.android.mms.util.SqliteWrapper;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.Contacts;
import android.provider.Telephony.Mms;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.regex.Matcher;

/**
 * Caches results of Sms.getDisplayAddress().
 */
public class ContactNameCache {
    private static ContactNameCache sInstance;

    private final HashMap<String, String> mCachedNames = new HashMap<String, String>();
    private final ContentObserver mPhonesObserver;

    private ContactNameCache(Context ctxt) {
        mPhonesObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfUpdate) {
                invalidate();
            }
        };
        ctxt.getContentResolver().registerContentObserver(
                Contacts.Phones.CONTENT_URI, true, mPhonesObserver);
    }

    public static void init(Context ctxt) {
        sInstance = new ContactNameCache(ctxt);
    }

    public static ContactNameCache getInstance() {
        return sInstance;
    }

    public synchronized String getContactName(Context context, String address) {
        if (mCachedNames.containsKey(address)) {
            return mCachedNames.get(address);
        } else {
            String name = getDisplayAddress(context, address);
            mCachedNames.put(address, name);
            return name;
        }
    }

    // should be private but made package private to avoid the extra accessor
    // for ContentObserver
    synchronized void invalidate() {
        mCachedNames.clear();
    }

    /**
     * Formats an address for displaying, doing a phone number lookup in the
     * Address Book, etc.
     *
     * @param context the context to use
     * @param address the address to format
     * @return a nicely formatted version of the sender to display
     */
    private static String getDisplayAddress(Context context, String address) {
        if (address == null) {
            return "";
        }

        String localNumber = TelephonyManager.getDefault().getLine1Number();
        String[] values = address.split(";");
        String result = "";
        for (int i = 0; i < values.length; i++) {
            if (values[i].length() > 0) {
                if (PhoneNumberUtils.compare(values[i], localNumber)) {
                    result = result + ";"
                                + context.getString(com.android.internal.R.string.me);
                } else if (Mms.isEmailAddress(values[i])) {
                    result = result + ";" + getDisplayName(context, values[i]);
                } else {
                    result = result + ";" + CallerInfo.getCallerId(context, values[i]);
                }
            }
        }

        if (result.length() > 0) {
            // Skip the first ';'
            return result.substring(1);
        }
        return result;
    }

    private static String getEmailDisplayName(String displayString) {
        Matcher match = Mms.QUOTED_STRING_PATTERN.matcher(displayString);
        if (match.matches()) {
            return match.group(1);
        }

        return displayString;
    }

    private static String getDisplayName(Context context, String email) {
        Matcher match = Mms.NAME_ADDR_EMAIL_PATTERN.matcher(email);
        if (match.matches()) {
            // email has display name
            return getEmailDisplayName(match.group(1));
        }

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                Contacts.ContactMethods.CONTENT_EMAIL_URI,
                new String[] { Contacts.ContactMethods.NAME },
                Contacts.ContactMethods.DATA + " = \'" + email + "\'",
                null, null);

        if (cursor != null) {
            try {
                int columnIndex = cursor.getColumnIndexOrThrow(
                        Contacts.ContactMethods.NAME);
                while (cursor.moveToNext()) {
                    String name = cursor.getString(columnIndex);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return email;
    }
}
