// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.mms.util;

/**
 * {@link PduFuture} for a pdu that is available now.
 *
 */
public class NullItemLoadedFuture implements ItemLoadedFuture {

    /** {@inheritDoc} */
    public void cancel() {
        // The callback has already been made, so there's nothing to cancel.
    }

    /** {@inheritDoc} */
    public boolean isDone() {
        return true;
    }
}
