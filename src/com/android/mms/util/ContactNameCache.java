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

import java.util.HashMap;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Contacts;
import android.provider.Telephony.Sms;

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
        ctxt.getContentResolver().registerContentObserver(Contacts.Phones.CONTENT_URI,
                true, mPhonesObserver);
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
            String name = Sms.getDisplayAddress(context, address);
            mCachedNames.put(address, name);
            return name;
        }
    }

    // should be private but made package private to avoid the extra accessor
    // for ContentObserver
    synchronized void invalidate() {
        mCachedNames.clear();
    }
}
