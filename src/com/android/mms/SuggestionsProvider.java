/*
 * Copyright (C) 2009 Google Inc.
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

package com.android.mms;

import java.util.ArrayList;

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.CrossProcessCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

/**
 * Suggestions provider for mms.  Queries the "words" table to provide possible word suggestions.
 */
public class SuggestionsProvider extends android.content.ContentProvider {

    final static String AUTHORITY = "com.android.mms.SuggestionsProvider";
//    final static int MODE = DATABASE_MODE_QUERIES + DATABASE_MODE_2LINES;

    public SuggestionsProvider() {
        super();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        Uri u = Uri.parse(String.format(
                "content://mms-sms/searchSuggest?pattern=%s",
                selectionArgs[0]));
        Cursor c = getContext().getContentResolver().query(
                u,
                null,
                null,
                null,
                null);

        return new SuggestionsCursor(c, selectionArgs[0]);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private class SuggestionsCursor implements CrossProcessCursor {
        Cursor mDatabaseCursor;
        int mColumnCount;
        int mCurrentRow;
        ArrayList<Row> mRows = new ArrayList<Row>();
        String mQuery;

        public SuggestionsCursor(Cursor cursor, String query) {
            mDatabaseCursor = cursor;
            mQuery = query;

            mColumnCount = cursor.getColumnCount();
            try {
                computeRows();
            } catch (SQLiteException ex) {
                // This can happen if the user enters -n (anything starting with -).
                // sqlite3/fts3 can't handle it.  Google for "logic error or missing database fts3"
                // for commentary on it.
                mRows.clear(); // assume no results
            }
        }

        public int getCount() {
            return mRows.size();
        }

        private class Row {
            private String mSnippet;
            private int mRowNumber;

            public Row(int row, String snippet) {
                mSnippet = snippet.trim();
                mRowNumber = row;
            }
            public String getSnippet() {
                return mSnippet;
            }
        }

        /*
         * Compute rows for rows in the cursor.  The cursor can contain duplicates which
         * are filtered out in the while loop.  Using DISTINCT on the result of the
         * FTS3 snippet function does not work so we do it here in the code.
         */
        private void computeRows() {
            int snippetColumn = mDatabaseCursor.getColumnIndex("snippet");

            int count = mDatabaseCursor.getCount();
            String previousSnippet = null;

            for (int i = 0; i < count; i++) {
                mDatabaseCursor.moveToPosition(i);
                String snippet = mDatabaseCursor.getString(snippetColumn);
                if (!TextUtils.equals(previousSnippet, snippet)) {
                    mRows.add(new Row(i, snippet));
                    previousSnippet = snippet;
                }
            }
        }

        private int [] computeOffsets(String offsetsString) {
            String [] vals = offsetsString.split(" ");

            int [] retvals = new int[vals.length];
            for (int i = retvals.length-1; i >= 0; i--) {
                retvals[i] = Integer.parseInt(vals[i]);
            }
            return retvals;
        }

        public void fillWindow(int position, CursorWindow window) {
            int count = getCount();
            if (position < 0 || position > count + 1) {
                return;
            }
            window.acquireReference();
            try {
                int oldpos = getPosition();
                int pos = position;
                window.clear();
                window.setStartPosition(position);
                int columnNum = getColumnCount();
                window.setNumColumns(columnNum);
                while (moveToPosition(pos) && window.allocRow()) {
                    for (int i = 0; i < columnNum; i++) {
                        String field = getString(i);
                        if (field != null) {
                            if (!window.putString(field, pos, i)) {
                                window.freeLastRow();
                                break;
                            }
                        } else {
                            if (!window.putNull(pos, i)) {
                                window.freeLastRow();
                                break;
                            }
                        }
                    }
                    ++pos;
                }
                moveToPosition(oldpos);
            } catch (IllegalStateException e){
                // simply ignore it
            } finally {
                window.releaseReference();
            }
        }

        public CursorWindow getWindow() {
            return null;
        }

        public boolean onMove(int oldPosition, int newPosition) {
            return ((CrossProcessCursor)mDatabaseCursor).onMove(oldPosition, newPosition);
        }

        /*
         * These "virtual columns" are columns which don't exist in the underlying
         * database cursor but are exported by this cursor.  For example, we compute
         * a "word" by taking the substring of the full row text in the words table
         * using the provided offsets.
         */
        private String [] mVirtualColumns = new String [] {
                SearchManager.SUGGEST_COLUMN_INTENT_DATA,
                SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
                SearchManager.SUGGEST_COLUMN_TEXT_1,
            };

        // Cursor column offsets for the above virtual columns.
        // These columns exist after the natural columns in the
        // database cursor.  So, for example, the column called
        // SUGGEST_COLUMN_TEXT_1 comes 3 after mDatabaseCursor.getColumnCount().
        private final int INTENT_DATA_COLUMN = 0;
        private final int INTENT_ACTION_COLUMN = 1;
        private final int INTENT_EXTRA_DATA_COLUMN = 2;
        private final int INTENT_TEXT_COLUMN = 3;


        public int getColumnCount() {
            return mColumnCount + mVirtualColumns.length;
        }

        public int getColumnIndex(String columnName) {
            for (int i = 0; i < mVirtualColumns.length; i++) {
                if (mVirtualColumns[i].equals(columnName)) {
                    return mColumnCount + i;
                }
            }
            return mDatabaseCursor.getColumnIndex(columnName);
        }

        public String [] getColumnNames() {
            String [] x = mDatabaseCursor.getColumnNames();
            String [] y = new String [x.length + mVirtualColumns.length];

            for (int i = 0; i < x.length; i++) {
                y[i] = x[i];
            }

            for (int i = 0; i < mVirtualColumns.length; i++) {
                y[x.length + i] = mVirtualColumns[i];
            }

            return y;
        }

        public boolean moveToPosition(int position) {
            if (position >= 0 && position < mRows.size()) {
                mCurrentRow = position;
                mDatabaseCursor.moveToPosition(mRows.get(position).mRowNumber);
                return true;
            } else {
                return false;
            }
        }

        public boolean move(int offset) {
            return moveToPosition(mCurrentRow + offset);
        }

        public boolean moveToFirst() {
            return moveToPosition(0);
        }

        public boolean moveToLast() {
            return moveToPosition(mRows.size() - 1);
        }

        public boolean moveToNext() {
            return moveToPosition(mCurrentRow + 1);
        }

        public boolean moveToPrevious() {
            return moveToPosition(mCurrentRow - 1);
        }

        public String getString(int column) {
            // if we're returning one of the columns in the underlying database column
            // then do so here
            if (column < mColumnCount) {
                return mDatabaseCursor.getString(column);
            }

            // otherwise we're returning one of the synthetic columns.
            // the constants like INTENT_DATA_COLUMN are offsets relative to
            // mColumnCount.
            Row row = mRows.get(mCurrentRow);
            switch (column - mColumnCount) {
                case INTENT_DATA_COLUMN:
                    Uri.Builder b = Uri.parse("content://mms-sms/search").buildUpon();
                    b = b.appendQueryParameter("pattern", row.getSnippet());
                    Uri u = b.build();
                    return u.toString();
                case INTENT_ACTION_COLUMN:
                    return Intent.ACTION_SEARCH;
                case INTENT_EXTRA_DATA_COLUMN:
                    return row.getSnippet();
                case INTENT_TEXT_COLUMN:
                    return row.getSnippet();
                default:
                    return null;
            }
        }

        public void close() {
            mDatabaseCursor.close();
        }

        public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
            mDatabaseCursor.copyStringToBuffer(columnIndex, buffer);
        }

        public void deactivate() {
            mDatabaseCursor.deactivate();
        }

        public byte[] getBlob(int columnIndex) {
            return null;
        }

        public int getColumnIndexOrThrow(String columnName)
                throws IllegalArgumentException {
            return 0;
        }

        public String getColumnName(int columnIndex) {
            return null;
        }

        public double getDouble(int columnIndex) {
            return 0;
        }

        public Bundle getExtras() {
            return Bundle.EMPTY;
        }

        public float getFloat(int columnIndex) {
            return 0;
        }

        public int getInt(int columnIndex) {
            return 0;
        }

        public long getLong(int columnIndex) {
            return 0;
        }

        public int getPosition() {
            return mCurrentRow;
        }

        public short getShort(int columnIndex) {
            return 0;
        }

        public boolean getWantsAllOnMoveCalls() {
            return false;
        }

        public boolean isAfterLast() {
            return mCurrentRow >= mRows.size();
        }

        public boolean isBeforeFirst() {
            return mCurrentRow < 0;
        }

        public boolean isClosed() {
            return mDatabaseCursor.isClosed();
        }

        public boolean isFirst() {
            return mCurrentRow == 0;
        }

        public boolean isLast() {
            return mCurrentRow == mRows.size() - 1;
        }

        public int getType(int columnIndex) {
            throw new UnsupportedOperationException();  // TODO revisit
        }

        public boolean isNull(int columnIndex) {
            return false;  // TODO revisit
        }

        public void registerContentObserver(ContentObserver observer) {
            mDatabaseCursor.registerContentObserver(observer);
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mDatabaseCursor.registerDataSetObserver(observer);
        }

        public boolean requery() {
            return false;
        }

        public Bundle respond(Bundle extras) {
            return mDatabaseCursor.respond(extras);
        }

        public void setNotificationUri(ContentResolver cr, Uri uri) {
            mDatabaseCursor.setNotificationUri(cr, uri);
        }

        public Uri getNotificationUri() {
            return mDatabaseCursor.getNotificationUri();
        }

        public void unregisterContentObserver(ContentObserver observer) {
            mDatabaseCursor.unregisterContentObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mDatabaseCursor.unregisterDataSetObserver(observer);
        }
    }
}
