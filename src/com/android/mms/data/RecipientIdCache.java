package com.android.mms.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.mms.util.SqliteWrapper;

public class RecipientIdCache {
    private static final String TAG = "RecipientIdCache";

    private static Uri sAllCanonical = Uri.parse("content://mms-sms/canonical-addresses");
    private static RecipientIdCache sInstance;
    static RecipientIdCache getInstance() { return sInstance; }
    private final Map<String, String> mCache;
    private final Context mContext;

    static void init(Context context) {
        sInstance = new RecipientIdCache(context);
        new Thread(new Runnable() {
            public void run() {
                fill();
            }
        }).start();
    }

    RecipientIdCache(Context context) {
        mCache = new HashMap<String, String>();
        mContext = context;
    }

    public static void fill() {
        Context context = sInstance.mContext;
        Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                sAllCanonical, null, null, null, null);
        try {
            synchronized (sInstance) {
                // Technically we don't have to clear this because the stupid
                // canonical_addresses table is never GC'ed.
                sInstance.mCache.clear();
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    String number = c.getString(1);
                    sInstance.mCache.put(id, number);
                }
            }
        } finally {
            c.close();
        }
    }

    public static List<String> getNumbers(String spaceSepIds) {
        synchronized (sInstance) {
            List<String> numbers = new ArrayList<String>();
            String[] ids = spaceSepIds.split(" ");
            for (String id : ids) {
                String number = sInstance.mCache.get(id);
                if (number == null) {
                    Log.w(TAG, "Recipient ID " + id + " not in DB!");
                    dump();
                    fill();
                    number = sInstance.mCache.get(id);
                } 
                if (TextUtils.isEmpty(number)) {
                    Log.w(TAG, "Recipient ID " + id + " has empty number!");
                } else {
                    numbers.add(number);
                }
            }
            return numbers;
        }
    }

    public static void dump() {
        synchronized (sInstance) {
            Log.d(TAG, "*** Recipient ID cache dump ***");
            for (String id : sInstance.mCache.keySet()) {
                Log.d(TAG, id + ": " + sInstance.mCache.get(id));
            }
        }
    }
}
