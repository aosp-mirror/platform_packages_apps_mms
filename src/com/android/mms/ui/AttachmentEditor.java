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

import com.android.mms.R;
import com.android.mms.model.AudioModel;
import com.android.mms.model.ImageModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.VideoModel;
import com.google.android.mms.MmsException;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewStub;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/**
 * This is an embedded editor/view to add photos and sound/video clips
 * into a multimedia message.
 */
public class AttachmentEditor {
    private static final String TAG = "AttachmentEditor";

    public static final int EMPTY                     = -1;
    public static final int TEXT_ONLY                 = 0;
    public static final int IMAGE_ATTACHMENT          = 1;
    public static final int VIDEO_ATTACHMENT          = 2;
    public static final int AUDIO_ATTACHMENT          = 3;
    public static final int SLIDESHOW_ATTACHMENT      = 4;

    static final int MSG_EDIT_SLIDESHOW   = 1;
    static final int MSG_SEND_SLIDESHOW   = 2;
    static final int MSG_PLAY_SLIDESHOW   = 3;
    static final int MSG_REPLACE_IMAGE    = 4;
    static final int MSG_REPLACE_VIDEO    = 5;
    static final int MSG_REPLACE_AUDIO    = 6;
    static final int MSG_PLAY_VIDEO       = 7;
    static final int MSG_PLAY_AUDIO       = 8;
    static final int MSG_VIEW_IMAGE       = 9;

    private final Context mContext;
    private final Handler mHandler;
    private final View mRoot;

    private SlideViewInterface mView;
    private SlideshowModel mSlideshow;
    private Presenter mPresenter;
    private int mAttachmentType;
    private OnAttachmentChangedListener mAttachmentChangedListener;
    private boolean mCanSend;
    private Button mSendButton;

    public AttachmentEditor(Context context, Handler handler, View vRoot) {
        mContext = context;
        mHandler = handler;
        mRoot = vRoot;
    }

    public void setCanSend(boolean enable) {
        if (mCanSend != enable) {
            mCanSend = enable;
            updateSendButton();
        }
    }

    private void updateSendButton() {
        if (null != mSendButton) {
            mSendButton.setEnabled(mCanSend);
            mSendButton.setFocusable(mCanSend);
        }
    }

    public int getAttachmentType() {
        return mAttachmentType;
    }

    public void setAttachment(SlideshowModel slideshow, int attachmentType) {
        if (attachmentType == EMPTY) {
            throw new IllegalArgumentException(
                    "Type of the attachment may not be EMPTY.");
        }

        mSlideshow = slideshow;

        int oldAttachmentType = mAttachmentType;
        mAttachmentType = attachmentType;

        if (mView != null) {
            ((View) mView).setVisibility(View.GONE);
            mView = null;
        }

        if (attachmentType != TEXT_ONLY) {
            mView = createView();

            if ((mPresenter == null) || !mSlideshow.equals(mPresenter.getModel())) {
                mPresenter = PresenterFactory.getPresenter(
                        "MmsThumbnailPresenter", mContext, mView, mSlideshow);
            } else {
                mPresenter.setView(mView);
            }

            mPresenter.present();
        }

        if ((mAttachmentChangedListener != null) && (mAttachmentType != oldAttachmentType)) {
            mAttachmentChangedListener.onAttachmentChanged(mAttachmentType, oldAttachmentType);
        }
    }

    public void removeAttachment() {
        SlideModel slide = mSlideshow.get(0);
        slide.removeImage();
        slide.removeVideo();
        slide.removeAudio();
    }
    
    public void hideView() {
        if (mView != null) {
            ((View)mView).setVisibility(View.GONE);
        }
    }

    private View getStubView(int stubId, int viewId) {
        View view = mRoot.findViewById(viewId);
        if (view == null) {
            ViewStub stub = (ViewStub) mRoot.findViewById(stubId);
            view = stub.inflate();
        }

        return view;
    }

    private class MessageOnClick implements OnClickListener {
        private int mWhat;
        
        public MessageOnClick(int what) {
            mWhat = what;
        }
        
        public void onClick(View v) {
            Message msg = Message.obtain(mHandler, mWhat);
            msg.sendToTarget();
        }
    }

