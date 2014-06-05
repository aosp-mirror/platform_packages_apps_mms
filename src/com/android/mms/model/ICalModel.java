package com.android.mms.model;

import org.w3c.dom.events.Event;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.mms.ui.MessageUtils;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.android.mms.R;

/**
 * 1. Create Ical Model
 * 2. Retrieve all Ical information like
 *    event info., event name etc
 *
 *
 */

public final class ICalModel extends MediaModel {
    private static final String TAG = "IcalModel";
    private static final boolean DEBUG = true;
    private static final boolean LOCAL_LOG = (DEBUG);

    public ICalModel(Context context, Uri uri) throws MmsException {
        super(context, ContentType.TEXT_VCALENDAR, SmilHelper.ELEMENT_TAG_REF, uri);
        initializeFromUri(uri);
    }

    protected void initializeFromUri(Uri uri) throws MmsException {
        if (LOCAL_LOG) {
            Log.v(TAG, "initializeFromUri, uri=" + uri + ", mContentType=" + mContentType +
                        ", mSrc=" + mSrc);
        }

        String path = getNameForUri(uri);

        if (path == null) {
            return;
        }

        mSrc = getNameFromPath(path, mContentType);

        if (TextUtils.isEmpty(mSrc)) {
            throw new MmsException("Media source name is unknown.");
        }

    }

    protected String getNameForUri(Uri uri) {
        return uri.getLastPathSegment();
    }

    public void handleEvent(Event evt) {
    }

    private String getNameFromPath(String path, String mContentType) {
        if (path == null) {
            return null;
        }

        String name = path.substring(path.lastIndexOf('/') + 1);


        name = fixFileExtension(name, mContentType);

        // Some MMSCs appear to have problems with filenames
        // containing a space. So just replace them with
        // underscores in the name, which is typically not
        // visible to the user anyway.
        name = name.replace(' ', '_');

        // Avoid multiple dots, as some phones can't handle it.
        if (name.indexOf('.') != name.lastIndexOf('.')) {
            name = name.substring(0, name.lastIndexOf('.') + 1).replace('.', '_') + '.'
                    + name.substring(name.lastIndexOf('.') + 1);
        }

        return name;
    }

    private final String fixFileExtension(String fileName, String type) {
        if (fileName == null || type == null) {
            Log.w(TAG, "fixFileExtension - fileName=" + fileName);
            return fileName;
        }

        int index = fileName.indexOf(".");
        if (index == -1) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            // If the file extension is empty, then leave it to the SdCardWriter
            // to determine the appropriate file extension.
            if (!TextUtils.isEmpty(extension)) {
                if (LOCAL_LOG) {
                    Log.v(TAG, "fixFileExtension - fileName=" + fileName + "." + extension);
                }

                return (fileName + "." + extension);

            }
        }

        return fileName;
    }

    public Bitmap getBitmap(int widthLimit, int heightLimit) {
        Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.ic_thb_mtg_invite);

        if (widthLimit == 0 || heightLimit == 0) {
            widthLimit = bitmap.getWidth();
            heightLimit = bitmap.getHeight();
        }
        Bitmap useThisBitmap = Bitmap.createScaledBitmap(bitmap, widthLimit, heightLimit, true);

        return useThisBitmap;
    }
}
