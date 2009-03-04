/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.mms.ui;

import com.android.mms.R;
import com.google.android.mms.pdu.PduHeaders;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;

/**
 * With this activity, users can set preferences for MMS and SMS and
 * can access and manipulate SMS messages stored on the SIM.
 */
public class MessagingPreferenceActivity extends PreferenceActivity {

    // Creation modes
    public static final int VALUE_CREATION_MODE_RESTRICTED = 0;
    public static final int VALUE_CREATION_MODE_WARNING    = 1;
    public static final int VALUE_CREATION_MODE_FREE       = 2;

    // Re-submission modes
    public static final int VALUE_RESUBMISSION_MODE_RESTRICTED = 0;
    public static final int VALUE_RESUBMISSION_MODE_WARNING    = 1;
    public static final int VALUE_RESUBMISSION_MODE_FREE       = 2;

    // Symbolic names for the keys used for preference lookup
    public static final String COMPRESS_IMAGE_MODE      = "pref_key_mms_compress_images";
    public static final String CREATION_MODE            = "pref_key_mms_creation_mode";
    public static final String MMS_DELIVERY_REPORT_MODE = "pref_key_mms_delivery_reports";
    public static final String EXPIRY_TIME              = "pref_key_mms_expiry";
    public static final String PRIORITY                 = "pref_key_mms_priority";
    public static final String READ_REPORT_MODE         = "pref_key_mms_read_reports";
    public static final String RESUBMISSION_MODE        = "pref_key_mms_resubmission_mode";
    public static final String SMS_DELIVERY_REPORT_MODE = "pref_key_sms_delivery_reports";
    public static final String NOTIFICATION_ENABLED     = "pref_key_enable_notifications";
    public static final String NOTIFICATION_VIBRATE     = "pref_key_vibrate";
    public static final String NOTIFICATION_SOUND       = "pref_key_sound";
    public static final String NOTIFICATION_RINGTONE    = "pref_key_ringtone";
    public static final String AUTO_RETRIEVAL           = "pref_key_mms_auto_retrieval";
    public static final String RETRIEVAL_DURING_ROAMING = "pref_key_mms_retrieval_during_roaming";

    // Menu entries
    private static final int MENU_RESTORE_DEFAULTS    = 1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.clear();
        menu.add(0, MENU_RESTORE_DEFAULTS, 0, R.string.restore_default);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESTORE_DEFAULTS:
                restoreDefaultPreferences();
                return true;
        }
        return false;
    }
    
    private void restoreDefaultPreferences() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit().clear().commit();
        setPreferenceScreen(null);
        addPreferencesFromResource(R.xml.preferences);
    }

    // TODO: Move this to a more appropriate class.
    /**
     * Given the name of a content class, which must be one of the
     * "pref_entry_values_mms_content_class" values in
     * "res/values/arrays.xml", return the corresponding ID.
     */
    public static int convertContentClass(String contentValue) {
        return Integer.parseInt(contentValue);
    }

    // TODO: Move this to a more appropriate class.
    /**
     * Given the string value of an expiry time from
     * Sharedpreferences, which must be one of the
     * "pref_entry_values_mms_expiry" values in
     * "res/values/arrays.xml", return the corresponding number of
     * seconds.
     */
    public static int convertExpiryTime(String expiryTimeName) {
        return Integer.parseInt(expiryTimeName);
    }

    // TODO: Move this to a more appropriate class.
    /**
     * Given the string value of a maximum size from
     * Sharedpreferences, which must be one of the
     * "pref_entry_values_mms_max_size" values in
     * "res/values/arrays.xml", return the corresponding number of
     * bytes.
     */
    public static long convertMaxMmsSize(String maxSize) {
        return Long.parseLong(maxSize);
    }

    // TODO: Move this to a more appropriate class.
    /**
     * Given the name of a priority class, which must be one of the
     * "pref_entry_values_mms_priority" values in
     * "res/values/arrays.xml", return the corresponding ID.
     */
    public static int convertPriorityId(String priorityValue) {
        if ("low".equals(priorityValue)) {
            return PduHeaders.PRIORITY_LOW;
        } else if ("medium".equals(priorityValue)) {
            return PduHeaders.PRIORITY_NORMAL;
        } else if ("high".equals(priorityValue)) {
            return PduHeaders.PRIORITY_HIGH;
        } else {
            throw new IllegalArgumentException("Unknown MMS priority.");
        }
    }

    // TODO: Move this to a more appropriate class.
    /**
     * Given the name of a creation mode, which must be one of the
     * "pref_entry_values_mms_creation_mode" values in
     * "res/values/arrays.xml", return the corresponding ID.
     */
    public static int convertCreationMode(String modeName) {
        if ("creation".equals(modeName)) {
            return MessagingPreferenceActivity.VALUE_CREATION_MODE_RESTRICTED;
        } else if ("warning".equals(modeName)) {
            return MessagingPreferenceActivity.VALUE_CREATION_MODE_WARNING;
        } else if ("free".equals(modeName)) {
            return MessagingPreferenceActivity.VALUE_CREATION_MODE_FREE;
        } else {
            throw new IllegalArgumentException("Unknown MMS creation mode.");
        }
    }

    // TODO: Move this to a more appropriate class.
    /**
     * Given the name of a resubmission mode, which must be one of the
     * "pref_entry_values_mms_resubmission_mode" values in
     * "res/values/arrays.xml", return the corresponding ID.
     */
    public static int convertResubmissionMode(String modeName) {
        if ("creation".equals(modeName)) {
            return MessagingPreferenceActivity.VALUE_RESUBMISSION_MODE_RESTRICTED;
        } else if ("warning".equals(modeName)) {
            return MessagingPreferenceActivity.VALUE_RESUBMISSION_MODE_WARNING;
        } else if ("free".equals(modeName)) {
            return MessagingPreferenceActivity.VALUE_RESUBMISSION_MODE_FREE;
        } else {
            throw new IllegalArgumentException("Unknown MMS resubmission mode.");
        }
    }
}
