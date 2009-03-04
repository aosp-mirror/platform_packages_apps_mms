/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.Window;

import com.android.mms.R;
import com.android.mms.transaction.SmsReceiverService;

/**
 * Display a class-zero SMS message to the user.  Wait for the user to
 * dismiss it.
 */
public class ClassZeroActivity extends Activity {
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawableResource(
                R.drawable.class_zero_background);

        CharSequence messageChars =  getIntent().getCharSequenceExtra(
                SmsReceiverService.CLASS_ZERO_BODY_KEY);

        new AlertDialog.Builder(this)
                .setMessage(messageChars)
                .setPositiveButton(android.R.string.ok, mOkListener)
                .setCancelable(false)
                .show();
    }

    private final OnClickListener mOkListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            ClassZeroActivity.this.finish();
        }
    };
}
