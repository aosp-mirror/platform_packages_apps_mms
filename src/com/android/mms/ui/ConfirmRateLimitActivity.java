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

import static com.android.mms.util.RateController.RATE_LIMIT_CONFIRMED_ACTION;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;

import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.util.RateController;

public class ConfirmRateLimitActivity extends Activity {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    private long mCreateTime;
    private Handler mHandler;
    private Runnable mRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.confirm_rate_limit_activity);

        Button button = (Button) findViewById(R.id.btn_yes);
        button.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doAnswer(true);
            }
        });

        button = (Button) findViewById(R.id.btn_no);
        button.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doAnswer(false);
            }
        });

        mHandler = new Handler();
        mRunnable = new Runnable() {
            public void run() {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Runnable executed.");
                }
                doAnswer(false);
            }
        };

        mCreateTime = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();

        long delay = mCreateTime - System.currentTimeMillis()
                        + (RateController.ANSWER_TIMEOUT - 500);

        if (delay <= 0) {
            doAnswer(false);
        } else if (mHandler != null) {
            // Close this activity after certain seconds if no user action.
            mHandler.postDelayed(mRunnable, delay);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)
                && (event.getRepeatCount() == 0)) {
            doAnswer(false);
        }
        return super.onKeyDown(keyCode, event);
    }

    private void doAnswer(boolean answer) {
        Intent intent = new Intent(RATE_LIMIT_CONFIRMED_ACTION);
        intent.putExtra("answer", answer);
        sendBroadcast(intent);
        finish();
    }
}
