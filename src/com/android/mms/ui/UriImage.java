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

import com.android.mms.model.ImageModel;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.util.SqliteWrapper;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.Telephony.Mms.Part;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class UriImage {
    private static final String TAG = "UriImage";
    private static final boolean DEBUG = true;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private final Context mContext;
    private final Uri mUri;
    private String mContentType;
    private String mPath;
    private String mSrc;
    private int mWidth;
    private int mHeight;

    public UriImage(Context context, Uri uri) {
        if ((null == context) || (null == uri)) {
            throw new IllegalArgumentException();
        }

        String scheme = uri.getScheme();
        if (scheme.equals("content")) {
            initFromContentUri(context, uri);
        } else if (uri.getScheme().equals("file")) {
            initFromFile(context, uri);
        }
        
        mSrc = mPath.substring(mPath.lastIndexOf('/') + 1);
        
        // Some MMSCs appear to have problems with filenames
        // containing a space.  So just replace them with
        // underscores in the name, which is typically not
        // visible to the user anyway.
        mSrc = mSrc.replace(' ', '_');

        mContext = context;
        mUri = uri;

        decodeBoundsInfo();
    }

    private void initFromFile(Context context, Uri uri) {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        mContentType = mimeTypeMap.getMimeTypeFromExtension(extension);
        if (mContentType == null) {
            throw new IllegalArgumentException(
                    "Unable to determine extension for " + uri.toString());
        }
        mPath = uri.getPath();
    }
    
    private void initFromContentUri(Context context, Uri uri) {
        Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                            uri, null, null, null, null);

        if (c == null) {
            throw new IllegalArgumentException(
                    "Query on " + uri + " returns null result.");
        }

        try {
            if ((c.getCount() != 1) || !c.moveToFirst()) {
                throw new IllegalArgumentException(
                        "Query on " + uri + " returns 0 or multiple rows.");
            }

            String filePath;
            if (ImageModel.isMmsUri(uri)) {
                filePath = c.getString(c.getColumnIndexOrThrow(Part.FILENAME));
                if (TextUtils.isEmpty(filePath)) {
                    filePath = c.getString(
                            c.getColumnIndexOrThrow(Part._DATA));
                }
                mContentType = c.getString(
                        c.getColumnIndexOrThrow(Part.CONTENT_TYPE));
            } else {
                filePath = c.getString(
                        c.getColumnIndexOrThrow(Images.Media.DATA));
                mContentType = c.getString(
                        c.getColumnIndexOrThrow(Images.Media.MIME_TYPE));
            }
            mPath = filePath;
        } finally {
            c.close();
        }
    }

    private void decodeBoundsInfo() {
        InputStream input = null;
        try {
            input = mContext.getContentResolver().openInputStream(mUri);
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, opt);
            mWidth = opt.outWidth;
            mHeight = opt.outHeight;
        } catch (FileNotFoundException e) {
            // Ignore
            Log.e(TAG, "IOException caught while opening stream", e);
        } finally {
            if (null != input) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(TAG, "IOException caught while closing stream", e);
                }
            }
        }
    }

    public String getContentType() {
        return mContentType;
    }

    public String getSrc() {
        return mSrc;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public PduPart getResizedImageAsPart(int widthLimit, int heightLimit) {
        PduPart part = new PduPart();

        byte[] data = getResizedImageData(widthLimit, heightLimit);
        if (data == null) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Resize image failed.");
            }
            return null;
        }

        part.setData(data);
        part.setContentType(getContentType().getBytes());
        String src = getSrc();
        byte[] srcBytes = src.getBytes();
        part.setContentLocation(srcBytes);
        part.setFilename(srcBytes);
        part.setContentId(src.substring(0, src.lastIndexOf(".")).getBytes());

        return part;
    }

    private byte[] getResizedImageData(int widthLimit, int heightLimit) {
        int outWidth = mWidth;
        int outHeight = mHeight;

        int s = 1;
        while ((outWidth / s > widthLimit) || (outHeight / s > heightLimit)) {
            s *= 2;
        }
        if (LOCAL_LOGV) {
            Log.v(TAG, "outWidth=" + outWidth / s
                    + " outHeight=" + outHeight / s);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = s;

        InputStream input = null;
        try {
            input = mContext.getContentResolver().openInputStream(mUri);
            Bitmap b = BitmapFactory.decodeStream(input, null, options);
            if (b == null) {
                return null;
            }

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            b.compress(CompressFormat.JPEG, MessageUtils.IMAGE_COMPRESSION_QUALITY, os);
            return os.toByteArray();
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }
}
