package com.android.mms.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;

import java.util.List;

public class SubStatusResolver {
    private static final String TAG = "SubStatusResolver";
    public static final long SUB_INDEX_ERROR = -1;    

    public static boolean isMobileDataEnabledOnSub(Context context, long subId) {
        if (subId == PhoneConstants.SUB1) {
            return true;
        } else {
            return false;
        }
    }
    
    public static boolean isMobileDataEnabledOnAnySub(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.
                getSystemService(Context.CONNECTIVITY_SERVICE);

        return connectivityManager.getMobileDataEnabled();
    }

}
