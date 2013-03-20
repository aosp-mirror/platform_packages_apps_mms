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
package com.android.mms.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.google.android.mms.MmsCreationMode;

public class ContentRestrictionFactory {
    private static ContentRestriction sContentRestriction;

    private ContentRestrictionFactory() {
    }

    public static ContentRestriction getContentRestriction(Context context) {
        int creationMode = getCreationMode(context);
        if (null == sContentRestriction || sContentRestriction.getCreationMode() != creationMode) {
            sContentRestriction = new CarrierContentRestriction(creationMode);
        }
        return sContentRestriction;
    }

    private static int getCreationMode(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int creationMode = Integer.parseInt(preferences.getString(
            MessagingPreferenceActivity.CREATION_MODE,
            MmsCreationMode.CREATION_MODE_FREE + ""));
        return creationMode;
    }
}
