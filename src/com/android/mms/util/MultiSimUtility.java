/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.app.Service;
import android.telephony.MSimTelephonyManager;
import android.content.Context;
import android.util.Log;

/**
 * The MultiSimActivity is responsible for getting current data subscription.
 */

    public class MultiSimUtility {

        public static final String ORIGIN_SUB_ID = "origin_sub_id";

        public static int getCurrentDataSubscription(Context mContext) {

            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                MSimTelephonyManager mtmgr = (MSimTelephonyManager)
                    mContext.getSystemService (Context.MSIM_TELEPHONY_SERVICE);
                return mtmgr.getPreferredDataSubscription();
            } else {
                return 0;
            }
        }
    }
