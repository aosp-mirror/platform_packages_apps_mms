/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mms;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class MmsConfig {
    private static final String TAG = LogTag.TAG;

    final static HashMap<Integer, Bundle> mConfigValues = new HashMap<Integer, Bundle>();

    // This is the max amount of storage multiplied by mMaxMessageSize that we
    // allow of unsent messages before blocking the user from sending any more
    // MMS's.
    private static int mMaxSizeScaleForPendingMmsAllowed = 4;       // default value
    private static int mDefaultSMSMessagesPerThread = 200;      // default value
    private static int mDefaultMMSMessagesPerThread = 20;       // default value
    private static int mMinMessageCountPerThread = 2;           // default value
    private static int mMaxMessageCountPerThread = 5000;        // default value
    private static boolean mEnableSlideDuration = true;
    private static int mMinimumSlideElementDuration = 7;        // default to 7 sec

    private static String sMmsAppPackage;

    private static final String SMS_PROMO_DISMISSED_KEY = "sms_promo_dismissed_key";

    /*
     * Macro names
     */
    // The raw phone number from TelephonyManager.getLine1Number
    public static final String MACRO_LINE1 = "LINE1";
    // The phone number without country code
    public static final String MACRO_LINE1NOCOUNTRYCODE = "LINE1NOCOUNTRYCODE";
    // NAI (Network Access Identifier), used by Sprint for authentication
    public static final String MACRO_NAI = "NAI";


    public static long getLong(int subId, final String valueName) {
        Bundle bundle = getBundle(subId);
        return bundle != null ? bundle.getLong(valueName) : 0L;
    }

    public static long getLong(final String valueName) {
        return getLong(SubscriptionManager.getDefaultSmsSubId(), valueName);
    }

    public static int getInt(int subId, final String valueName) {
        Bundle bundle = getBundle(subId);
        return bundle != null ? bundle.getInt(valueName) : 0;
    }

    public static int getInt(final String valueName) {
        return getInt(SubscriptionManager.getDefaultSmsSubId(), valueName);
    }

    public static String getString(int subId, final String valueName) {
        Bundle bundle = getBundle(subId);
        return bundle != null ? bundle.getString(valueName) : null;
    }

    public static String getString(final String valueName) {
        return getString(SubscriptionManager.getDefaultSmsSubId(), valueName);
    }

    public static boolean getBoolean(int subId, final String valueName) {
        Bundle bundle = getBundle(subId);
        return bundle != null ? bundle.getBoolean(valueName) : false;
    }

    public static boolean getBoolean(final String valueName) {
        return getBoolean(SubscriptionManager.getDefaultSmsSubId(), valueName);
    }

    private static Bundle getBundle(int subId) {
        final boolean validSubId = SubscriptionManager.isUsableSubIdValue(subId);
        if (!validSubId) {
            subId = SubscriptionManager.getDefaultSmsSubId();
        }

        Bundle bundle = mConfigValues.get(subId);
        if (bundle != null) {
//            Log.v(TAG, "getBundle CACHED subId: " + subId + " " + bundleToString(bundle));
            return bundle;
        }
        SmsManager smsManager = SmsManager.getSmsManagerForSubscriber(subId);
        bundle = smsManager.getCarrierConfigValues();
        if (bundle != null && validSubId) {
            mConfigValues.put(subId, bundle);
        }
//        Log.v(TAG, "getBundle subId: " + subId + " " + bundleToString(bundle));
        return bundle;
    }

    private static String bundleToString(final Bundle bundle) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bundle: {");
        if (bundle == null) {
            sb.append("null");
        } else {
            for (String key : bundle.keySet()) {
                sb.append(" " + key + " -> " + bundle.get(key) + ";");
            }
        }
        sb.append(" }");
        return sb.toString();
    }


    public static int getMaxSizeScaleForPendingMmsAllowed() {
        return mMaxSizeScaleForPendingMmsAllowed;
    }

    public static int getDefaultSMSMessagesPerThread() {
        return mDefaultSMSMessagesPerThread;
    }

    public static int getDefaultMMSMessagesPerThread() {
        return mDefaultMMSMessagesPerThread;
    }

    public static int getMinMessageCountPerThread() {
        return mMinMessageCountPerThread;
    }

    public static int getMaxMessageCountPerThread() {
        return mMaxMessageCountPerThread;
    }

    public static boolean isSmsEnabled() {
        String defaultSmsApplication =
                Telephony.Sms.getDefaultSmsPackage(MmsApp.getApplication().getApplicationContext());

        if (defaultSmsApplication != null && defaultSmsApplication.equals(getMmsAppPackageName())) {
            return true;
        }
        return false;
    }

    private static String getMmsAppPackageName() {
        if (sMmsAppPackage == null) {
            sMmsAppPackage = MmsApp.getApplication().getApplicationContext().getPackageName();
        }
        return sMmsAppPackage;
    }

    public static boolean isSmsPromoDismissed() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                MmsApp.getApplication().getApplicationContext());
        return preferences.getBoolean(SMS_PROMO_DISMISSED_KEY, false);
    }

    public static void setSmsPromoDismissed() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                MmsApp.getApplication().getApplicationContext());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(SMS_PROMO_DISMISSED_KEY, true);
        editor.apply();
    }

    public static boolean getSlideDurationEnabled() {
        return mEnableSlideDuration;
    }

    public static int getMinimumSlideElementDuration() {
        return mMinimumSlideElementDuration;
    }

    public static String getHttpParamMacro(String macro, int subId) {
        if (MACRO_LINE1.equals(macro)) {
            return getLine1(subId);
        } else if (MACRO_LINE1NOCOUNTRYCODE.equals(macro)) {
            return getLine1NoCountryCode(subId);
        } else if (MACRO_NAI.equals(macro)) {
            return getNai(subId);
        }
        return null;
    }

    /**
     * @return the phone number
     */
    private static String getLine1(int subId) {
        final TelephonyManager telephonyManager =
                (TelephonyManager) MmsApp.getApplication().getApplicationContext().getSystemService(
                        Context.TELEPHONY_SERVICE);
        return telephonyManager.getLine1NumberForSubscriber(subId);
    }

    private static String getLine1NoCountryCode(int subId) {
        final TelephonyManager telephonyManager =
                (TelephonyManager) MmsApp.getApplication().getApplicationContext().getSystemService(
                        Context.TELEPHONY_SERVICE);
        // TODO - strip country code
        return telephonyManager.getLine1NumberForSubscriber(subId);
    }

    /**
     * @return the NAI (Network Access Identifier) from SystemProperties
     */
    private static String getNai(int subId) {
        final TelephonyManager telephonyManager =
                (TelephonyManager) MmsApp.getApplication().getApplicationContext().getSystemService(
                        Context.TELEPHONY_SERVICE);
        String nai = telephonyManager.getNai(SubscriptionManager.getSlotId(subId));
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "MmsConfig.getNai: nai=" + nai);
        }

        if (!TextUtils.isEmpty(nai)) {
            String naiSuffix = getString(subId, SmsManager.MMS_CONFIG_NAI_SUFFIX);
            if (!TextUtils.isEmpty(naiSuffix)) {
                nai = nai + naiSuffix;
            }
            byte[] encoded = null;
            try {
                encoded = Base64.encode(nai.getBytes("UTF-8"), Base64.NO_WRAP);
            } catch (UnsupportedEncodingException e) {
                encoded = Base64.encode(nai.getBytes(), Base64.NO_WRAP);
            }
            try {
                nai = new String(encoded, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                nai = new String(encoded);
            }
        }
        return nai;
    }
}
