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
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.android.mms.R;

/**
 * This activity provides the function to edit the text content of given slide.
 */
public class EditSlideTextActivity extends Activity {
    public static final String SLIDE_INDEX = "slide_index";
    public static final String SLIDE_TOTAL = "slide_total";
    public static final String TEXT_CONTENT = "text";

    private TextView mLabel;
    private Button mDone;
    private EditText mText;

    private int mCurSlide;
    private int mTotal;

    private Bundle mState;
    //  State.
    private final static String STATE = "state";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.edit_slide_text);

        String text;
        if (icicle == null) {
            // Get extra from intent.
            Intent intent = getIntent();
            mCurSlide = intent.getIntExtra(SLIDE_INDEX, 1);
            mTotal = intent.getIntExtra(SLIDE_TOTAL, 1);
            text = intent.getStringExtra(TEXT_CONTENT);
        } else {
            mState = icicle.getBundle(STATE);

            mCurSlide = mState.getInt(SLIDE_INDEX, 1);
            mTotal = mState.getInt(SLIDE_TOTAL, 1);
            text = mState.getString(TEXT_CONTENT);
        }

        // Label.
        mLabel = (TextView) findViewById(R.id.label);
        mLabel.setText("Edit text for slide " + (mCurSlide + 1) + "/" + mTotal);

        // Input text field.
        mText = (EditText) findViewById(R.id.text);
        mText.setText(text);
        mText.setOnKeyListener(mOnKeyListener);

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
        mState.putString(TEXT_CONTENT, mText.getText().toString());

        outState.putBundle(STATE, mState);
    }

    private final OnKeyListener mOnKeyListener = new OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getKeyCode() != KeyEvent.ACTION_DOWN) {
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
        setResult(RESULT_OK, new Intent(mText.getText().toString()));
        finish();
    }
}
