package com.android.mms.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.util.Log;

import java.util.List;

public class SubStatusResolver {
    private static final String TAG = "SubStatusResolver";
    public static final long SUB_INDEX_ERROR = -1;    

    public static boolean isMobileDataEnabledOnSub(Context context, long subId) {
        long currentDataId = SUB_INDEX_ERROR;
        
        Log.d(TAG, "isMobileDataEnabledOnSub(), subId = " + subId);
        ConnectivityManager connectivityManager = (ConnectivityManager) context.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        List<SubInfoRecord> subInfoList = SubscriptionManager.getActivatedSubInfoList(context);
        if (subInfoList != null) {
            for (SubInfoRecord subInfo : subInfoList) {
                if (connectivityManager.getMobileDataEnabled(subInfo.mSubId)) {
                    currentDataId = subInfo.mSubId;
                }
            }
        }
        Log.d(TAG, "isMobileDataEnabledOnSub(),  fianl currentDataId = " + currentDataId);
        if (currentDataId != SUB_INDEX_ERROR && currentDataId != subId) {
            return false;
        } else {
            return true;
        }
    }
    
    public static boolean isMobileDataEnabledOnAnySub(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        List<SubInfoRecord> subInfoList = SubscriptionManager.getActivatedSubInfoList(context);
        if (subInfoList != null) {
            for (SubInfoRecord subInfo : subInfoList) {
                if (connectivityManager.getMobileDataEnabled(subInfo.mSubId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
