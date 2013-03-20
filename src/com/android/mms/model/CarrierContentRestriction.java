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

import java.util.ArrayList;

import android.content.ContentResolver;
import android.util.Log;

import com.android.mms.ContentRestrictionException;
import com.android.mms.ExceedMessageSizeException;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.ResolutionException;
import com.android.mms.UnsupportContentTypeException;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsCreationMode;

public class CarrierContentRestriction implements ContentRestriction {

    private ArrayList<String> mSupportedImageTypes;
    private ArrayList<String> mSupportedAudioTypes;
    private ArrayList<String> mSupportedVideoTypes;
    private int mMsgSizeLimit;
    private int mImageWidthLimit;
    private int mImageHeightLimit;
    private int mCreationMode;

    private static final boolean DEBUG = true;

    public CarrierContentRestriction(int creationMode) {
        mCreationMode = creationMode;
        mSupportedImageTypes = ContentType.getImageTypes(mCreationMode);
        mSupportedAudioTypes = ContentType.getAudioTypes(mCreationMode);
        mSupportedVideoTypes = ContentType.getVideoTypes(mCreationMode);
        Log.d(LogTag.APP, "CarrierContentRestriction creationMode: " + creationMode);

        switch (creationMode) {
            case MmsCreationMode.CREATION_MODE_RESTRICTED:
            case MmsCreationMode.CREATION_MODE_WARNING:
                mMsgSizeLimit = MmsConfig.getMaxMessageSize();
                mImageWidthLimit = MmsConfig.getMaxImageWidth();
                mImageHeightLimit = MmsConfig.getMaxImageHeight();
                break;
            case MmsCreationMode.CREATION_MODE_FREE:
            default:
                mMsgSizeLimit = MmsConfig.getMaxMessageSize();
                mImageWidthLimit = MmsConfig.getMaxImageWidth();
                mImageHeightLimit = MmsConfig.getMaxImageHeight();
                break;
        }
    }

    public void checkMessageSize(int messageSize, int increaseSize, ContentResolver resolver)
            throws ContentRestrictionException {
        if (DEBUG) {
            Log.d(LogTag.APP, "CarrierContentRestriction.checkMessageSize messageSize: " +
                        messageSize + " increaseSize: " + increaseSize +
                        " MmsConfig.getMaxMessageSize: " + MmsConfig.getMaxMessageSize());
        }
        if ((messageSize < 0) || (increaseSize < 0)) {
            throw new ContentRestrictionException("Negative message size"
                    + " or increase size", mCreationMode);
        }
        int newSize = messageSize + increaseSize;

        if ((newSize < 0) || (newSize > mMsgSizeLimit)) {
            throw new ExceedMessageSizeException("Exceed message size limitation", mCreationMode);
        }
    }

    public void checkResolution(int width, int height) throws ContentRestrictionException {
        if ((mCreationMode != MmsCreationMode.CREATION_MODE_FREE)
                && ((width > mImageWidthLimit) || (height > mImageHeightLimit))) {
            throw new ResolutionException("content resolution exceeds restriction.", mCreationMode);
        }
    }

    public void checkImageContentType(String contentType)
            throws ContentRestrictionException {
        if (null == contentType) {
            throw new UnsupportContentTypeException("Null content type to be check", mCreationMode);
        }

        if (!mSupportedImageTypes.contains(contentType)) {
            throw new UnsupportContentTypeException("Unsupported image content type : "
                    + contentType, mCreationMode);
        }
    }

    public void checkAudioContentType(String contentType)
            throws ContentRestrictionException {
        if (null == contentType) {
            throw new UnsupportContentTypeException("Null content type to be check", mCreationMode);
        }

        if (!mSupportedAudioTypes.contains(contentType)) {
            throw new UnsupportContentTypeException("Unsupported audio content type : "
                    + contentType, mCreationMode);
        }
    }

    public void checkVideoContentType(String contentType)
            throws ContentRestrictionException {
        if (null == contentType) {
            throw new UnsupportContentTypeException("Null content type to be check", mCreationMode);
        }

        if (!mSupportedVideoTypes.contains(contentType)) {
            throw new UnsupportContentTypeException("Unsupported video content type : "
                    + contentType, mCreationMode);
        }
    }

    public int getCreationMode() {
        return mCreationMode;
    }
}
