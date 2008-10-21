package com.android.mms.ui;

import com.android.mms.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.Window;

/**
 * Quick dirty activity to display a dialog from services
 */
public class AlertDialogActivity extends Activity {
    // TODO combine this class with ClassZeroActivity

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawableResource(
                R.drawable.class_zero_background);
        
        String title = getIntent().getStringExtra("title");
        String message = getIntent().getStringExtra("message");

        new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, mOkListener)
        .setCancelable(false)
        .show();
    }

    private final OnClickListener mOkListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            AlertDialogActivity.this.finish();
        }
    };
}
