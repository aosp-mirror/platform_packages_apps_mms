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
import android.util.Config;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;


public class ImageModel extends RegionMediaModel {
    private static final String TAG = "Mms/image";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private static final int THUMBNAIL_BOUNDS_LIMIT = 480;

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
            MessageUtils.writeHprofDataToFile();
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
        if (image == null) {
            throw new ExceedMessageSizeException("No room to resize picture: " + getUri());
        }
        PduPart part = image.getResizedImageAsPart(
                MmsConfig.getMaxImageWidth(),
                MmsConfig.getMaxImageHeight(),
                byteLimit);

        PduPersister persister = PduPersister.getPduPersister(mContext);
        this.mSize = part.getData().length;
        Uri newUri = persister.persistPart(part, messageId);
        setUri(newUri);
    }
}
