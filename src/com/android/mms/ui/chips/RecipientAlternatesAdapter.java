/*
* Copyright (C) 2011 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.mms.ui.chips;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.mms.ui.chips.Queries.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.android.ex.chips.R;

/**
* RecipientAlternatesAdapter backs the RecipientEditTextView for managing contacts
* queried by email or by phone number.
*/
public class RecipientAlternatesAdapter extends CursorAdapter {
    static final int MAX_LOOKUPS = 50;
    private final LayoutInflater mLayoutInflater;

    private final long mCurrentId;

    private int mCheckedItemPosition = -1;

    private OnCheckedItemChangedListener mCheckedItemChangedListener;

    private static final String TAG = "RecipAlternates";

    public static final int QUERY_TYPE_EMAIL = 0;
    public static final int QUERY_TYPE_PHONE = 1;
    private Query mQuery;

    public static HashMap<String, RecipientEntry> getMatchingRecipients(Context context,
            ArrayList<String> inAddresses) {
        return getMatchingRecipients(context, inAddresses, QUERY_TYPE_EMAIL);
    }

    /**
* Get a HashMap of address to RecipientEntry that contains all contact
* information for a contact with the provided address, if one exists. This
* may block the UI, so run it in an async task.
*
* @param context Context.
* @param inAddresses Array of addresses on which to perform the lookup.
* @return HashMap<String,RecipientEntry>
*/
    public static HashMap<String, RecipientEntry> getMatchingRecipients(Context context,
            ArrayList<String> inAddresses, int addressType) {
        Queries.Query query;
        if (addressType == QUERY_TYPE_EMAIL) {
            query = Queries.EMAIL;
        } else {
            query = Queries.PHONE;
        }
        int addressesSize = Math.min(MAX_LOOKUPS, inAddresses.size());
        String[] addresses = new String[addressesSize];
        StringBuilder bindString = new StringBuilder();
        // Create the "?" string and set up arguments.
        for (int i = 0; i < addressesSize; i++) {
            Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(inAddresses.get(i).toLowerCase());
            addresses[i] = (tokens.length > 0 ? tokens[0].getAddress() : inAddresses.get(i));
            bindString.append("?");
            if (i < addressesSize - 1) {
                bindString.append(",");
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Doing reverse lookup for " + addresses.toString());
        }

        HashMap<String, RecipientEntry> recipientEntries = new HashMap<String, RecipientEntry>();
        Cursor c = context.getContentResolver().query(
                query.getContentUri(),
                query.getProjection(),
                query.getProjection()[Queries.Query.DESTINATION] + " IN (" + bindString.toString()
                        + ")", addresses, null);

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    do {
                        String address = c.getString(Queries.Query.DESTINATION);
                        recipientEntries.put(address, RecipientEntry.constructTopLevelEntry(
                                c.getString(Queries.Query.NAME),
                                c.getInt(Queries.Query.DISPLAY_NAME_SOURCE),
                                c.getString(Queries.Query.DESTINATION),
                                c.getInt(Queries.Query.DESTINATION_TYPE),
                                c.getString(Queries.Query.DESTINATION_LABEL),
                                c.getLong(Queries.Query.CONTACT_ID),
                                c.getLong(Queries.Query.DATA_ID),
                                c.getString(Queries.Query.PHOTO_THUMBNAIL_URI)));
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Received reverse look up information for " + address
                                    + " RESULTS: "
                                    + " NAME : " + c.getString(Queries.Query.NAME)
                                    + " CONTACT ID : " + c.getLong(Queries.Query.CONTACT_ID)
                                    + " ADDRESS :" + c.getString(Queries.Query.DESTINATION));
                        }
                    } while (c.moveToNext());
                }
            } finally {
                c.close();
            }
        }
        return recipientEntries;
    }

    public RecipientAlternatesAdapter(Context context, long contactId, long currentId, int viewId,
            OnCheckedItemChangedListener listener) {
        this(context, contactId, currentId, viewId, QUERY_TYPE_EMAIL, listener);
    }

    public RecipientAlternatesAdapter(Context context, long contactId, long currentId, int viewId,
            int queryMode, OnCheckedItemChangedListener listener) {
        super(context, getCursorForConstruction(context, contactId, queryMode), 0);
        mLayoutInflater = LayoutInflater.from(context);
        mCurrentId = currentId;
        mCheckedItemChangedListener = listener;

        if (queryMode == QUERY_TYPE_EMAIL) {
            mQuery = Queries.EMAIL;
        } else if (queryMode == QUERY_TYPE_PHONE) {
            mQuery = Queries.PHONE;
        } else {
            mQuery = Queries.EMAIL;
            Log.e(TAG, "Unsupported query type: " + queryMode);
        }
    }

    private static Cursor getCursorForConstruction(Context context, long contactId, int queryType) {
        final Cursor cursor;
        if (queryType == QUERY_TYPE_EMAIL) {
            cursor = context.getContentResolver().query(
                    Queries.EMAIL.getContentUri(),
                    Queries.EMAIL.getProjection(),
                    Queries.EMAIL.getProjection()[Queries.Query.CONTACT_ID] + " =?", new String[] {
                        String.valueOf(contactId)
                    }, null);
        } else {
            cursor = context.getContentResolver().query(
                    Queries.PHONE.getContentUri(),
                    Queries.PHONE.getProjection(),
                    Queries.PHONE.getProjection()[Queries.Query.CONTACT_ID] + " =?", new String[] {
                        String.valueOf(contactId)
                    }, null);
        }
        return removeDuplicateDestinations(cursor);
    }

    /**
* @return a new cursor based on the given cursor with all duplicate destinations removed.
*
* It's only intended to use for the alternate list, so...
* - This method ignores all other fields and dedupe solely on the destination. Normally,
* if a cursor contains multiple contacts and they have the same destination, we'd still want
* to show both.
* - This method creates a MatrixCursor, so all data will be kept in memory. We wouldn't want
* to do this if the original cursor is large, but it's okay here because the alternate list
* won't be that big.
*/
    // Visible for testing
    /* package */ static Cursor removeDuplicateDestinations(Cursor original) {
        final MatrixCursor result = new MatrixCursor(
                original.getColumnNames(), original.getCount());
        final HashSet<String> destinationsSeen = new HashSet<String>();

        original.moveToPosition(-1);
        while (original.moveToNext()) {
            final String destination = original.getString(Query.DESTINATION);
            if (destinationsSeen.contains(destination)) {
                continue;
            }
            destinationsSeen.add(destination);

            result.addRow(new Object[] {
                    original.getString(Query.NAME),
                    original.getString(Query.DESTINATION),
                    original.getInt(Query.DESTINATION_TYPE),
                    original.getString(Query.DESTINATION_LABEL),
                    original.getLong(Query.CONTACT_ID),
                    original.getLong(Query.DATA_ID),
                    original.getString(Query.PHOTO_THUMBNAIL_URI),
                    original.getInt(Query.DISPLAY_NAME_SOURCE)
                    });
        }

        return result;
    }

    @Override
    public long getItemId(int position) {
        Cursor c = getCursor();
        if (c.moveToPosition(position)) {
            c.getLong(Queries.Query.DATA_ID);
        }
        return -1;
    }

    public RecipientEntry getRecipientEntry(int position) {
        Cursor c = getCursor();
        c.moveToPosition(position);
        return RecipientEntry.constructTopLevelEntry(
                c.getString(Queries.Query.NAME),
                c.getInt(Queries.Query.DISPLAY_NAME_SOURCE),
                c.getString(Queries.Query.DESTINATION),
                c.getInt(Queries.Query.DESTINATION_TYPE),
                c.getString(Queries.Query.DESTINATION_LABEL),
                c.getLong(Queries.Query.CONTACT_ID),
                c.getLong(Queries.Query.DATA_ID),
                c.getString(Queries.Query.PHOTO_THUMBNAIL_URI));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Cursor cursor = getCursor();
        cursor.moveToPosition(position);
        if (convertView == null) {
            convertView = newView();
        }
        if (cursor.getLong(Queries.Query.DATA_ID) == mCurrentId) {
            mCheckedItemPosition = position;
            if (mCheckedItemChangedListener != null) {
                mCheckedItemChangedListener.onCheckedItemChanged(mCheckedItemPosition);
            }
        }
        bindView(convertView, convertView.getContext(), cursor);
        return convertView;
    }

    // TODO: this is VERY similar to the BaseRecipientAdapter. Can we combine
    // somehow?
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        int position = cursor.getPosition();

        TextView display = (TextView) view.findViewById(android.R.id.title);
        ImageView imageView = (ImageView) view.findViewById(android.R.id.icon);
        RecipientEntry entry = getRecipientEntry(position);
        if (position == 0) {
            display.setText(cursor.getString(Queries.Query.NAME));
            display.setVisibility(View.VISIBLE);
            // TODO: see if this needs to be done outside the main thread
            // as it may be too slow to get immediately.
            imageView.setImageURI(entry.getPhotoThumbnailUri());
            imageView.setVisibility(View.VISIBLE);
        } else {
            display.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
        }
        TextView destination = (TextView) view.findViewById(android.R.id.text1);
        destination.setText(cursor.getString(Queries.Query.DESTINATION));

        TextView destinationType = (TextView) view.findViewById(android.R.id.text2);
        if (destinationType != null) {
            destinationType.setText(mQuery.getTypeLabel(context.getResources(),
                    cursor.getInt(Queries.Query.DESTINATION_TYPE),
                    cursor.getString(Queries.Query.DESTINATION_LABEL)).toString().toUpperCase());
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return newView();
    }

    private View newView() {
        return mLayoutInflater.inflate(R.layout.chips_recipient_dropdown_item, null);
    }

    /*package*/ static interface OnCheckedItemChangedListener {
        public void onCheckedItemChanged(int position);
    }
}
