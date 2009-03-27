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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.method.HideReturnsTransformationMethod;
import android.util.AttributeSet;
import android.util.Config;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsoluteLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.IOException;
import java.util.Map;

/**
 * A basic view to show the contents of a slide.
 */
public class SlideView extends AbsoluteLayout implements
        AdaptableSlideViewInterface {
    private static final String TAG = "SlideView";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;
    // FIXME: Need getHeight from mAudioInfoView instead of constant AUDIO_INFO_HEIGHT.
    private static final int AUDIO_INFO_HEIGHT = 82;

    private View mAudioInfoView;
    private ImageView mImageView;
    private VideoView mVideoView;
    private ScrollView mScrollText;
    private TextView mTextView;
    private OnSizeChangedListener mSizeChangedListener;
    private MediaPlayer mAudioPlayer;
    private boolean mIsPrepared;
    private boolean mStartWhenPrepared;
    private int     mSeekWhenPrepared;
    private boolean mStopWhenPrepared;
    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            mIsPrepared = true;
            if (mSeekWhenPrepared > 0) {
                mAudioPlayer.seekTo(mSeekWhenPrepared);
                mSeekWhenPrepared = 0;
            }
            if (mStartWhenPrepared) {
                mAudioPlayer.start();
                mStartWhenPrepared = false;
                displayAudioInfo();
            }
            if (mStopWhenPrepared) {
                mAudioPlayer.stop();
                mAudioPlayer.release();
                mAudioPlayer = null;
                mStopWhenPrepared = false;
                hideAudioInfo();
            }
        }
    };

    public SlideView(Context context) {
        super(context);
    }

    public SlideView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setImage(String name, Bitmap bitmap) {
        if (mImageView == null) {
            mImageView = new ImageView(mContext);
            addView(mImageView, new LayoutParams(
                    0, 0, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            if (DEBUG) {
                mImageView.setBackgroundColor(0xFFFF0000);
            }
        }

        if (null == bitmap) {
            bitmap = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_missing_thumbnail_picture);
        }
        mImageView.setImageBitmap(bitmap);
    }

    public void setImageRegion(int left, int top, int width, int height) {
        if (mImageView != null) {
            mImageView.setLayoutParams(new LayoutParams(width, height, left, top));
        }
    }

    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub
    }

    public void setVideo(String name, Uri video) {
        if (mVideoView == null) {
            mVideoView = new VideoView(mContext);
            addView(mVideoView, new LayoutParams(
                    0, 0, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            if (DEBUG) {
                mVideoView.setBackgroundColor(0xFFFF0000);
            }
        }

        if (LOCAL_LOGV) {
            Log.v(TAG, "Changing video source to " + video);
        }
        mVideoView.setVideoURI(video);
    }

    private void initAudioInfoView(String name) {
        if (null == mAudioInfoView) {
            LayoutInflater factory = LayoutInflater.from(getContext());
            mAudioInfoView = factory.inflate(R.layout.playing_audio_info, null);
            int height = mAudioInfoView.getHeight();
            TextView audioName = (TextView) mAudioInfoView.findViewById(R.id.name);
            audioName.setText(name);
            addView(mAudioInfoView, new LayoutParams(
                    LayoutParams.FILL_PARENT, AUDIO_INFO_HEIGHT,
                    0, getHeight() - AUDIO_INFO_HEIGHT));
            if (DEBUG) {
                mAudioInfoView.setBackgroundColor(0xFFFF0000);
            }
        }

        mAudioInfoView.setVisibility(View.GONE);
    }

    private void displayAudioInfo() {
        if (null != mAudioInfoView) {
            mAudioInfoView.setVisibility(View.VISIBLE);
        }
    }

    private void hideAudioInfo() {
        if (null != mAudioInfoView) {
            mAudioInfoView.setVisibility(View.GONE);
        }
    }

    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        if (audio == null) {
            throw new IllegalArgumentException("Audio URI may not be null.");
        }

        if (LOCAL_LOGV) {
            Log.v(TAG, "Changing audio source to " + audio);
        }

        if (mAudioPlayer != null) {
            mAudioPlayer.reset();
            mAudioPlayer.release();
            mAudioPlayer = null;
        }
        mIsPrepared = false;

        try {
            mAudioPlayer = new MediaPlayer();
            mAudioPlayer.setOnPreparedListener(mPreparedListener);
            mAudioPlayer.setDataSource(mContext, audio);
            mAudioPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "Unexpected IOException.", e);
            mAudioPlayer.release();
            mAudioPlayer = null;
        }
        initAudioInfoView(name);
    }

    public void setText(String name, String text) {
        if (null == mScrollText) {
            mScrollText = new ScrollView(mContext);
            mScrollText.setScrollBarStyle(SCROLLBARS_OUTSIDE_INSET);
            addView(mScrollText, new LayoutParams(
                    0, 0, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            if (DEBUG) {
                mScrollText.setBackgroundColor(0xFF00FF00);
            }
        }

        if (null == mTextView) {
            mTextView = new TextView(mContext);
            mTextView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            mScrollText.addView(mTextView);
        }

        mScrollText.requestFocus();
        mTextView.setText(text);
    }

    public void setTextRegion(int left, int top, int width, int height) {
        if (mScrollText != null) {
            mScrollText.setLayoutParams(new LayoutParams(width, height, left, top));
        }
    }

    public void setVideoRegion(int left, int top, int width, int height) {
        if (mVideoView != null) {
            mVideoView.setLayoutParams(new LayoutParams(width, height, left, top));
        }
    }

    public void setImageVisibility(boolean visible) {
        if (mImageView != null) {
            mImageView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void setTextVisibility(boolean visible) {
        if (mScrollText != null) {
            mScrollText.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void setVideoVisibility(boolean visible) {
        if (mVideoView != null) {
            mVideoView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void startAudio() {
        if ((mAudioPlayer != null) && mIsPrepared) {
            mAudioPlayer.start();
            mStartWhenPrepared = false;
            displayAudioInfo();
        } else {
            mStartWhenPrepared = true;
        }
    }

    public void stopAudio() {
        if ((mAudioPlayer != null) && mIsPrepared) {
            mAudioPlayer.stop();
            mAudioPlayer.release();
            mAudioPlayer = null;
            hideAudioInfo();
        } else {
            mStopWhenPrepared = true;
        }
    }

    public void pauseAudio() {
        if ((mAudioPlayer != null) && mIsPrepared) {
            if (mAudioPlayer.isPlaying()) {
                mAudioPlayer.pause();
            }
        }
        mStartWhenPrepared = false;
    }

    public void seekAudio(int seekTo) {
        if ((mAudioPlayer != null) && mIsPrepared) {
            mAudioPlayer.seekTo(seekTo);
        } else {
            mSeekWhenPrepared = seekTo;
        }
    }

    public void startVideo() {
        if (mVideoView != null) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Starting video playback.");
            }
            mVideoView.start();
        }
    }

    public void stopVideo() {
        if ((mVideoView != null)) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Stopping video playback.");
            }
            mVideoView.stopPlayback();
        }
    }

    public void pauseVideo() {
        if (mVideoView != null) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Pausing video playback.");
            }
            mVideoView.pause();
        }
    }

    public void seekVideo(int seekTo) {
        if (mVideoView != null) {
            if (seekTo > 0) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Seeking video playback to " + seekTo);
                }
                mVideoView.seekTo(seekTo);
            }
        }
    }

    public void reset() {
        if (null != mScrollText) {
            mScrollText.setVisibility(View.GONE);
        }

        if (null != mImageView) {
            mImageView.setVisibility(View.GONE);
        }

        if (null != mAudioPlayer) {
            stopAudio();
        }

        if (null != mVideoView) {
            stopVideo();
            mVideoView.setVisibility(View.GONE);
        }
    }

    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (mSizeChangedListener != null) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "new size=" + w + "x" + h);
            }
            mSizeChangedListener.onSizeChanged(w, h - AUDIO_INFO_HEIGHT);
        }
    }

    public void setOnSizeChangedListener(OnSizeChangedListener l) {
        mSizeChangedListener = l;
    }
}
