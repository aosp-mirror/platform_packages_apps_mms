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
    public static final int CAPTURED_IMAGE_ATTACHMENT = 5;
    public static final int CAPTURED_VIDEO_ATTACHMENT = 6;
    public static final int CAPTURED_AUDIO_ATTACHMENT = 7;

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

    private SlideViewInterface createView() {
        switch (mAttachmentType) {
            case IMAGE_ATTACHMENT: {
                LinearLayout view =(LinearLayout) getStubView(
                        R.id.image_attachment_view_stub, R.id.image_attachment_view);
                view.setVisibility(View.VISIBLE);

                Button vwImageBtn = (Button) view.findViewById(R.id.view_image_button);
                Button rpImageBtn = (Button) view.findViewById(R.id.replace_image_button);
                Button rmImageBtn = (Button) view.findViewById(R.id.remove_image_button);

                vwImageBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Message msg = Message.obtain(mHandler, MSG_VIEW_IMAGE);
                        msg.sendToTarget();
                    }
                });

                rpImageBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Message msg = Message.obtain(mHandler, MSG_REPLACE_IMAGE);
                        msg.sendToTarget();
                    }
                });

                rmImageBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        mSlideshow.get(0).removeImage();
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

            case VIDEO_ATTACHMENT: {
                LinearLayout view =(LinearLayout) getStubView(
                        R.id.video_attachment_view_stub, R.id.video_attachment_view);
                view.setVisibility(View.VISIBLE);

                Button vwVideoBtn = (Button) view.findViewById(R.id.view_video_button);
                Button rpVideoBtn = (Button) view.findViewById(R.id.replace_video_button);
                Button rmVideoBtn = (Button) view.findViewById(R.id.remove_video_button);

                vwVideoBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Message msg = Message.obtain(mHandler, MSG_PLAY_VIDEO);
                        msg.sendToTarget();
                    }
                });

                rpVideoBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Message msg = Message.obtain(mHandler, MSG_REPLACE_VIDEO);
                        msg.sendToTarget();
                    }
                });

                rmVideoBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        mSlideshow.get(0).removeVideo();
                        mView.stopVideo();
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

            case AUDIO_ATTACHMENT: {
                LinearLayout view =(LinearLayout) getStubView(
                        R.id.audio_attachment_view_stub, R.id.audio_attachment_view);
                view.setVisibility(View.VISIBLE);

                Button plAudioBtn = (Button) view.findViewById(R.id.play_audio_button);
                Button rpAudioBtn = (Button) view.findViewById(R.id.replace_audio_button);
                Button rmAudioBtn = (Button) view.findViewById(R.id.remove_audio_button);

                plAudioBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Message msg = Message.obtain(mHandler, MSG_PLAY_AUDIO);
                        msg.sendToTarget();
                    }
                });

                rpAudioBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        AudioModel audio = mSlideshow.get(0).getAudio();
                        if (audio != null) {
                            audio.stop();
                        }
                        Message msg = Message.obtain(mHandler, MSG_REPLACE_AUDIO);
                        msg.sendToTarget();
                    }
                });

                rmAudioBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        AudioModel audio = mSlideshow.get(0).getAudio();
                        if (audio != null) {
                            audio.stop();
                        }
                        mSlideshow.get(0).removeAudio();
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

            case SLIDESHOW_ATTACHMENT: {
                LinearLayout view =(LinearLayout) getStubView(
                        R.id.slideshow_attachment_view_stub, R.id.slideshow_attachment_view);
                view.setVisibility(View.VISIBLE);

                Button editBtn = (Button) view.findViewById(R.id.edit_slideshow_button);
                mSendButton = (Button) view.findViewById(R.id.send_slideshow_button);
                updateSendButton();
                final ImageButton playBtn = (ImageButton) view.findViewById(
                        R.id.play_slideshow_button);

                editBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Message msg = Message.obtain(mHandler, MSG_EDIT_SLIDESHOW);
                        msg.sendToTarget();
                    }
                });

                mSendButton.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Message msg = Message.obtain(mHandler, MSG_SEND_SLIDESHOW);
                        msg.sendToTarget();
                    }
                });

                playBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Message msg = Message.obtain(mHandler, MSG_PLAY_SLIDESHOW);
                        msg.sendToTarget();
                    }
                });

                return (SlideViewInterface) view;
            }

            default:
                throw new IllegalArgumentException();
        }
    }

    public void changeImage(Uri uri) throws MmsException {
        mSlideshow.get(0).add(new ImageModel(
                mContext, uri, mSlideshow.getLayout().getImageRegion()));
    }

    public void changeVideo(Uri uri) throws MmsException {
        VideoModel video = new VideoModel(mContext, uri,
                mSlideshow.getLayout().getImageRegion());
        SlideModel slide = mSlideshow.get(0);
        slide.add(video);
        slide.updateDuration(video.getDuration());
    }

    public void changeAudio(Uri uri) throws MmsException {
        AudioModel audio = new AudioModel(mContext, uri);
        SlideModel slide = mSlideshow.get(0);
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
