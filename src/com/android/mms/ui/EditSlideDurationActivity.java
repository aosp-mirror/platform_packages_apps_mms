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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mms.LogTag;
import com.android.mms.R;

/**
 * This activity provides the function to edit the duration of given slide.
 */
public class EditSlideDurationActivity  extends Activity {
    public static final String SLIDE_INDEX = "slide_index";
    public static final String SLIDE_TOTAL = "slide_total";
    public static final String SLIDE_DUR   = "dur";

    private TextView mLabel;
    private Button mDone;
    private EditText mDur;

    private int mCurSlide;
    private int mTotal;

    private Bundle mState;
    //  State.
    private final static String STATE = "state";
    private final static String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.edit_slide_duration);

        int dur;
        if (icicle == null) {
            // Get extra from intent.
            Intent intent = getIntent();
            mCurSlide = intent.getIntExtra(SLIDE_INDEX, 1);
            mTotal = intent.getIntExtra(SLIDE_TOTAL, 1);
            dur = intent.getIntExtra(SLIDE_DUR, 8);
        } else {
            mState = icicle.getBundle(STATE);

            mCurSlide = mState.getInt(SLIDE_INDEX, 1);
            mTotal = mState.getInt(SLIDE_TOTAL, 1);
            dur = mState.getInt(SLIDE_DUR, 8);
        }

        // Label.
        mLabel = (TextView) findViewById(R.id.label);
        mLabel.setText(getString(R.string.duration_selector_title) + " " + (mCurSlide + 1) + "/" + mTotal);

        // Input text field.
        mDur = (EditText) findViewById(R.id.text);
        mDur.setText(String.valueOf(dur));
        mDur.setOnKeyListener(mOnKeyListener);

        // Done button.
        mDone = (Button) findViewById(R.id.done);
        mDone.setOnClickListener(mOnDoneClickListener);
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mState = new Bundle();
        mState.putInt(SLIDE_INDEX, mCurSlide);
        mState.putInt(SLIDE_TOTAL, mTotal);

        int durValue;
        try {
            durValue = Integer.parseInt(mDur.getText().toString());
        } catch (NumberFormatException e) {
            // On an illegal value, set the duration back to a default value.
            durValue = 5;
        }
        mState.putInt(SLIDE_DUR, durValue);

        outState.putBundle(STATE, mState);
    }

    private final OnKeyListener mOnKeyListener = new OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    // Edit complete.
                    editDone();
                    break;
            }
            return false;
        }
    };

    private final OnClickListener mOnDoneClickListener = new OnClickListener() {
        public void onClick(View v) {
            // Edit complete.
            editDone();
        }
    };

    protected void editDone() {
        // Set result to parent, and close window.
        // Check the duration.
        String dur = mDur.getText().toString();
        int durValue = 0;
        try {
            durValue = Integer.valueOf(dur);
        } catch (NumberFormatException e) {
            notifyUser(R.string.duration_not_a_number);
            return;
        }
        if (durValue <= 0) {
            notifyUser(R.string.duration_zero);
            return;
        }

        // Set result.
        setResult(RESULT_OK, new Intent(mDur.getText().toString()));
        finish();
    }

    private void notifyUser(int msgId) {
        mDur.requestFocus();
        mDur.selectAll();
        Toast.makeText(this, msgId, Toast.LENGTH_SHORT).show();
        return;
    }
}
