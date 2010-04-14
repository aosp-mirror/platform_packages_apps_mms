package com.android.mms.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.ContentValues;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.provider.Telephony;

import com.android.mms.LogTag;
import android.database.sqlite.SqliteWrapper;

public class RecipientIdCache {
    private static final boolean LOCAL_DEBUG = false;
    private static final String TAG = "Mms/cache";

    private static Uri sAllCanonical =
            Uri.parse("content://mms-sms/canonical-addresses");

    private static Uri sSingleCanonicalAddressUri =
            Uri.parse("content://mms-sms/canonical-address");

    private static RecipientIdCache sInstance;
    static RecipientIdCache getInstance() { return sInstance; }
    private final Map<Long, String> mCache;
    private final Context mContext;

    public static class Entry {
        public long id;
        public String number;

        public Entry(long id, String number) {
            this.id = id;
            this.number = number;
        }
    };

    static void init(Context context) {
        sInstance = new RecipientIdCache(context);
        new Thread(new Runnable() {
            public void run() {
                fill();
            }
        }).start();
    }

    RecipientIdCache(Context context) {
        mCache = new HashMap<Long, String>();
        mContext = context;
    }

    public static void fill() {
        if (Log.isLoggable(LogTag.THREAD_CACHE, Log.VERBOSE)) {
            LogTag.debug("[RecipientIdCache] fill: begin");
        }

        Context context = sInstance.mContext;
        Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                sAllCanonical, null, null, null, null);
        if (c == null) {
            Log.w(TAG, "null Cursor in fill()");
            return;
        }

        try {
            synchronized (sInstance) {
                // Technically we don't have to clear this because the stupid
                // canonical_addresses table is never GC'ed.
                sInstance.mCache.clear();
                while (c.moveToNext()) {
                    // TODO: don't hardcode the column indices
                    long id = c.getLong(0);
                    String number = c.getString(1);
                    sInstance.mCache.put(id, number);
                }
            }
        } finally {
            c.close();
        }

        if (Log.isLoggable(LogTag.THREAD_CACHE, Log.VERBOSE)) {
            LogTag.debug("[RecipientIdCache] fill: finished");
            dump();
        }
    }

    public static List<Entry> getAddresses(String spaceSepIds) {
        synchronized (sInstance) {
            List<Entry> numbers = new ArrayList<Entry>();
            String[] ids = spaceSepIds.split(" ");
            for (String id : ids) {
                long longId;

                try {
                    longId = Long.parseLong(id);
                } catch (NumberFormatException ex) {
                    // skip this id
                    continue;
                }

                String number = sInstance.mCache.get(longId);

                if (number == null) {
                    Log.w(TAG, "RecipientId " + longId + " not in cache!");
                    if (Log.isLoggable(LogTag.THREAD_CACHE, Log.VERBOSE)) {
                        dump();
                    }
                    
                    fill();
                    number = sInstance.mCache.get(longId);
                }

                if (TextUtils.isEmpty(number)) {
                    Log.w(TAG, "RecipientId " + longId + " has empty number!");
                } else {
                    numbers.add(new Entry(longId, number));
                }
            }
            return numbers;
        }
    }

    public static void updateNumbers(long threadId, ContactList contacts) {
        long recipientId = 0;

        for (Contact contact : contacts) {
            if (contact.isNumberModified()) {
                contact.setIsNumberModified(false);
            } else {
                // if the contact's number wasn't modified, don't bother.
                continue;
            }

            recipientId = contact.getRecipientId();
            if (recipientId == 0) {
                continue;
            }

            String number1 = contact.getNumber();
            String number2 = sInstance.mCache.get(recipientId);

            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.d(TAG, "[RecipientIdCache] updateNumbers: comparing " + number1 +
                        " with " + number2);
            }

            // if the numbers don't match, let's update the RecipientIdCache's number
            // with the new number in the contact.
            if (!number1.equalsIgnoreCase(number2)) {
                sInstance.mCache.put(recipientId, number1);
                sInstance.updateCanonicalAddressInDb(recipientId, number1);
            }
        }
    }

    private void updateCanonicalAddressInDb(long id, String number) {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.d(TAG, "[RecipientIdCache] updateCanonicalAddressInDb: id=" + id +
                    ", number=" + number);
        }

        ContentValues values = new ContentValues();
        values.put(Telephony.CanonicalAddressesColumns.ADDRESS, number);

        StringBuilder buf = new StringBuilder(Telephony.CanonicalAddressesColumns._ID);
        buf.append('=').append(id);

        Uri uri = ContentUris.withAppendedId(sSingleCanonicalAddressUri, id);
        mContext.getContentResolver().update(uri, values, buf.toString(), null);
    }

    public static void dump() {
        // Only dump user private data if we're in special debug mode
        synchronized (sInstance) {
            Log.d(TAG, "*** Recipient ID cache dump ***");
            for (Long id : sInstance.mCache.keySet()) {
                Log.d(TAG, id + ": " + sInstance.mCache.get(id));
            }
        }
    }
}
