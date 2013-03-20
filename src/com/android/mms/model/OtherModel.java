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

public class OtherModel extends MediaModel {
    public OtherModel(Context context, String contentType, String src, Uri uri)
            throws MmsException {
        super(context, SmilHelper.ELEMENT_TAG_REF, contentType, src, uri);
    }

    static private class OtherModelType {
        public String mSrc;
        public String mContentType;
    };

    public static OtherModel OtherModelFactory (Context context,
            String contentType, String src, Uri uri) throws MmsException {
        OtherModel otherModel = null;
        if (contentType.equalsIgnoreCase(ContentType.TEXT_VCARD)) {
            otherModel = new VCardModel(context, contentType, src, uri);
        }
        else {
            throw new MmsException("Unsupport type of othermodel");
        }
        return otherModel;
    }

    public static OtherModel OtherModelFactory (Context context, Uri uri)
            throws MmsException {
        OtherModelType modelType = getModelTypeFromUri(context, uri);
        return OtherModelFactory(context, modelType.mContentType, modelType.mSrc, uri);
    }

    @Override
    public void handleEvent(Event evt) {

        notifyModelChanged(false);
    }

    @Override
    public boolean isOther() {
        return true;
    }

    private static OtherModelType getModelTypeFromUri(Context context, Uri uri)
            throws MmsException {
        String scheme = uri.getScheme();
        if (scheme.equals("content")) {
            return getModelTypeFromContentUri(context, uri);
        } else if (uri.getScheme().equals("file")) {
            return getModelTypeFromFile(uri);
        } else {
            throw new MmsException("Unsupport type");
        }
    }

    private static OtherModelType getModelTypeFromContentUri(Context context, Uri uri) {
        String tmpFileName = "vcard.vcf";
        OtherModelType modelType = new OtherModelType();
        // Display name
        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(uri, new String[] {
                OpenableColumns.DISPLAY_NAME }, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    tmpFileName = cursor.getString(0);
                    if (TextUtils.isEmpty(tmpFileName)) {
                        tmpFileName = context.getString(R.string.unnamed_vcard_name) + ".vcf";
                    }
                }
            } finally {
                cursor.close();
            }
        }
        if (tmpFileName.length() > 26) {
            tmpFileName = tmpFileName.substring(tmpFileName.length() - 26);
        }
        modelType.mSrc = tmpFileName;
        modelType.mContentType = ContentType.TEXT_VCARD;
        return modelType;
    }

    private static OtherModelType getModelTypeFromFile(Uri uri) throws MmsException {
        String path = uri.getPath();
        OtherModelType modelType = new OtherModelType();

        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        if (TextUtils.isEmpty(extension)) {
            // getMimeTypeFromExtension() doesn't handle spaces in filenames nor
            // can it handle
            // urlEncoded strings. Let's try one last time at finding the
            // extension.
            int dotPos = path.lastIndexOf('.');
            if (0 <= dotPos) {
                extension = path.substring(dotPos + 1);
            }
        }
        modelType.mSrc = path.substring(path.lastIndexOf('/') + 1);
        if ("vcf".equals(extension)) {
            modelType.mContentType = ContentType.TEXT_VCARD;
        } else {
            Log.e(TAG, "wrong uri:" + uri.toString());
            throw new MmsException("wrong uri:" + uri.toString());
        }
        return modelType;
    }
}
