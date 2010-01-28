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

package com.android.mms.drm;

import android.database.sqlite.SqliteWrapper;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

public class DrmUtils {
    private static final String TAG = "DrmUtils";
    private static final Uri DRM_TEMP_URI = Uri.parse("content://mms/drm");

    private DrmUtils() {
    }

    public static void cleanupStorage(Context context) {
        SqliteWrapper.delete(context, context.getContentResolver(),
                DRM_TEMP_URI, null, null);
    }

    public static Uri insert(Context context, DrmWrapper drmObj)
            throws IOException {
        ContentResolver cr = context.getContentResolver();
        Uri uri = SqliteWrapper.insert(context, cr, DRM_TEMP_URI,
                                       new ContentValues(0));
        OutputStream os = null;
        try {
            os = cr.openOutputStream(uri);
            byte[] data = drmObj.getDecryptedData();
            if (data != null) {
                os.write(data);
            }
            return uri;
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }
}
