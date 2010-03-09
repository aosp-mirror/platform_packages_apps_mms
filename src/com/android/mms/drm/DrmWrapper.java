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

package com.android.mms.drm;

import com.google.android.mms.ContentType;

import android.drm.mobile1.DrmException;
import android.drm.mobile1.DrmRawContent;
import android.drm.mobile1.DrmRights;
import android.drm.mobile1.DrmRightsManager;
import android.net.Uri;
import android.util.Config;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The Drm Wrapper.
 */
public class DrmWrapper {
    /**
     * The DRM right object.
     */
    private DrmRights mRight;

    /**
     * The DrmRawContent.
     */
    private final DrmRawContent mDrmObject;

    private final Uri mDataUri;
    private final byte[] mData;
    /**
     * The decrypted data.
     */
    private byte[] mDecryptedData;

    /**
     * The log tag.
     */
    private static final String LOG_TAG = "DrmWrapper";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    /**
     * Constructor.
     * @param uri
     */
    public DrmWrapper(String drmContentType, Uri uri, byte[] drmData)
            throws DrmException, IOException {
        if ((drmContentType == null) || (drmData == null)) {
            throw new IllegalArgumentException(
                    "Content-Type or data may not be null.");
        }

        mDataUri = uri;
        mData = drmData;

        ByteArrayInputStream drmDataStream = new ByteArrayInputStream(drmData);
        mDrmObject = new DrmRawContent(drmDataStream, drmDataStream.available(),
                                       drmContentType);
        // Install rights if necessary.
        if (!isRightsInstalled()) {
            if (LOCAL_LOGV) {
                Log.v(LOG_TAG, "DRM rights not installed yet.");
            }
            installRights(drmData);
        }
    }

    /**
     * Get permission type for the decrypted content-type.
     *
     * @return the permission
     */
    private int getPermission() {
        String contentType = mDrmObject.getContentType();

        if (ContentType.isAudioType(contentType) ||
                ContentType.isVideoType(contentType)) {
            return DrmRights.DRM_PERMISSION_PLAY;
        }
        return DrmRights.DRM_PERMISSION_DISPLAY;
    }

    /**
     * Get decrypted data.
     *
     * @return the decrypted content if decryption was successful.
     * @throws IOException
     */
    public byte[] getDecryptedData() throws IOException {
        if ((mDecryptedData == null) && (mRight != null)) {
            // Decrypt it.
            InputStream decryptedDataStream = mDrmObject.getContentInputStream(mRight);
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[256];
                int len;
                while ((len = decryptedDataStream.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                mDecryptedData = baos.toByteArray();
            } finally {
                try {
                    decryptedDataStream.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, e.getMessage(), e);
                }
            }
        }

        if (mDecryptedData != null) {
            byte[] decryptedData = new byte[mDecryptedData.length];
            System.arraycopy(mDecryptedData, 0, decryptedData, 0, mDecryptedData.length);
            return decryptedData;
        }
        return null;
    }

    /**
     * Consume the rights.
     *
     * @return true if consume success
     *         false if consume failure
     */
    public boolean consumeRights() {
        if (mRight == null) {
            return false;
        }

        return mRight.consumeRights(getPermission());
    }

    /**
     * Install Right.
     *
     * @param rightData right's data
     * @throws IOException
     * @throws DrmException
     */
    public void installRights(byte[] rightData) throws DrmException, IOException {
        if (rightData == null) {
            throw new DrmException("Right data may not be null.");
        }

        if (LOCAL_LOGV) {
            Log.v(LOG_TAG, "Installing DRM rights.");
        }

        ByteArrayInputStream rightDataStream = new ByteArrayInputStream(rightData);
        mRight = DrmRightsManager.getInstance().installRights(
                rightDataStream, rightData.length,
                DrmRawContent.DRM_MIMETYPE_MESSAGE_STRING);
    }

    /**
     * Check whether the DRM object's right is existed. If not, we should
     * download it.
     *
     * @return true if it is existed
     *         false if not
     */
    public boolean isRightsInstalled() {
        if (mRight != null) {
            return true;
        }

        mRight = DrmRightsManager.getInstance().queryRights(mDrmObject);
        return mRight != null ? true : false;
    }

    /**
     * Check whether this DRM object can be forwarded.
     *
     * @return true if this object can be forwarded
     *         false if not
     */
    public boolean isAllowedToForward() {
        if (DrmRawContent.DRM_SEPARATE_DELIVERY != mDrmObject.getRawType()) {
            return false;
        }
        return true;
    }

    /**
     * Get URL of right.
     *
     * @return the right's URL
     */
    public String getRightsAddress() {
        if (mDrmObject == null) {
            return null;
        }
        return mDrmObject.getRightsAddress();
    }

    /**
     * Get the decrypted object's content-type.
     *
     * @return the content-type
     */
    public String getContentType() {
        return mDrmObject.getContentType();
    }

    public Uri getOriginalUri() {
        return mDataUri;
    }

    public byte[] getOriginalData() {
        return mData;
    }
}
