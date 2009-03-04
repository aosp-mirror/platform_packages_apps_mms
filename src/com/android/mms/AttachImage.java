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

package com.android.mms;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony.Mms.Intents;
import android.util.Config;
import android.util.Log;

import com.android.mms.ui.ComposeMessageActivity;

class AttachImage extends Activity {
    private static final String TAG = "AttachImage";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;
    private static final int TAG_COMPOSER = 1;

    public AttachImage() {
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (LOCAL_LOGV) {
            Log.v(TAG, "mms.AttachImage.onCreate with " + getIntent().getData());
        }

        Intent intent = new Intent();
        intent.setClass(this, ComposeMessageActivity.class);
        intent.putExtra(Intents.EXTRA_CONTENTS, new Uri[] { getIntent().getData() });
        intent.putExtra(Intents.EXTRA_TYPES,    new String[] { "image/*" });
        startActivityForResult(intent, TAG_COMPOSER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "mms.AttachImage.onActivityResult requstCode " + requestCode + "; result is " + resultCode);
        }

        switch (requestCode) {
            case TAG_COMPOSER: {
                finish();
                break;
            }
        }
    }

}

