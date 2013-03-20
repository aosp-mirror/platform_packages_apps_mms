/*
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
package com.android.mms.model;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.mms.R;
import com.android.mms.TempFileProvider;

import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.w3c.dom.events.Event;

public class VCardModel extends OtherModel {
    public VCardModel(Context context, String contentType, String src, Uri uri)
            throws MmsException {
        super(context, contentType, src, uri);
    }

    private Uri getViewVcardTempUri() {
        OutputStream os = null;
        InputStream is = null;
        try {
            is = mContext.getContentResolver().openInputStream(mUri);
            String tmpFileName = getSrc();
            tmpFileName = tmpFileName.replace("&", "a");
            tmpFileName = tmpFileName.replace("/", "");
            File tempFile = new File(TempFileProvider.getScrapPath(mContext, tmpFileName));
            File parentFile = tempFile.getParentFile();
            if (!parentFile.exists() && !parentFile.mkdirs()) {
                Log.e(TAG, "[TempFileProvider] tempStoreFd: " + parentFile.getPath()
                        + "does not exist!");
                return null;
            }
            os = new FileOutputStream(tempFile);
            byte[] buffer = new byte[256];
            for (int len = 0; (len = is.read(buffer)) != -1;) {
                os.write(buffer, 0, len);
            }
            return Uri.fromFile(tempFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to open Input/Output stream.", e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read/write data.", e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException while closing: " + os, e);
                } // Ignore
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException while closing: " + is, e);
                } // Ignore
            }
        }
        return null;
    }

    public Uri getUri() {
        Uri intentUri = mUri;
        if (ContentType.TEXT_VCARD.equalsIgnoreCase(mContentType)) {
            Uri uri = getViewVcardTempUri();
            if (uri != null) {
                intentUri = uri;
            }
        }
        return intentUri;
    }
}
