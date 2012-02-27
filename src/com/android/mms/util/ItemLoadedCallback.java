// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.mms.util;

/**
 * Callback interface for a background item loaded request.
 *
 */
public interface ItemLoadedCallback<T> {
    /**
     * Called when an item's loading is complete. At most one of {@code result}
     * and {@code exception} should be non-null.
     *
     * @param result the object result, or {@code null} if the request failed or
     *        was cancelled.
     */
    void onItemLoaded(T result, Throwable exception);
}
