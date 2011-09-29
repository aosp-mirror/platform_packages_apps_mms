// Copyright 2011 Google Inc.
// All Rights Reserved.

package com.android.mms;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.provider.Telephony.CanonicalAddressesColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Mms.Addr;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Mms.Rate;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.mms.pdu.PduHeaders;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import android.provider.Telephony.Threads;

public class TempFileProvider extends ContentProvider {
    private static String TAG = "TempFileProvider";

    private static final int MMS_SCRAP_SPACE = 1;
    private static final UriMatcher sURLMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURLMatcher.addURI("mms_temp_file", "scrapSpace", MMS_SCRAP_SPACE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values,
            String selection, String[] selectionArgs) {
        return 0;
    }

    private ParcelFileDescriptor getTempStoreFd() {
        String fileName = MmsApp.getApplication().getScrapPath();
        ParcelFileDescriptor pfd = null;

        try {
            File file = new File(fileName);

            // make sure the path is valid and directories created for this file.
            File parentFile = file.getParentFile();
            if (!parentFile.exists() && !parentFile.mkdirs()) {
                Log.e(TAG, "[TempFileProvider] tempStoreFd: " + parentFile.getPath() +
                        "does not exist!");
                return null;
            }

            pfd = ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_WRITE
                            | android.os.ParcelFileDescriptor.MODE_CREATE);
        } catch (Exception ex) {
            Log.e(TAG, "getTempStoreFd: error creating pfd for " + fileName, ex);
        }

        return pfd;
    }

    @Override
    public String getType(Uri uri) {
        return "*/*";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // if the url is "content://mms/takePictureTempStore", then it means the requester
        // wants a file descriptor to write image data to.

        ParcelFileDescriptor fd = null;
        int match = sURLMatcher.match(uri);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.d(TAG, "openFile: uri=" + uri + ", mode=" + mode);
        }

        switch (match) {
            case MMS_SCRAP_SPACE:
                fd = getTempStoreFd();
                break;
        }

        return fd;
    }
}
