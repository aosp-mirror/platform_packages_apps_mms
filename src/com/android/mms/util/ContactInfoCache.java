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
import android.text.TextUtils;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String TAG = "MMS/Cache";

    private static final boolean LOCAL_DEBUG = false;

    private static final long REBUILD_DELAY = 5000; // 5 seconds
    private static final String SEPARATOR = ";";

    // query params for caller id lookup
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" +
            Contacts.Phones.NUMBER + ",?)";
    private static final Uri PHONES_WITH_PRESENCE_URI =
            Uri.parse(Contacts.Phones.CONTENT_URI + "_with_presence");

    private static final String[] CALLER_ID_PROJECTION = new String[] {
            Contacts.People.Phones.NUMBER,      // 0
            Contacts.People.Phones.LABEL,       // 1
            Contacts.People.NAME,               // 2
            Contacts.Phones.PERSON_ID,          // 3
            Contacts.People.PRESENCE_STATUS,    // 4
    };
    private static final int PHONE_NUMBER_COLUMN = 0;
    private static final int PHONE_LABEL_COLUMN = 1;
    private static final int CONTACT_NAME_COLUMN = 2;
    private static final int CONTACT_ID_COLUMN = 3;
    private static final int CONTACT_PRESENCE_COLUMN = 4;

    // query params for contact lookup by email
    private static final String CONTACT_METHOD_SELECTION = Contacts.ContactMethods.DATA + "=?";
    private static final Uri CONTACT_METHOD_WITH_PRESENCE_URI =
            Uri.withAppendedPath(Contacts.ContactMethods.CONTENT_URI, "with_presence");

    private static final String[] CONTACT_METHOD_PROJECTION = new String[] {
            Contacts.ContactMethods.NAME,        // 0
            Contacts.People.PRESENCE_STATUS,     // 1
            Contacts.ContactMethods.PERSON_ID,   // 2
    };
    private static final int CONTACT_METHOD_NAME_COLUMN = 0;
    private static final int CONTACT_METHOD_STATUS_COLUMN = 1;
    private static final int CONTACT_METHOD_ID_COLUMN = 2;



    private static ContactInfoCache sInstance;

    private final Context mContext;

    private String[] mContactInfoSelectionArgs = new String[1];

    // cached contact info
    private final HashMap<String, CacheEntry> mCache = new HashMap<String, CacheEntry>();

    // for background cache rebuilding
    private Thread mCacheRebuilder = null;
    private Object mCacheRebuildLock = new Object();
    private boolean mPhoneCacheInvalidated = false;
    private boolean mEmailCacheInvalidated = false;

    /**
     * CacheEntry stores the caller id or email lookup info.
     */
    public class CacheEntry {
        /**
         * phone number
         */
        public String phoneNumber;
        /**
         * phone label
         */
        public String phoneLabel;
        /**
         * name of the contact
         */
        public String name;
        /**
         * the contact id in the contacts people table
         */
        public long person_id;
        /**
         * the presence icon resource id
         */
        public int presenceResId;
        /**
         * If true, it indicates the CacheEntry has old info. We want to give the user of this
         * class a chance to use the old info, as it can still be useful for displaying something
         * rather than nothing in the UI. But this flag indicates that the CacheEntry needs to be
         * updated.
         */
        private boolean isStale;

        /**
         * Returns true if this CacheEntry needs to be updated. However, cache may still contain
         * the old information.
         *
         */
        public boolean isStale() {
            return isStale;
        }
        
        public String toString() {
            StringBuilder buf = new StringBuilder("name=" + name);
            buf.append(", phone=" + phoneNumber);
            buf.append(", pid=" + person_id);
            buf.append(", presence=" + presenceResId);
            buf.append(", stale=" + isStale);
            return buf.toString();
        }
    };

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
     * invalidates the cache entries by marking CacheEntry.isStale to true.
     */
    public void invalidateCache() {
        synchronized (mCache) {
            for (Map.Entry<String, CacheEntry> e: mCache.entrySet()) {
                CacheEntry entry = e.getValue();                
                entry.isStale = true;
            }
        }
    }
    
    /**
     * invalidates a single cache entry. Can pass in an email or number.
     */
    public void invalidateContact(String emailOrNumber) {
        synchronized (mCache) {
            CacheEntry entry = mCache.get(emailOrNumber);
            if (entry != null) {
                entry.isStale = true;
            }
        }
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

    public void dump() {
        synchronized (mCache) {
            Log.i(TAG, "ContactInfoCache.dump");

            for (String name : mCache.keySet()) {
                CacheEntry entry = mCache.get(name);
                if (entry != null) {
                    Log.i(TAG, "key=" + name + ", cacheEntry={" + entry.toString() + '}');
                } else {
                    Log.i(TAG, "key=" + name + ", cacheEntry={null}");
                }
            }
        }        
    }

    /**
     * Returns the caller info in CacheEntry.
     */
    public CacheEntry getContactInfo(Context context, String numberOrEmail) {
        if (Mms.isEmailAddress(numberOrEmail)) {
            return getContactInfoForEmailAddress(context, numberOrEmail, true /* allow query */);
        } else {
            return getContactInfoForPhoneNumber(context, numberOrEmail, true /* allow query */);
        }
    }

    /**
     * Returns the caller info in a CacheEntry. If 'noQuery' is set to true, then this
     * method only checks in the cache and makes no content provider query.
     *
     * @param context the Context.
     * @param number the phone number for the contact.
     * @param allowQuery allow (potentially blocking) query the content provider if true.
     * @return the CacheEntry containing the contact info.
     */
    public CacheEntry getContactInfoForPhoneNumber(Context context, String number,
                                                   boolean allowQuery) {
        // TODO: numbers like "6501234567" and "+16501234567" are equivalent.
        // we should convert them into a uniform format so that we don't cache
        // them twice.
        number = Recipient.filterPhoneNumber(number);
        synchronized (mCache) {
            if (mCache.containsKey(number)) {
                CacheEntry entry = mCache.get(number);
                if (LOCAL_DEBUG) {
                    log("getContactInfo: number=" + number + ", name=" + entry.name +
                            ", presence=" + entry.presenceResId);
                }
                if (!allowQuery || !entry.isStale()) {
                    return entry;
                }
            } else if (!allowQuery) {
                return null;
            }

            CacheEntry entry = queryContactInfoByNumber(context, number);
            mCache.put(number, entry);

            return entry;
        }
    }

    /**
     * Queries the caller id info with the phone number.
     * @return a CacheEntry containing the caller id info corresponding to the number.
     */
    private CacheEntry queryContactInfoByNumber(Context context, String number) {
        CacheEntry entry = new CacheEntry();
        entry.phoneNumber = number;

        //if (LOCAL_DEBUG) log("queryContactInfoByNumber: number=" + number);

        mContactInfoSelectionArgs[0] = number;

        Cursor cursor = context.getContentResolver().query(
                PHONES_WITH_PRESENCE_URI,
                CALLER_ID_PROJECTION,
                CALLER_ID_SELECTION,
                mContactInfoSelectionArgs,
                null);

        try {
            if (cursor.moveToFirst()) {
                entry.phoneLabel = cursor.getString(PHONE_LABEL_COLUMN);
                entry.name = cursor.getString(CONTACT_NAME_COLUMN);
                entry.person_id = cursor.getLong(CONTACT_ID_COLUMN);
                entry.presenceResId = getPresenceIconResourceId(
                        cursor.getInt(CONTACT_PRESENCE_COLUMN));
                if (LOCAL_DEBUG) {
                    log("queryContactInfoByNumber: name=" + entry.name + ", number=" + number +
                            ", presence=" + entry.presenceResId);
                }
            }
        } finally {
            cursor.close();
        }

        return entry;
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

        CacheEntry entry = getContactInfoForEmailAddress(context, email, true /* allow query */);
        if (entry != null && entry.name != null) {
            return entry.name;
        }

        return email;
    }

    /**
     * Returns the contact info for a given email address
     *
     * @param context the context.
     * @param email the email address.
     * @param allowQuery allow making (potentially blocking) content provider queries if true.
     * @return a CacheEntry if the contact is found.
     */
    public CacheEntry getContactInfoForEmailAddress(Context context, String email,
                                                    boolean allowQuery) {
        synchronized (mCache) {
            if (mCache.containsKey(email)) {
                CacheEntry entry = mCache.get(email);
                if (!allowQuery || !entry.isStale()) {
                    return entry;
                }
            } else if (!allowQuery) {
                return null;
            }

            CacheEntry entry = queryEmailDisplayName(context, email);
            mCache.put(email, entry);

            return entry;
        }
    }

    /**
     * A cached version of CallerInfo.getCallerId().
     */
    private String getCallerId(Context context, String number) {
        ContactInfoCache.CacheEntry entry = getContactInfo(context, number);
        if (entry != null && !TextUtils.isEmpty(entry.name)) {
            return entry.name;
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

    private int getPresenceIconResourceId(int presence) {
        if (presence != Contacts.People.OFFLINE) {
            return Contacts.Presence.getPresenceIconResourceId(presence);
        }

        return 0;
    }

    /**
     * Query the contact email table to get the name of an email address.
     */
    private CacheEntry queryEmailDisplayName(Context context, String email) {
        CacheEntry entry = new CacheEntry();

        mContactInfoSelectionArgs[0] = email;
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                CONTACT_METHOD_WITH_PRESENCE_URI,
                CONTACT_METHOD_PROJECTION,
                CONTACT_METHOD_SELECTION,
                mContactInfoSelectionArgs,
                null);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    entry.presenceResId = getPresenceIconResourceId(
                            cursor.getInt(CONTACT_METHOD_STATUS_COLUMN));
                    entry.person_id = cursor.getLong(CONTACT_METHOD_ID_COLUMN);

                    String name = cursor.getString(CONTACT_METHOD_NAME_COLUMN);
                    if (!TextUtils.isEmpty(name)) {
                        entry.name = name;
                        if (LOCAL_DEBUG) {
                            log("queryEmailDisplayName: name=" + entry.name + ", email=" + email +
                                    ", presence=" + entry.presenceResId);
                        }
                        break;
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return entry;
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
                if (LOCAL_DEBUG) log("rebuild cache for phone numbers...");
                for (String phone : phones) {
                    synchronized (mCache) {
                        CacheEntry entry = queryContactInfoByNumber(mContext, phone);
                        mCache.put(phone, entry);
                    }
                }
            }
            if (emails != null) {
                if (LOCAL_DEBUG) log("rebuild cache for emails...");
                for (String email : emails) {
                    synchronized (mCache) {
                        CacheEntry entry = queryEmailDisplayName(mContext, email);
                        mCache.put(email, entry);
                    }
                }
            }
        }
    }

    private void log(String msg) {
        Log.d(TAG, "[ContactInfoCache] " + msg);
    }
}
