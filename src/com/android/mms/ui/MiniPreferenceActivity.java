/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * */

package com.android.mms.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.android.mms.R;

/**
 * This activity is used by 3rd party apps to allow the user to turn on/off notifications in
 * the Messaging app.
 */
public class MiniPreferenceActivity extends Activity {
    public static String DISABLE_NOTIFICATIONS_INTENT =
        "com.android.mms.intent.action.MESSAGING_APP_NOTIFICATIONS";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        boolean notificationsEnabled = MessagingPreferenceActivity.getNotificationEnabled(this);

        if (!notificationsEnabled) {
            setResult(RESULT_OK);
            finish();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.setMessage(getResources()
                .getString(R.string.disable_notifications_dialog_message))
            .setCancelable(true)
            .setPositiveButton(R.string.yes, mDialogButtonListener)
            .setNegativeButton(R.string.no, mDialogButtonListener)
            .show();

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                if (!MiniPreferenceActivity.this.isFinishing()) {
                    finish();
                }
            }
        });
    }

    private DialogInterface.OnClickListener mDialogButtonListener =
        new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // turn off Messaging notifications
                    MessagingPreferenceActivity.enableNotifications(false,
                            MiniPreferenceActivity.this);
                    setResult(RESULT_OK);
                } else {
                    setResult(RESULT_CANCELED);
                }
                finish();
            }
    };
}
