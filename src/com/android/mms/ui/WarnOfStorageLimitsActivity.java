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

package com.android.mms.ui;

import com.android.mms.R;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

/**
 * This is the UI for telling the user about the storage limit setting.
 */
public class WarnOfStorageLimitsActivity extends AlertActivity
            implements DialogInterface.OnClickListener {
    private static final String LOG_TAG = "WarnOfStorageLimitsActivity";

    private static final int POSITIVE_BUTTON = AlertDialog.BUTTON_POSITIVE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Can't set this theme in the manifest. The resource compiler complains the
        // resource is internal and not visible. Without setting this theme, the window
        // gets a double window outline.
        this.setTheme(com.android.internal.R.style.Theme_Dialog_Alert);

        super.onCreate(savedInstanceState);

        // Set up the "dialog"
        final AlertController.AlertParams p = mAlertParams;
        p.mTitle = getString(R.string.storage_limits_title);
        p.mMessage = getString(R.string.storage_limits_message);
        p.mPositiveButtonText = getString(R.string.storage_limits_setting);
        p.mNegativeButtonText = getString(R.string.storage_limits_setting_dismiss);
        p.mPositiveButtonListener = this;
        setupAlert();
    }

    /**
     * {@inheritDoc}
     */
    public void onClick(DialogInterface dialog, int which) {

        if (which == POSITIVE_BUTTON) {
            Intent intent = new Intent(this,
                    MessagingPreferenceActivity.class);
            startActivity(intent);
        }

        // No matter what, finish the activity
        finish();
    }

}
