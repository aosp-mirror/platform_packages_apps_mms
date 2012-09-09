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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.Telephony.Mms.Part;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.mms.LogTag;
import com.android.mms.model.ImageModel;
import com.google.android.mms.ContentType;
import com.google.android.mms.pdu.PduPart;

public class UriImage {
    private static final String TAG = "Mms/image";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

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

        mContext = context;
        mUri = uri;

        decodeBoundsInfo();

        if (LOCAL_LOGV) {
            Log.v(TAG, "UriImage uri: " + uri + " mPath: " + mPath + " mWidth: " + mWidth +
                    " mHeight: " + mHeight);
        }
    }

    private void initFromFile(Context context, Uri uri) {
        mPath = uri.getPath();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(mPath);
        if (TextUtils.isEmpty(extension)) {
            // getMimeTypeFromExtension() doesn't handle spaces in filenames nor can it handle
            // urlEncoded strings. Let's try one last time at finding the extension.
            int dotPos = mPath.lastIndexOf('.');
            if (0 <= dotPos) {
                extension = mPath.substring(dotPos + 1);
            }
        }
        mContentType = mimeTypeMap.getMimeTypeFromExtension(extension);
        // It's ok if mContentType is null. Eventually we'll show a toast telling the
        // user the picture couldn't be attached.

        buildSrcFromPath();
    }

    private void buildSrcFromPath() {
        mSrc = mPath.substring(mPath.lastIndexOf('/') + 1);

        if(mSrc.startsWith(".") && mSrc.length() > 1) {
            mSrc = mSrc.substring(1);
        }

        // Some MMSCs appear to have problems with filenames
        // containing a space.  So just replace them with
        // underscores in the name, which is typically not
        // visible to the user anyway.
        mSrc = mSrc.replace(' ', '_');
    }

    private void initFromContentUri(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        Cursor c = SqliteWrapper.query(context, resolver,
                            uri, null, null, null, null);

        mSrc = null;
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
                filePath = uri.getPath();
                try {
                    mContentType = c.getString(
                            c.getColumnIndexOrThrow(Images.Media.MIME_TYPE)); // mime_type
                } catch (IllegalArgumentException e) {
                    try {
                        mContentType = c.getString(c.getColumnIndexOrThrow("mimetype"));
                    } catch (IllegalArgumentException ex) {
                        mContentType = resolver.getType(uri);
                        Log.v(TAG, "initFromContentUri: " + uri + ", getType => " + mContentType);
                    }
                }

                // use the original filename if possible
                int nameIndex = c.getColumnIndex(Images.Media.DISPLAY_NAME);
                if (nameIndex != -1) {
                    mSrc = c.getString(nameIndex);
                    if (!TextUtils.isEmpty(mSrc)) {
                        // Some MMSCs appear to have problems with filenames
                        // containing a space.  So just replace them with
                        // underscores in the name, which is typically not
                        // visible to the user anyway.
                        mSrc = mSrc.replace(' ', '_');
                    } else {
                        mSrc = null;
                    }
                }
            }
            mPath = filePath;
            if (mSrc == null) {
                buildSrcFromPath();
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "initFromContentUri couldn't load image uri: " + uri, e);
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

    public String getPath() {
        return mPath;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    /**
     * Get a version of this image resized to fit the given dimension and byte-size limits. Note
     * that the content type of the resulting PduPart may not be the same as the content type of
     * this UriImage; always call {@link PduPart#getContentType()} to get the new content type.
     *
     * @param widthLimit The width limit, in pixels
     * @param heightLimit The height limit, in pixels
     * @param byteLimit The binary size limit, in bytes
     * @return A new PduPart containing the resized image data
     */
    public PduPart getResizedImageAsPart(int widthLimit, int heightLimit, int byteLimit) {
        PduPart part = new PduPart();

        byte[] data =  getResizedImageData(mWidth, mHeight,
                widthLimit, heightLimit, byteLimit, mUri, mContext);
        if (data == null) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Resize image failed.");
            }
            return null;
        }

        part.setData(data);
        // getResizedImageData ALWAYS compresses to JPEG, regardless of the original content type
        part.setContentType(ContentType.IMAGE_JPEG.getBytes());

        return part;
    }

    private static final int NUMBER_OF_RESIZE_ATTEMPTS = 4;

    /**
     * Resize and recompress the image such that it fits the given limits. The resulting byte
     * array contains an image in JPEG format, regardless of the original image's content type.
     * @param widthLimit The width limit, in pixels
     * @param heightLimit The height limit, in pixels
     * @param byteLimit The binary size limit, in bytes
     * @return A resized/recompressed version of this image, in JPEG format
     */
    public static byte[] getResizedImageData(int width, int height,
            int widthLimit, int heightLimit, int byteLimit, Uri uri, Context context) {
        int outWidth = width;
        int outHeight = height;

        float scaleFactor = 1.F;
        while ((outWidth * scaleFactor > widthLimit) || (outHeight * scaleFactor > heightLimit)) {
            scaleFactor *= .75F;
        }

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "getResizedBitmap: wlimit=" + widthLimit +
                    ", hlimit=" + heightLimit + ", sizeLimit=" + byteLimit +
                    ", width=" + width + ", height=" + height +
                    ", initialScaleFactor=" + scaleFactor +
                    ", uri=" + uri);
        }

        InputStream input = null;
        try {
            ByteArrayOutputStream os = null;
            int attempts = 1;
            int sampleSize = 1;
            BitmapFactory.Options options = new BitmapFactory.Options();
            int quality = MessageUtils.IMAGE_COMPRESSION_QUALITY;
            Bitmap b = null;

            // In this loop, attempt to decode the stream with the best possible subsampling (we
            // start with 1, which means no subsampling - get the original content) without running
            // out of memory.
            do {
                input = context.getContentResolver().openInputStream(uri);
                options.inSampleSize = sampleSize;
                try {
                    b = BitmapFactory.decodeStream(input, null, options);
                    if (b == null) {
                        return null;    // Couldn't decode and it wasn't because of an exception,
                                        // bail.
                    }
                } catch (OutOfMemoryError e) {
                    Log.w(TAG, "getResizedBitmap: img too large to decode (OutOfMemoryError), " +
                            "may try with larger sampleSize. Curr sampleSize=" + sampleSize);
                    sampleSize *= 2;    // works best as a power of two
                    attempts++;
                    continue;
                } finally {
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                }
            } while (b == null && attempts < NUMBER_OF_RESIZE_ATTEMPTS);

            if (b == null) {
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)
                        && attempts >= NUMBER_OF_RESIZE_ATTEMPTS) {
                    Log.v(TAG, "getResizedImageData: gave up after too many attempts to resize");
                }
                return null;
            }

            boolean resultTooBig = true;
            attempts = 1;   // reset count for second loop
            // In this loop, we attempt to compress/resize the content to fit the given dimension
            // and file-size limits.
            do {
                try {
                    if (options.outWidth > widthLimit || options.outHeight > heightLimit ||
                            (os != null && os.size() > byteLimit)) {
                        // The decoder does not support the inSampleSize option.
                        // Scale the bitmap using Bitmap library.
                        int scaledWidth = (int)(outWidth * scaleFactor);
                        int scaledHeight = (int)(outHeight * scaleFactor);

                        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                            Log.v(TAG, "getResizedImageData: retry scaling using " +
                                    "Bitmap.createScaledBitmap: w=" + scaledWidth +
                                    ", h=" + scaledHeight);
                        }

                        b = Bitmap.createScaledBitmap(b, scaledWidth, scaledHeight, false);
                        if (b == null) {
                            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                                Log.v(TAG, "Bitmap.createScaledBitmap returned NULL!");
                            }
                            return null;
                        }
                    }

                    // Compress the image into a JPG. Start with MessageUtils.IMAGE_COMPRESSION_QUALITY.
                    // In case that the image byte size is still too large reduce the quality in
                    // proportion to the desired byte size.
                    os = new ByteArrayOutputStream();
                    b.compress(CompressFormat.JPEG, quality, os);
                    int jpgFileSize = os.size();
                    if (jpgFileSize > byteLimit) {
                        quality = (quality * byteLimit) / jpgFileSize;  // watch for int division!
                        if (quality < MessageUtils.MINIMUM_IMAGE_COMPRESSION_QUALITY) {
                            quality = MessageUtils.MINIMUM_IMAGE_COMPRESSION_QUALITY;
                        }

                        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                            Log.v(TAG, "getResizedImageData: compress(2) w/ quality=" + quality);
                        }

                        os = new ByteArrayOutputStream();
                        b.compress(CompressFormat.JPEG, quality, os);
                    }
                } catch (java.lang.OutOfMemoryError e) {
                    Log.w(TAG, "getResizedImageData - image too big (OutOfMemoryError), will try "
                            + " with smaller scale factor, cur scale factor: " + scaleFactor);
                    // fall through and keep trying with a smaller scale factor.
                }
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.v(TAG, "attempt=" + attempts
                            + " size=" + (os == null ? 0 : os.size())
                            + " width=" + outWidth * scaleFactor
                            + " height=" + outHeight * scaleFactor
                            + " scaleFactor=" + scaleFactor
                            + " quality=" + quality);
                }
                scaleFactor *= .75F;
                attempts++;
                resultTooBig = os == null || os.size() > byteLimit;
            } while (resultTooBig && attempts < NUMBER_OF_RESIZE_ATTEMPTS);
            b.recycle();        // done with the bitmap, release the memory
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE) && resultTooBig) {
                Log.v(TAG, "getResizedImageData returning NULL because the result is too big: " +
                        " requested max: " + byteLimit + " actual: " + os.size());
            }

            return resultTooBig ? null : os.toByteArray();
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }
}
