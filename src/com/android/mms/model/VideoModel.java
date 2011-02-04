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
import com.android.mms.LogTag;
import com.android.mms.dom.events.EventImpl;
import com.android.mms.dom.smil.SmilMediaElementImpl;
import com.android.mms.drm.DrmWrapper;
import com.google.android.mms.MmsException;
import android.database.sqlite.SqliteWrapper;

import org.w3c.dom.events.Event;
import org.w3c.dom.smil.ElementTime;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;

import com.google.android.mms.ContentType;
import java.io.IOException;

public class VideoModel extends RegionMediaModel {
    private static final String TAG = MediaModel.TAG;
    private static final boolean DEBUG = true;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    public VideoModel(Context context, Uri uri, RegionModel region)
            throws MmsException {
        this(context, null, null, uri, region);
        initModelFromUri(uri);
        checkContentRestriction();
    }

    public VideoModel(Context context, String contentType, String src,
            Uri uri, RegionModel region) throws MmsException {
        super(context, SmilHelper.ELEMENT_TAG_VIDEO, contentType, src, uri, region);
    }

    public VideoModel(Context context, String contentType, String src,
            DrmWrapper wrapper, RegionModel regionModel) throws IOException {
        super(context, SmilHelper.ELEMENT_TAG_VIDEO, contentType, src, wrapper, regionModel);
    }

    private void initModelFromUri(Uri uri) throws MmsException {
        ContentResolver cr = mContext.getContentResolver();
        Cursor c = SqliteWrapper.query(mContext, cr, uri, null, null, null, null);

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String path = c.getString(c.getColumnIndexOrThrow(Images.Media.DATA));
                    mSrc = path.substring(path.lastIndexOf('/') + 1);
                    mContentType = c.getString(c.getColumnIndexOrThrow(
                            Images.Media.MIME_TYPE));
                    if (TextUtils.isEmpty(mContentType)) {
                        throw new MmsException("Type of media is unknown.");
                    }

                    if (mContentType.equals(ContentType.VIDEO_MP4) && !(TextUtils.isEmpty(mSrc))) {
                        int index = mSrc.lastIndexOf(".");
                        if (index != -1) {
                            try {
                                String extension = mSrc.substring(index + 1);
                                if (!(TextUtils.isEmpty(extension)) &&
                                        (extension.equalsIgnoreCase("3gp") ||
                                        extension.equalsIgnoreCase("3gpp") ||
                                        extension.equalsIgnoreCase("3g2"))) {
                                    mContentType = ContentType.VIDEO_3GPP;
                                }
                            } catch(IndexOutOfBoundsException ex) {
                                if (LOCAL_LOGV) {
                                    Log.v(TAG, "Media extension is unknown.");
                                }
                            }
                        }
                    }

                    if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                        Log.v(TAG, "New VideoModel created:"
                                + " mSrc=" + mSrc
                                + " mContentType=" + mContentType
                                + " mUri=" + uri);
                    }
                } else {
                    throw new MmsException("Nothing found: " + uri);
                }
            } finally {
                c.close();
            }
        } else {
            throw new MmsException("Bad URI: " + uri);
        }

        initMediaDuration();
    }

    // EventListener Interface
    public void handleEvent(Event evt) {
        String evtType = evt.getType();
        if (LOCAL_LOGV || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            Log.v(TAG, "[VideoModel] handleEvent " + evt.getType() + " on " + this);
        }

        MediaAction action = MediaAction.NO_ACTIVE_ACTION;
        if (evtType.equals(SmilMediaElementImpl.SMIL_MEDIA_START_EVENT)) {
            action = MediaAction.START;

            // if the Music player app is playing audio, we should pause that so it won't
            // interfere with us playing video here.
            pauseMusicPlayer();

            mVisible = true;
        } else if (evtType.equals(SmilMediaElementImpl.SMIL_MEDIA_END_EVENT)) {
            action = MediaAction.STOP;
            if (mFill != ElementTime.FILL_FREEZE) {
                mVisible = false;
            }
        } else if (evtType.equals(SmilMediaElementImpl.SMIL_MEDIA_PAUSE_EVENT)) {
            action = MediaAction.PAUSE;
            mVisible = true;
        } else if (evtType.equals(SmilMediaElementImpl.SMIL_MEDIA_SEEK_EVENT)) {
            action = MediaAction.SEEK;
            mSeekTo = ((EventImpl) evt).getSeekTo();
            mVisible = true;
        }

        appendAction(action);
        notifyModelChanged(false);
    }

    protected void checkContentRestriction() throws ContentRestrictionException {
        ContentRestriction cr = ContentRestrictionFactory.getContentRestriction();
        cr.checkVideoContentType(mContentType);
    }

    @Override
    protected boolean isPlayable() {
        return true;
    }
}
