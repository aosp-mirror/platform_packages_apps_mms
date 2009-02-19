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
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.RecipientList.Recipient;
import com.google.android.mms.util.SqliteWrapper;

import android.content.Context;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.Contacts;
import android.provider.Telephony.Mms;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;

/**
 * This class caches query results of contact database and provides convenient
 * methods to return contact display name, etc.
 * 
 * TODO: To improve performance, we should make contacts query by ourselves instead of
 *       doing it one by one calling the CallerInfo API. In the long term, the contacts
 *       database could have a caching layer to ease the work for all apps.
 */
public class ContactInfoCache {

    private static final long REBUILD_DELAY = 5000; // 5 seconds
    private static final String SEPARATOR = ";";

    private static ContactInfoCache sInstance;

    private final Context mContext;

    // cached contact info
    private final HashMap<String, CallerInfo> mCache = new HashMap<String, CallerInfo>();

    // for background cache rebuilding
    private Thread mCacheRebuilder = null;
    private Object mCacheRebuildLock = new Object();
    private boolean mPhoneCacheInvalidated = false;
    private boolean mEmailCacheInvalidated = false;

    private ContactInfoCache(Context context) {
        mContext = context;

        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(Contacts.Phones.CONTENT_URI, true,
                new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfUpdate) {
                        synchronized (mCacheRebuildLock) {
                            mPhoneCacheInvalidated = true;
                            startCacheRebuilder();
                        }
                    }
                });
        resolver.registerContentObserver(Contacts.ContactMethods.CONTENT_EMAIL_URI, true,
                new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfUpdate) {
                        synchronized (mCacheRebuildLock) {
                            mEmailCacheInvalidated = true;
                            startCacheRebuilder();
                        }
                    }
                });
    }

    /**
     * Initialize the global instance. Should call only once.
     */
    public static void init(Context context) {
        sInstance = new ContactInfoCache(context);
    }

    /**
     * Get the global instance.
     */
    public static ContactInfoCache getInstance() {
        return sInstance;
    }

    /**
     * A cached version of CallerInfo.getCallerInfo().
     */
    public CallerInfo getCallerInfo(Context context, String number) {
        // TODO: numbers like "6501234567" and "+16501234567" are equivalent.
        // we should convert them into a uniform format so that we don't cache
        // them twice.
        number = Recipient.filterPhoneNumber(number);
        synchronized (mCache) {
            if (mCache.containsKey(number)) {
                return mCache.get(number);
            } else {
                CallerInfo ci = CallerInfo.getCallerInfo(context, number);
                mCache.put(number, ci);
                return ci;
            }
        }
    }

    /**
     * Get the display names of contacts. Contacts can be either email address or
     * phone number.
     *
     * @param context the context to use
     * @param address the addresses to lookup, separated by ";"
     * @return a nicely formatted version of the contact names to display
     */
    public String getContactName(Context context, String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (String value : address.split(SEPARATOR)) {
            if (value.length() > 0) {
                result.append(SEPARATOR);
                if (MessageUtils.isLocalNumber(value)) {
                    result.append(context.getString(com.android.internal.R.string.me));
                } else if (Mms.isEmailAddress(value)) {
                    result.append(getDisplayName(context, value));
                } else {
                    result.append(getCallerId(context, value));
                }
            }
        }

        if (result.length() > 0) {
            // Skip the first ";"
            return result.substring(1);
        }

        return "";
    }

    /**
     * Get the display name of an email address. If the address already contains
     * the name, parse and return it. Otherwise, query the contact database. Cache
     * query results for repeated queries.
     */
    public String getDisplayName(Context context, String email) {
        Matcher match = Mms.NAME_ADDR_EMAIL_PATTERN.matcher(email);
        if (match.matches()) {
            // email has display name
            return getEmailDisplayName(match.group(1));
        }

        CallerInfo ci;
        synchronized (mCache) {
            if (mCache.containsKey(email)) {
                ci = mCache.get(email);
            } else {
                ci = new CallerInfo();
                ci.name = queryEmailDisplayName(context, email);
                mCache.put(email, ci);
            }
        }

        if (ci.name != null) {
            return ci.name;
        }

        return email;
    }

    /**
     * A cached version of CallerInfo.getCallerId().
     */
    private String getCallerId(Context context, String number) {
        CallerInfo info = getCallerInfo(context, number);
        if (!TextUtils.isEmpty(info.name)) {
            return info.name;
        }
        return number;
    }

    private static String getEmailDisplayName(String displayString) {
        Matcher match = Mms.QUOTED_STRING_PATTERN.matcher(displayString);
        if (match.matches()) {
            return match.group(1);
        }

        return displayString;
    }

    /**
     * Query the contact email table to get the name of an email address.
     */
    private static String queryEmailDisplayName(Context context, String email) {
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

        return null;
    }

    /**
     * Start the background cache rebuilding thread if there is not one yet.
     */
    private void startCacheRebuilder() {
        if (mCacheRebuilder == null) {
            mCacheRebuilder = new Thread(new Runnable() {
                    public void run() {
                        rebuildCache();
                    }
            });
            mCacheRebuilder.start();
        }
    }

    /**
     * Get the list of phone/email candidates for the cache rebuilding. This is
     * a snapshot of the keys in the cache.
     */
    private void getRebuildList(List<String> phones, List<String> emails) {
        synchronized (mCache) {
            for (String name : mCache.keySet()) {
                if (Mms.isEmailAddress(name)) {
                    if (emails != null) {
                        emails.add(name);
                    }
                } else {
                    if (phones != null) {
                        phones.add(name);
                    }
                }
            }
        } 
    }

    /**
     * The actual work of rebuilding the cache, i.e. syncing our cache with
     * the contacts database.
     */
    private void rebuildCache() {
        List<String> phones;
        List<String> emails;

        for (;;) {

            // simulate the Nagle's algorithm:
            // delay for a while to prevent from getting too busy, when, say,
            // there is a big contacts sync going on
            try {
                Thread.sleep(REBUILD_DELAY);
            } catch (InterruptedException ie) {
            }

            phones = null;
            emails = null;
            synchronized (mCacheRebuildLock) {
                // if nothing changed during our sync, stop this thread
                // otherwise, just keep working on it.
                if (!(mPhoneCacheInvalidated || mEmailCacheInvalidated)) {
                    mCacheRebuilder = null;
                    return;
                }
                if (mPhoneCacheInvalidated) {
                    phones = new ArrayList<String>();
                    mPhoneCacheInvalidated = false;
                }
                if (mEmailCacheInvalidated) {
                    emails = new ArrayList<String>();
                    mEmailCacheInvalidated = false;
                }
            }
            // retrieve the list of phone/email candidates for syncing
            // which is a snapshot of the keys in the cache
            getRebuildList(phones, emails);
            // now sync
            if (phones != null) {
                for (String phone : phones) {
                    synchronized (mCache) {
                        CallerInfo ci = CallerInfo.getCallerInfo(mContext, phone);
                        mCache.put(phone, ci);
                    }
                }
            }
            if (emails != null) {
                for (String email : emails) {
                    synchronized (mCache) {
                        CallerInfo ci = new CallerInfo();
                        ci.name = queryEmailDisplayName(mContext, email);
                        mCache.put(email, ci);
                    }
                }
            }
        }
    }
}
