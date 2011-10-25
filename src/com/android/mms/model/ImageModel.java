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

package com.android.mms.model;

import com.android.mms.ContentRestrictionException;
import com.android.mms.ExceedMessageSizeException;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.dom.smil.SmilMediaElementImpl;
import android.drm.mobile1.DrmException;
import com.android.mms.drm.DrmWrapper;
import com.android.mms.ui.UriImage;
import com.android.mms.ui.MessageUtils;

import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;

import org.w3c.dom.events.Event;
import org.w3c.dom.smil.ElementTime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class ImageModel extends RegionMediaModel {
    @SuppressWarnings("hiding")
    private static final String TAG = "Mms/image";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    private static final int THUMBNAIL_BOUNDS_LIMIT = 480;

    /**
     * These are the image content types that MMS supports. Anything else needs to be transcoded
     * into one of these content types before being sent over MMS.
     */
    private static final Set<String> SUPPORTED_MMS_IMAGE_CONTENT_TYPES =
        new HashSet<String>(Arrays.asList(new String[] {
                "image/jpeg",
            }));

    private int mWidth;
    private int mHeight;
    private SoftReference<Bitmap> mBitmapCache = new SoftReference<Bitmap>(null);

    public ImageModel(Context context, Uri uri, RegionModel region)
            throws MmsException {
        super(context, SmilHelper.ELEMENT_TAG_IMAGE, uri, region);
        initModelFromUri(uri);
        checkContentRestriction();
    }

    public ImageModel(Context context, String contentType, String src,
            Uri uri, RegionModel region) throws DrmException, MmsException {
        super(context, SmilHelper.ELEMENT_TAG_IMAGE,
                contentType, src, uri, region);
        decodeImageBounds();
    }

    public ImageModel(Context context, String contentType, String src,
            DrmWrapper wrapper, RegionModel regionModel) throws IOException {
        super(context, SmilHelper.ELEMENT_TAG_IMAGE, contentType, src,
                wrapper, regionModel);
    }

    private void initModelFromUri(Uri uri) throws MmsException {
        UriImage uriImage = new UriImage(mContext, uri);

        mContentType = uriImage.getContentType();
        if (TextUtils.isEmpty(mContentType)) {
            throw new MmsException("Type of media is unknown.");
        }
        mSrc = uriImage.getSrc();
        mWidth = uriImage.getWidth();
        mHeight = uriImage.getHeight();

        if (LOCAL_LOGV) {
            Log.v(TAG, "New ImageModel created:"
                    + " mSrc=" + mSrc
                    + " mContentType=" + mContentType
                    + " mUri=" + uri);
        }
    }

    private void decodeImageBounds() throws DrmException {
        UriImage uriImage = new UriImage(mContext, getUriWithDrmCheck());
        mWidth = uriImage.getWidth();
        mHeight = uriImage.getHeight();

        if (LOCAL_LOGV) {
            Log.v(TAG, "Image bounds: " + mWidth + "x" + mHeight);
        }
    }

    // EventListener Interface
    @Override
    public void handleEvent(Event evt) {
        if (evt.getType().equals(SmilMediaElementImpl.SMIL_MEDIA_START_EVENT)) {
            mVisible = true;
        } else if (mFill != ElementTime.FILL_FREEZE) {
            mVisible = false;
        }

        notifyModelChanged(false);
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    protected void checkContentRestriction() throws ContentRestrictionException {
        ContentRestriction cr = ContentRestrictionFactory.getContentRestriction();
        cr.checkImageContentType(mContentType);
    }

    public Bitmap getBitmap() {
        return internalGetBitmap(getUri());
    }

    public Bitmap getBitmapWithDrmCheck() throws DrmException {
        return internalGetBitmap(getUriWithDrmCheck());
    }

    private Bitmap internalGetBitmap(Uri uri) {
        Bitmap bm = mBitmapCache.get();
        if (bm == null) {
            try {
                bm = createThumbnailBitmap(THUMBNAIL_BOUNDS_LIMIT, uri);
                if (bm != null) {
                    mBitmapCache = new SoftReference<Bitmap>(bm);
                }
            } catch (OutOfMemoryError ex) {
                // fall through and return a null bitmap. The callers can handle a null
                // result and show R.drawable.ic_missing_thumbnail_picture
            }
        }
        return bm;
    }

    private Bitmap createThumbnailBitmap(int thumbnailBoundsLimit, Uri uri) {
        int outWidth = mWidth;
        int outHeight = mHeight;

        int s = 1;
        while ((outWidth / s > thumbnailBoundsLimit)
                || (outHeight / s > thumbnailBoundsLimit)) {
            s *= 2;
        }
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "createThumbnailBitmap: scale=" + s + ", w=" + outWidth / s
                    + ", h=" + outHeight / s);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = s;

        InputStream input = null;
        try {
            input = mContext.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(input, null, options);
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        } catch (OutOfMemoryError ex) {
            if (DEBUG) {
                MessageUtils.writeHprofDataToFile();
            }
            throw ex;
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

    @Override
    public boolean getMediaResizable() {
        return true;
    }

    @Override
    protected void resizeMedia(int byteLimit, long messageId) throws MmsException {
        UriImage image = new UriImage(mContext, getUri());

        int widthLimit = MmsConfig.getMaxImageWidth();
        int heightLimit = MmsConfig.getMaxImageHeight();
        int size = getMediaSize();
        // In mms_config.xml, the max width has always been declared larger than the max height.
        // Swap the width and height limits if necessary so we scale the picture as little as
        // possible.
        if (image.getHeight() > image.getWidth()) {
            int temp = widthLimit;
            widthLimit = heightLimit;
            heightLimit = temp;
        }

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "resizeMedia size: " + size + " image.getWidth(): "
                    + image.getWidth() + " widthLimit: " + widthLimit
                    + " image.getHeight(): " + image.getHeight()
                    + " heightLimit: " + heightLimit
                    + " image.getContentType(): " + image.getContentType());
        }

        // Check if we're already within the limits - in which case we don't need to resize.
        // The size can be zero here, even when the media has content. See the comment in
        // MediaModel.initMediaSize. Sometimes it'll compute zero and it's costly to read the
        // whole stream to compute the size. When we call getResizedImageAsPart(), we'll correctly
        // set the size.
        if (size != 0 && size <= byteLimit &&
                image.getWidth() <= widthLimit &&
                image.getHeight() <= heightLimit &&
                SUPPORTED_MMS_IMAGE_CONTENT_TYPES.contains(image.getContentType())) {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.v(TAG, "resizeMedia - already sized");
            }
            return;
        }

        PduPart part = image.getResizedImageAsPart(
                widthLimit,
                heightLimit,
                byteLimit);

        if (part == null) {
            throw new ExceedMessageSizeException("Not enough memory to turn image into part: " +
                    getUri());
        }

        // Update the content type because it may have changed due to resizing/recompressing
        mContentType = new String(part.getContentType());

        String src = getSrc();
        byte[] srcBytes = src.getBytes();
        part.setContentLocation(srcBytes);
        int period = src.lastIndexOf(".");
        byte[] contentId = period != -1 ? src.substring(0, period).getBytes() : srcBytes;
        part.setContentId(contentId);

        PduPersister persister = PduPersister.getPduPersister(mContext);
        this.mSize = part.getData().length;

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "resizeMedia mSize: " + mSize);
        }

        Uri newUri = persister.persistPart(part, messageId);
        setUri(newUri);
    }
}