    private SlideViewInterface createView() {
        switch(mAttachmentType) {
        case IMAGE_ATTACHMENT:
            return createMediaView(
                    R.id.image_attachment_view_stub, R.id.image_attachment_view,
                    R.id.view_image_button, R.id.replace_image_button, R.id.remove_image_button,
                    MSG_VIEW_IMAGE, MSG_REPLACE_IMAGE);
            
        case VIDEO_ATTACHMENT:
            return createMediaView(
                    R.id.video_attachment_view_stub, R.id.video_attachment_view,
                    R.id.view_video_button, R.id.replace_video_button, R.id.remove_video_button,
                    MSG_PLAY_VIDEO, MSG_REPLACE_VIDEO);
            
        case AUDIO_ATTACHMENT:
            return createMediaView(
                    R.id.audio_attachment_view_stub, R.id.audio_attachment_view,
                    R.id.play_audio_button, R.id.replace_audio_button, R.id.remove_audio_button,
                    MSG_PLAY_AUDIO, MSG_REPLACE_AUDIO);
            
        case SLIDESHOW_ATTACHMENT:
            return createSlideshowView();
            
        default:
            throw new IllegalArgumentException();
        }
    }

    private SlideViewInterface createMediaView(
            int stub_view_id, int real_view_id,
            int view_button_id, int replace_button_id, int remove_button_id,
            int view_message, int replace_message) {
        LinearLayout view = (LinearLayout)getStubView(stub_view_id, real_view_id);
        view.setVisibility(View.VISIBLE);

        Button viewButton = (Button) view.findViewById(view_button_id);
        Button replaceButton = (Button) view.findViewById(replace_button_id);
        Button removeButton = (Button) view.findViewById(remove_button_id);

        viewButton.setOnClickListener(new MessageOnClick(view_message));
        replaceButton.setOnClickListener(new MessageOnClick(replace_message));

        removeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                removeAttachment();
                ((View) mView).setVisibility(View.GONE);

                int oldAttachmentType = mAttachmentType;
                mAttachmentType = TEXT_ONLY;
                if (mAttachmentChangedListener != null) {
                    mAttachmentChangedListener.onAttachmentChanged(
                            mAttachmentType, oldAttachmentType);
                }
            }
        });

        return (SlideViewInterface) view;
    }
    
    private SlideViewInterface createSlideshowView() {
        LinearLayout view =(LinearLayout) getStubView(
                R.id.slideshow_attachment_view_stub, R.id.slideshow_attachment_view);
        view.setVisibility(View.VISIBLE);

        Button editBtn = (Button) view.findViewById(R.id.edit_slideshow_button);
        mSendButton = (Button) view.findViewById(R.id.send_slideshow_button);
        updateSendButton();
        final ImageButton playBtn = (ImageButton) view.findViewById(
                R.id.play_slideshow_button);

        editBtn.setOnClickListener(new MessageOnClick(MSG_EDIT_SLIDESHOW));
        mSendButton.setOnClickListener(new MessageOnClick(MSG_SEND_SLIDESHOW));
        playBtn.setOnClickListener(new MessageOnClick(MSG_PLAY_SLIDESHOW));

        return (SlideViewInterface) view;
    }

    public void changeImage(Uri uri) throws MmsException {
        ImageModel image = new ImageModel(mContext, uri,
                mSlideshow.getLayout().getImageRegion());
        SlideModel slide = mSlideshow.get(0);
        slide.removeVideo();
        slide.removeAudio();
        slide.add(image);
    }

    public void changeVideo(Uri uri) throws MmsException {
        VideoModel video = new VideoModel(mContext, uri,
                mSlideshow.getLayout().getImageRegion());
        SlideModel slide = mSlideshow.get(0);
        slide.removeImage();
        slide.removeAudio();
        slide.add(video);
        slide.updateDuration(video.getDuration());
    }

    public void changeAudio(Uri uri) throws MmsException {
        AudioModel audio = new AudioModel(mContext, uri);
        SlideModel slide = mSlideshow.get(0);
        slide.removeImage();
        slide.removeVideo();
        slide.add(audio);
        slide.updateDuration(audio.getDuration());
    }

    public void setOnAttachmentChangedListener(OnAttachmentChangedListener l) {
        mAttachmentChangedListener = l;
    }

    public interface OnAttachmentChangedListener {
        void onAttachmentChanged(int newType, int oldType);
    }
}
