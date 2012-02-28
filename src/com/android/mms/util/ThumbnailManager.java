/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.mms.util;

import java.util.Set;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.android.mms.LogTag;
import com.android.mms.ui.UriImage;

/**
 * Primary {@link ThumbnailManager} implementation used by {@link MessagingApplication}.
 * <p>
 * Public methods should only be used from a single thread (typically the UI
 * thread). Callbacks will be invoked on the thread where the ThumbnailManager
 * was instantiated.
 * <p>
 * Uses a thread-pool ExecutorService instead of AsyncTasks since clients may
 * request lots of pdus around the same time, and AsyncTask may reject tasks
 * in that case and has no way of bounding the number of threads used by those
 * tasks.
 * <p>
 * ThumbnailManager is used to asynchronously load pictures and create thumbnails. The thumbnails
 * are stored in a local cache with SoftReferences. Once a thumbnail is loaded, it will call the
 * passed in callback with the result. If a thumbnail is immediately available in the cache,
 * the callback will be called immediately as well.
 *
 * Based on BooksImageManager by Virgil King.
 */
public class ThumbnailManager extends BackgroundLoaderManager {
    private static final String TAG = "ThumbnailManager";

    private static final boolean DEBUG_DISABLE_LOAD = false;
    private static final boolean DEBUG_LONG_WAIT = false;

    private static final int THUMBNAIL_BOUNDS_LIMIT = 480;
    private static final int PICTURE_SIZE_LIMIT = 20 * 1024;

    private final SimpleCache<Uri, Bitmap> mThumbnailCache;
    private final Context mContext;

    public ThumbnailManager(final Context context) {
        super(context);

        mThumbnailCache = new SimpleCache<Uri, Bitmap>(32, 256, 0.75f);
        mContext = context;
    }

    /**
     * getThumbnail must be called on the same thread that created ThumbnailManager. This is
     * normally the UI thread.
     * @param uri the uri of the image
     * @param width the original full width of the image
     * @param height the original full height of the image
     * @param callback the callback to call when the thumbnail is fully loaded
     * @return
     */
    public ItemLoadedFuture getThumbnail(Uri uri, int width, int height,
            final ItemLoadedCallback<Bitmap> callback) {
        if (uri == null) {
            throw new NullPointerException();
        }

        final Bitmap thumbnail = mThumbnailCache.get(uri);

        final boolean thumbnailExists = (thumbnail != null);
        final boolean taskExists = mPendingTaskUris.contains(uri);
        final boolean newTaskRequired = !thumbnailExists && !taskExists;
        final boolean callbackRequired = (callback != null);

        if (Log.isLoggable(LogTag.THUMBNAIL_CACHE, Log.DEBUG)) {
            Log.v(TAG, "getThumbnail mThumbnailCache.get for uri: " + uri + " thumbnail: " +
                    thumbnail + " callback: " + callback + " thumbnailExists: " +
                    thumbnailExists + " taskExists: " + taskExists +
                    " newTaskRequired: " + newTaskRequired +
                    " callbackRequired: " + callbackRequired);
        }

        if (thumbnailExists) {
            if (callbackRequired) {
                callback.onItemLoaded(thumbnail, null);
            }
            return new NullItemLoadedFuture();
        }

        if (callbackRequired) {
            addCallback(uri, callback);
        }

        if (newTaskRequired) {
            mPendingTaskUris.add(uri);
            Runnable task = new ThumbnailTask(uri, width, height);
            mExecutor.execute(task);
        }
        return new ItemLoadedFuture() {
            public void cancel() {
                cancelCallback(callback);
            }
            public boolean isDone() {
                return false;
            }
        };
    }

    @Override
    public void clear() {
        super.clear();

        mThumbnailCache.clear();
    }

    public String getTag() {
        return TAG;
    }

    public class ThumbnailTask implements Runnable {
        private final Uri mUri;
        private final int mWidth;
        private final int mHeight;

        public ThumbnailTask(Uri uri, int width, int height) {
            if (uri == null) {
                throw new NullPointerException();
            }
            mUri = uri;
            mWidth = width;
            mHeight = height;
        }

        /** {@inheritDoc} */
        public void run() {
            if (DEBUG_DISABLE_LOAD) {
                return;
            }
            if (DEBUG_LONG_WAIT) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                }
            }

            byte[] data = UriImage.getResizedImageData(mWidth, mHeight,
                    THUMBNAIL_BOUNDS_LIMIT, THUMBNAIL_BOUNDS_LIMIT,
                    PICTURE_SIZE_LIMIT, mUri, mContext);
            if (Log.isLoggable(LogTag.THUMBNAIL_CACHE, Log.DEBUG)) {
                Log.v(TAG, "createBitmap size: " + (data == null ? data : data.length));
            }
            Bitmap bitmap = data == null ? null :
                BitmapFactory.decodeByteArray(data, 0, data.length);
            if (Log.isLoggable(LogTag.THUMBNAIL_CACHE, Log.DEBUG) && bitmap == null) {
                Log.v(TAG, "DECODED BITMAP IS NULL!!!");
            }

            final Bitmap resultBitmap = bitmap;
            mCallbackHandler.post(new Runnable() {
                public void run() {
                    final Set<ItemLoadedCallback> callbacks = mCallbacks.get(mUri);
                    if (callbacks != null) {
                        // Make a copy so that the callback can unregister itself
                        for (final ItemLoadedCallback<Bitmap> callback : asList(callbacks)) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Invoking item loaded callback " + callback);
                            }
                            callback.onItemLoaded(resultBitmap, null);
                        }
                    } else {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "No image callback!");
                        }
                    }

                    // Add the bitmap to the soft cache if the load succeeded
                    if (resultBitmap != null) {
                        mThumbnailCache.put(mUri, resultBitmap);
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.v(TAG, "in callback runnable: bitmap uri: " + mUri +
                                    " width: " + resultBitmap.getWidth() + " height: " +
                                    resultBitmap.getHeight() + " size: " +
                                    resultBitmap.getByteCount());
                        }
                    }

                    mCallbacks.remove(mUri);
                    mPendingTaskUris.remove(mUri);

                    if (Log.isLoggable(LogTag.THUMBNAIL_CACHE, Log.DEBUG)) {
                        Log.d(TAG, "Image task for " + mUri + "exiting " + mPendingTaskUris.size()
                                + " remain");
                    }
                }
            });
        }
    }
}
