package com.android.mms.ui;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.android.mms.LogTag;
import com.android.mms.util.GifDecoder;
import com.google.android.mms.ContentType;

/*
 * Extension of ImageView class that supports animated GIFs
 */
public class MessagingImageView extends ImageView {
    private static final String TAG = "MessagingImageView";

    // Minimum delay time between two gif frames
    // Below this value, frames can be missed during a gif animation
    private static final long MIN_GIF_DELAY_TIME = 50;
    private GifDecoder mGifDecoder;
    private Bitmap mTmpBitmap;
    final Handler mHandler = new Handler();
    private boolean mIsPlayingGif = false;

    public MessagingImageView(Context context) {
        super(context);
    }

    public MessagingImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MessagingImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Starts playing the GIF
     * @param uri The {@link Uri} of the GIF to play
     * @param mimeType The image mime type
     * @param alternateImage The {@link Bitmap} containing an alternative image
     *                       to display when GIF cannot be loaded
     */
    public void startPlayingGif(Uri uri, String mimeType, Bitmap alternativeImage) {
        // Check if the mime type is for a GIF
        if (!isGif(mimeType)) {
            throw new IllegalArgumentException("Trying to play a non GIF file!");
        }

        // Check if a GIF is already being played
        if (mIsPlayingGif) {
            Log.w(TAG, "Animation in progress. Stopping current animation to start new one.");
            stopPlayingGif();
        }

        // Init GifDecoder for the Uri
        int status = initGifDecoder(uri);

        // Check if decode was successful
        if (status == GifDecoder.STATUS_OK) {
            mIsPlayingGif = true;
            new Thread(new Runnable() {
                public void run() {
                   final int frameCount = mGifDecoder.getFrameCount();
                   final int loopCount = mGifDecoder.getLoopCount();
                   int repetitionCounter = 0;
                   do {
                       for (int i = 0; i < frameCount; i++) {
                           mTmpBitmap = mGifDecoder.getFrame(i);
                           mHandler.post(mUpdateResults);
                           try {
                               long sleepTime = mGifDecoder.getDelay(i);
                               Thread.sleep(sleepTime > MIN_GIF_DELAY_TIME ?
                                                 sleepTime : MIN_GIF_DELAY_TIME);
                           } catch (InterruptedException e) {
                               e.printStackTrace();
                           }
                       }
                       if (loopCount != 0) {
                           repetitionCounter++;
                       }
                   } while (mIsPlayingGif && (repetitionCounter <= loopCount));
                }
            }).start();
        } else {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.d(TAG, "Failed to decode GIF with error: " + status +
                        ". Displaying image without animation.");
            }
            // If decode fails, set the alternate image
            setImageBitmap(alternativeImage);
        }
    }

    /**
     * Stops playing the GIF
     */
    public void stopPlayingGif() {
        mIsPlayingGif = false;
        mGifDecoder.recycle();
    }

    /**
     * Runnable to update UI
     */
    final Runnable mUpdateResults = new Runnable() {
        public void run() {
           if (mTmpBitmap != null && !mTmpBitmap.isRecycled()) {
               MessagingImageView.this.setImageBitmap(mTmpBitmap);
           }
        }
     };

    /**
     * Initialize GifDecoder
     */
    private int initGifDecoder(Uri uri) {
        int status = GifDecoder.STATUS_OPEN_ERROR;
        InputStream stream = null;
        try {
            mGifDecoder = new GifDecoder();
            stream = getContext().getContentResolver().openInputStream(uri);
            status = mGifDecoder.read(stream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            // Release input stream
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return status;
    }

    /**
     * Check if image is a GIF
     */
    private boolean isGif(String mimeType) {
        return (ContentType.IMAGE_GIF.equals(mimeType));
    }
}
