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

package com.android.mms.ui;

import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mms.model.ImageModel;
import com.android.mms.model.OtherModel;
import com.android.mms.model.VCardModel;
import com.android.mms.R;
import com.google.android.mms.ContentType;

public class OtherAttachmentView extends LinearLayout implements SlideViewInterface {

    private ImageView mImageView;
    private TextView mTextView;
    private static final String TAG = "OtherAttachmentView";

    public OtherAttachmentView(Context context) {
        super(context);
    }

    public OtherAttachmentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mImageView = (ImageView) findViewById(R.id.image_content);
        mTextView = (TextView) findViewById(R.id.text_content);
    }

    @Override
    public void reset() {
        mImageView.setImageDrawable(null);
    }

    @Override
    public void setVisibility(boolean visible) {
        setVisibility(visible ? View.VISIBLE : View.GONE);

    }

    @Override
    public void setImage(String name, Bitmap bitmap) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setImageVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setVideo(String name, Uri video) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setVideoThumbnail(String name, Bitmap bitmap) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setVideoVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }

    @Override
    public void startVideo() {
        // TODO Auto-generated method stub

    }

    @Override
    public void stopVideo() {
        // TODO Auto-generated method stub

    }

    @Override
    public void pauseVideo() {
        // TODO Auto-generated method stub

    }

    @Override
    public void seekVideo(int seekTo) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        // TODO Auto-generated method stub

    }

    @Override
    public void startAudio() {
        // TODO Auto-generated method stub

    }

    @Override
    public void stopAudio() {
        // TODO Auto-generated method stub

    }

    @Override
    public void pauseAudio() {
        // TODO Auto-generated method stub

    }

    @Override
    public void seekAudio(int seekTo) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setText(String name, String text) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setTextVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setOther(OtherModel otherModel) {
        try {
            if (otherModel != null && otherModel instanceof VCardModel) {
                mImageView.setImageResource(R.drawable.vcard_icon);
            }
            mTextView.setText(otherModel.getSrc());
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setImage: out of memory: ", e);
        }
    }

    @Override
    public void setOtherVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }
}
