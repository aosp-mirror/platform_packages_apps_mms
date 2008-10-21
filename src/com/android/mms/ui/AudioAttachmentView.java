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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;

/**
 * This class provides an embedded editor/viewer of audio attachment.
 */
public class AudioAttachmentView extends LinearLayout implements
        SlideViewInterface {
    private static final String TAG = "AudioAttachmentView";

    private final Resources mRes;
    private TextView mNameView;
    private TextView mAlbumView;
    private TextView mArtistView;
    private TextView mErrorMsgView;
    private Uri mAudioUri;
    private MediaPlayer mMediaPlayer;
    private boolean mIsPlaying;

    public AudioAttachmentView(Context context) {
        super(context);
        mRes = context.getResources();
    }

    public AudioAttachmentView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRes = context.getResources();
    }

    @Override
    protected void onFinishInflate() {
        mNameView = (TextView) findViewById(R.id.audio_name);
        mAlbumView = (TextView) findViewById(R.id.album_name);
        mArtistView = (TextView) findViewById(R.id.artist_name);
        mErrorMsgView = (TextView) findViewById(R.id.audio_error_msg);
    }

    private void onPlaybackError() {
        Log.e(TAG, "Error occurred while playing audio.");
        showErrorMessage(mRes.getString(R.string.cannot_play_audio));
        stopAudio();
    }

    private void cleanupMediaPlayer() {
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
                mMediaPlayer.release();
            } finally {
                mMediaPlayer = null;
            }
        }
    }

    synchronized public void startAudio() {
        if (!mIsPlaying && (mAudioUri != null)) {
            mMediaPlayer = MediaPlayer.create(mContext, mAudioUri);
            if (mMediaPlayer != null) {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                    public void onCompletion(MediaPlayer mp) {
                        stopAudio();
                    }
                });
                mMediaPlayer.setOnErrorListener(new OnErrorListener() {
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        onPlaybackError();
                        return true;
                    }
                });

                mIsPlaying = true;
                mMediaPlayer.start();
            }
        }
    }

    public void startVideo() {
        // TODO Auto-generated method stub

    }

    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        synchronized (this) {
            mAudioUri = audio;
        }

        mNameView.setText(name);
        mAlbumView.setText((String) extras.get("album"));
        mArtistView.setText((String) extras.get("artist"));
    }

    public void setImage(String name, Bitmap bitmap) {
        // TODO Auto-generated method stub

    }

    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub

    }

    public void setImageVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }

    public void setText(String name, String text) {
        // TODO Auto-generated method stub

    }

    public void setTextVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }

    public void setVideo(String name, Uri video) {
        // TODO Auto-generated method stub

    }

    public void setVideoVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }

    synchronized public void stopAudio() {
        try {
            cleanupMediaPlayer();
        } finally {
            mIsPlaying = false;
        }
    }

    public void stopVideo() {
        // TODO Auto-generated method stub

    }

    public void reset() {
        synchronized (this) {
            if (mIsPlaying) {
                stopAudio();
            }
        }
        mErrorMsgView.setVisibility(GONE);
    }

    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub

    }

    private void showErrorMessage(String msg) {
        mErrorMsgView.setText(msg);
        mErrorMsgView.setVisibility(VISIBLE);
    }

    public void pauseAudio() {
        // TODO Auto-generated method stub

    }

    public void pauseVideo() {
        // TODO Auto-generated method stub

    }

    public void seekAudio(int seekTo) {
        // TODO Auto-generated method stub

    }

    public void seekVideo(int seekTo) {
        // TODO Auto-generated method stub

    }
}
