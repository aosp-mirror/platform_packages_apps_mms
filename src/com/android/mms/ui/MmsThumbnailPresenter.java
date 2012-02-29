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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.android.mms.R;
import com.android.mms.model.AudioModel;
import com.android.mms.model.ImageModel;
import com.android.mms.model.Model;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.VideoModel;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager.ImageLoaded;

public class MmsThumbnailPresenter extends Presenter {
    private static final String TAG = "MmsThumbnailPresenter";
    private ItemLoadedCallback mOnLoadedCallback;

    public MmsThumbnailPresenter(Context context, ViewInterface view, Model model) {
        super(context, view, model);
    }

    @Override
    public void present(ItemLoadedCallback callback) {
        mOnLoadedCallback = callback;
        SlideModel slide = ((SlideshowModel) mModel).get(0);
        if (slide != null) {
            presentFirstSlide((SlideViewInterface) mView, slide);
        }
    }

    private void presentFirstSlide(SlideViewInterface view, SlideModel slide) {
        view.reset();

        if (slide.hasImage()) {
            presentImageThumbnail(view, slide.getImage());
        } else if (slide.hasVideo()) {
            presentVideoThumbnail(view, slide.getVideo());
        } else if (slide.hasAudio()) {
            presentAudioThumbnail(view, slide.getAudio());
        }
    }

    private ItemLoadedCallback<ImageLoaded> mImageLoadedCallback =
            new ItemLoadedCallback<ImageLoaded>() {
        public void onItemLoaded(ImageLoaded imageLoaded, Throwable exception) {
            if (exception == null) {
                // Right now we're only handling image loaded callbacks.
                SlideModel slide = ((SlideshowModel) mModel).get(0);
                if (slide != null) {
                    if (slide.hasVideo() && imageLoaded.mIsVideo) {
                        ((SlideViewInterface)mView).setVideoThumbnail(null, imageLoaded.mBitmap);
                    } else if (slide.hasImage() && !imageLoaded.mIsVideo) {
                        ((SlideViewInterface)mView).setImage(null, imageLoaded.mBitmap);
                    }
                }
            }
            if (mOnLoadedCallback != null) {
                mOnLoadedCallback.onItemLoaded(imageLoaded, exception);
            }
        }
    };

    private void presentVideoThumbnail(SlideViewInterface view, VideoModel video) {
        if (video.isDrmProtected()) {
            showDrmIcon(view, video.getSrc());
        } else {
            view.setVideoThumbnail(video.getSrc(), null);  // inflate the view and show loading icon
            // while we're loading the real bitmap
            video.loadThumbnailBitmap(mImageLoadedCallback);
        }
    }

    private void presentImageThumbnail(SlideViewInterface view, ImageModel image) {
        if (image.isDrmProtected()) {
            showDrmIcon(view, image.getSrc());
        } else {
            view.setImage(image.getSrc(), null);    // inflate the view and show loading icon
            // while we're loading the real bitmap
            image.loadThumbnailBitmap(mImageLoadedCallback);
        }
    }

    protected void presentAudioThumbnail(SlideViewInterface view, AudioModel audio) {
        if (audio.isDrmProtected()) {
            showDrmIcon(view, audio.getSrc());
        } else {
            view.setAudio(audio.getUri(), audio.getSrc(), audio.getExtras());
        }
    }

    // Show an icon instead of real content in the thumbnail.
    private void showDrmIcon(SlideViewInterface view, String name) {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(
                    mContext.getResources(), R.drawable.ic_mms_drm_protected);
            view.setImage(name, bitmap);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "showDrmIcon: out of memory: ", e);
        }
    }

    public void onModelChanged(Model model, boolean dataChanged) {
        // TODO Auto-generated method stub
    }

    public void cancelBackgroundLoading() {
        // Currently we only support background loading of thumbnails. If we extend background
        // loading to other media types, we should add a cancelLoading API to Model.
        SlideModel slide = ((SlideshowModel) mModel).get(0);
        if (slide != null && slide.hasImage()) {
            slide.getImage().cancelThumbnailLoading();
        }
    }

}
