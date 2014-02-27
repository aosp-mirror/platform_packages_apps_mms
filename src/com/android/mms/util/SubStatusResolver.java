package com.android.mms.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.telephony.SubscriptionController;

import java.util.List;

public class SubStatusResolver {
    
    public static final long SUB_INDEX_ERROR = -1;    

    public static boolean isMobileDataEnabledOnSub(Context context, long subId) {
        long currentDataId = SUB_INDEX_ERROR;
        
        ConnectivityManager connectivityManager = (ConnectivityManager) context.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        List<SubscriptionController.SubInfoRecord> subInfoList = SubscriptionController.getActivatedSubInfoList(context);
        if (subInfoList != null) {
            for (SubscriptionController.SubInfoRecord subInfo : subInfoList) {
                if (connectivityManager.getMobileDataEnabled(subInfo.mSubId)) {
                    currentDataId = subInfo.mSubId;
                }
            }
        }
        if (currentDataId != SUB_INDEX_ERROR && currentDataId != subId) {
            return true;
        } else {
            return false;
        }
    }
    
    public static boolean isMobileDataEnabledOnAnySub(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        List<SubscriptionController.SubInfoRecord> subInfoList = SubscriptionController.getActivatedSubInfoList(context);
        if (subInfoList != null) {
            for (SubscriptionController.SubInfoRecord subInfo : subInfoList) {
                if (connectivityManager.getMobileDataEnabled(subInfo.mSubId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
