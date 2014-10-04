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

package com.android.mms.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.util.Log;

import java.util.List;

import com.android.mms.LogTag;

public class SubStatusResolver {
    private static final String TAG = "SubStatusResolver";

    public static boolean isMobileDataEnabledOnAnySub(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        List<SubInfoRecord> subInfoList = SubscriptionManager.getActiveSubInfoList();
        if (subInfoList != null) {
            for (SubInfoRecord subInfo : subInfoList) {
                if (connectivityManager.getMobileDataEnabled(/* TODO subInfo.subId*/)) {
                    return true;
                }
            }
        }
        return false;
    }
}
