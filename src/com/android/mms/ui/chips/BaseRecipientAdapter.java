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

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.provider.Telephony.Mms;
import com.android.ex.chips.R;

/**
* Adapter for showing a recipient list.
*/
public abstract class BaseRecipientAdapter extends BaseAdapter implements Filterable,
        AccountSpecifier {
    private static final String TAG = "BaseRecipientAdapter";

    private static final boolean DEBUG = false;

    /**
* The preferred number of results to be retrieved. This number may be
* exceeded if there are several directories configured, because we will use
* the same limit for all directories.
*/
    private static final int DEFAULT_PREFERRED_MAX_RESULT_COUNT = 10;

    /**
* The number of extra entries requested to allow for duplicates. Duplicates
* are removed from the overall result.
*/
    private static final int ALLOWANCE_FOR_DUPLICATES = 5;

    // This is ContactsContract.PRIMARY_ACCOUNT_NAME. Available from ICS as hidden
    private static final String PRIMARY_ACCOUNT_NAME = "name_for_primary_account";
    // This is ContactsContract.PRIMARY_ACCOUNT_TYPE. Available from ICS as hidden
    private static final String PRIMARY_ACCOUNT_TYPE = "type_for_primary_account";

    /** The number of photos cached in this Adapter. */
    private static final int PHOTO_CACHE_SIZE = 20;

    /**
* The "Waiting for more contacts" message will be displayed if search is not complete
* within this many milliseconds.
*/
    private static final int MESSAGE_SEARCH_PENDING_DELAY = 1000;
    /** Used to prepare "Waiting for more contacts" message. */
    private static final int MESSAGE_SEARCH_PENDING = 1;

    public static final int QUERY_TYPE_EMAIL = 0;
    public static final int QUERY_TYPE_PHONE = 1;
    public static final int QUERY_TYPE_BOTH = 2;

    private final Queries.Query mQuery;
    private final Queries.Query mQuery2;
    private final int mQueryType;

    /**
* Model object for a {@link Directory} row.
*/
    public final static class DirectorySearchParams {
        public long directoryId;
        public String directoryType;
        public String displayName;
        public String accountName;
        public String accountType;
        public CharSequence constraint;
        public DirectoryFilter filter;
    }

    private static class PhotoQuery {
        public static final String[] PROJECTION = {
            Photo.PHOTO
        };

        public static final int PHOTO = 0;
    }

    private static class DirectoryListQuery {

        public static final Uri URI =
                Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "directories");
        public static final String[] PROJECTION = {
            Directory._ID, // 0
            Directory.ACCOUNT_NAME, // 1
            Directory.ACCOUNT_TYPE, // 2
            Directory.DISPLAY_NAME, // 3
            Directory.PACKAGE_NAME, // 4
            Directory.TYPE_RESOURCE_ID, // 5
        };

        public static final int ID = 0;
        public static final int ACCOUNT_NAME = 1;
        public static final int ACCOUNT_TYPE = 2;
        public static final int DISPLAY_NAME = 3;
        public static final int PACKAGE_NAME = 4;
        public static final int TYPE_RESOURCE_ID = 5;
    }

    /** Used to temporarily hold results in Cursor objects. */
    private static class TemporaryEntry {
        public final String displayName;
        public final String destination;
        public final int destinationType;
        public final String destinationLabel;
        public final long contactId;
        public final long dataId;
        public final String thumbnailUriString;
        public final int displayNameSource;

        public TemporaryEntry(Cursor cursor) {
            this.displayName = cursor.getString(Queries.Query.NAME);
            this.destination = cursor.getString(Queries.Query.DESTINATION);
            this.destinationType = cursor.getInt(Queries.Query.DESTINATION_TYPE);
            this.destinationLabel = cursor.getString(Queries.Query.DESTINATION_LABEL);
            this.contactId = cursor.getLong(Queries.Query.CONTACT_ID);
            this.dataId = cursor.getLong(Queries.Query.DATA_ID);
            this.thumbnailUriString = cursor.getString(Queries.Query.PHOTO_THUMBNAIL_URI);
            this.displayNameSource = cursor.getInt(Queries.Query.DISPLAY_NAME_SOURCE);
        }
    }

    /**
* Used to pass results from {@link DefaultFilter#performFiltering(CharSequence)} to
* {@link DefaultFilter#publishResults(CharSequence, android.widget.Filter.FilterResults)}
*/
    private static class DefaultFilterResult {
        public final List<RecipientEntry> entries;
        public final LinkedHashMap<Long, List<RecipientEntry>> entryMap;
        public final List<RecipientEntry> nonAggregatedEntries;
        public final Set<String> existingDestinations;
        public final List<DirectorySearchParams> paramsList;

        public DefaultFilterResult(List<RecipientEntry> entries,
                LinkedHashMap<Long, List<RecipientEntry>> entryMap,
                List<RecipientEntry> nonAggregatedEntries,
                Set<String> existingDestinations,
                List<DirectorySearchParams> paramsList) {
            this.entries = entries;
            this.entryMap = entryMap;
            this.nonAggregatedEntries = nonAggregatedEntries;
            this.existingDestinations = existingDestinations;
            this.paramsList = paramsList;
        }
    }

    /**
* An asynchronous filter used for loading two data sets: email rows from the local
* contact provider and the list of {@link Directory}'s.
*/
    private final class DefaultFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (DEBUG) {
                Log.d(TAG, "start filtering. constraint: " + constraint + ", thread:"
                        + Thread.currentThread());
            }

            final FilterResults results = new FilterResults();
            Cursor defaultDirectoryCursor = null;
            Cursor defaultDirectoryCursor2 = null;
            Cursor directoryCursor = null;

            if (TextUtils.isEmpty(constraint)) {
                clearTempEntries();
                // Return empty results.
                return results;
            }

            try {
                defaultDirectoryCursor = doQuery(mQuery, constraint, mPreferredMaxResultCount, null);
                defaultDirectoryCursor2 = doQuery(mQuery2, constraint, mPreferredMaxResultCount, null);

                if (defaultDirectoryCursor == null && defaultDirectoryCursor2 == null) {
                    if (DEBUG) {
                        Log.w(TAG, "null cursor returned for default Email filter query.");
                    }
                } else {
                    // These variables will become mEntries, mEntryMap, mNonAggregatedEntries, and
                    // mExistingDestinations. Here we shouldn't use those member variables directly
                    // since this method is run outside the UI thread.
                    final LinkedHashMap<Long, List<RecipientEntry>> entryMap =
                            new LinkedHashMap<Long, List<RecipientEntry>>();
                    final List<RecipientEntry> nonAggregatedEntries =
                            new ArrayList<RecipientEntry>();
                    final Set<String> existingDestinations = new HashSet<String>();

                    if (defaultDirectoryCursor != null) {
                        while (defaultDirectoryCursor.moveToNext()) {
                            if (mQuery == Queries.EMAIL &&
                                    !Mms.isEmailAddress(defaultDirectoryCursor.getString(Queries.Query.DESTINATION)))
                                continue;
                            // Note: At this point each entry doesn't contain any photo
                            // (thus getPhotoBytes() returns null).
                            putOneEntry(new TemporaryEntry(defaultDirectoryCursor),
                                    true, entryMap, nonAggregatedEntries, existingDestinations);
                        }
                    }

                    if (defaultDirectoryCursor2 != null) {
                        while (defaultDirectoryCursor2.moveToNext()) {
                            if (mQuery == Queries.EMAIL &&
                                    !Mms.isEmailAddress(defaultDirectoryCursor2.getString(Queries.Query.DESTINATION)))
                                continue;
                            // Note: At this point each entry doesn't contain any photo
                            // (thus getPhotoBytes() returns null).
                            putOneEntry(new TemporaryEntry(defaultDirectoryCursor2),
                                    true, entryMap, nonAggregatedEntries, existingDestinations);
                        }
                    }

                    // We'll copy this result to mEntry in publicResults() (run in the UX thread).
                    final List<RecipientEntry> entries = constructEntryList(false,
                            entryMap, nonAggregatedEntries, existingDestinations);

                    // After having local results, check the size of results. If the results are
                    // not enough, we search remote directories, which will take longer time.
                    final int limit = mPreferredMaxResultCount - existingDestinations.size();
                    final List<DirectorySearchParams> paramsList;
                    if (limit > 0) {
                        if (DEBUG) {
                            Log.d(TAG, "More entries should be needed (current: "
                                    + existingDestinations.size()
                                    + ", remaining limit: " + limit + ") ");
                        }
                        directoryCursor = mContentResolver.query(
                                DirectoryListQuery.URI, DirectoryListQuery.PROJECTION,
                                null, null, null);
                        paramsList = setupOtherDirectories(directoryCursor);
                    } else {
                        // We don't need to search other directories.
                        paramsList = null;
                    }

                    results.values = new DefaultFilterResult(
                            entries, entryMap, nonAggregatedEntries,
                            existingDestinations, paramsList);
                    results.count = 1;
                }
            } finally {
                if (defaultDirectoryCursor != null) {
                    defaultDirectoryCursor.close();
                }
                if (defaultDirectoryCursor2 != null) {
                    defaultDirectoryCursor2.close();
                }
                if (directoryCursor != null) {
                    directoryCursor.close();
                }
            }
            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, FilterResults results) {
            // If a user types a string very quickly and database is slow, "constraint" refers to
            // an older text which shows inconsistent results for users obsolete (b/4998713).
            // TODO: Fix it.
            mCurrentConstraint = constraint;

            clearTempEntries();

            if (results.values != null) {
                DefaultFilterResult defaultFilterResult = (DefaultFilterResult) results.values;
                mEntryMap = defaultFilterResult.entryMap;
                mNonAggregatedEntries = defaultFilterResult.nonAggregatedEntries;
                mExistingDestinations = defaultFilterResult.existingDestinations;

                // If there are no local results, in the new result set, cache off what had been
                // shown to the user for use until the first directory result is returned
                if (defaultFilterResult.entries.size() == 0 &&
                        defaultFilterResult.paramsList != null) {
                    cacheCurrentEntries();
                }

                updateEntries(defaultFilterResult.entries);

                // We need to search other remote directories, doing other Filter requests.
                if (defaultFilterResult.paramsList != null) {
                    final int limit = mPreferredMaxResultCount -
                            defaultFilterResult.existingDestinations.size();
                    startSearchOtherDirectories(constraint, defaultFilterResult.paramsList, limit);
                }
            }

        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            final RecipientEntry entry = (RecipientEntry)resultValue;
            final String displayName = entry.getDisplayName();
            final String emailAddress = entry.getDestination();
            if (TextUtils.isEmpty(displayName) || TextUtils.equals(displayName, emailAddress)) {
                 return emailAddress;
            } else {
                return new Rfc822Token(displayName, emailAddress, null).toString();
            }
        }
    }

    /**
* An asynchronous filter that performs search in a particular directory.
*/
    private final class DirectoryFilter extends Filter {
        private final DirectorySearchParams mParams;
        private int mLimit;

        public DirectoryFilter(DirectorySearchParams params) {
            mParams = params;
        }

        public synchronized void setLimit(int limit) {
            this.mLimit = limit;
        }

        public synchronized int getLimit() {
            return this.mLimit;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (DEBUG) {
                Log.d(TAG, "DirectoryFilter#performFiltering. directoryId: " + mParams.directoryId
                        + ", constraint: " + constraint + ", thread: " + Thread.currentThread());
            }
            final FilterResults results = new FilterResults();
            results.values = null;
            results.count = 0;

            if (!TextUtils.isEmpty(constraint)) {
                final ArrayList<TemporaryEntry> tempEntries = new ArrayList<TemporaryEntry>();

                Cursor cursor = null;
                try {
                    // We don't want to pass this Cursor object to UI thread (b/5017608).
                    // Assuming the result should contain fairly small results (at most ~10),
                    // We just copy everything to local structure.
                    cursor = doQuery(mQuery, constraint, getLimit(), mParams.directoryId);

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            if (mQuery == Queries.EMAIL &&
                                    !Mms.isEmailAddress(cursor.getString(Queries.Query.DESTINATION)))
                                continue;
                            tempEntries.add(new TemporaryEntry(cursor));
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                try {
                    // We don't want to pass this Cursor object to UI thread (b/5017608).
                    // Assuming the result should contain fairly small results (at most ~10),
                    // We just copy everything to local structure.
                    cursor = doQuery(mQuery2, constraint, getLimit(), mParams.directoryId);

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            if (mQuery2 == Queries.EMAIL &&
                                    !Mms.isEmailAddress(cursor.getString(Queries.Query.DESTINATION)))
                                continue;
                            tempEntries.add(new TemporaryEntry(cursor));
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                if (!tempEntries.isEmpty()) {
                    results.values = tempEntries;
                    results.count = 1;
                }
            }

            if (DEBUG) {
                Log.v(TAG, "finished loading directory \"" + mParams.displayName + "\"" +
                        " with query " + constraint);
            }

            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, FilterResults results) {
            if (DEBUG) {
                Log.d(TAG, "DirectoryFilter#publishResult. constraint: " + constraint
                        + ", mCurrentConstraint: " + mCurrentConstraint);
            }
            mDelayedMessageHandler.removeDelayedLoadMessage();
            // Check if the received result matches the current constraint
            // If not - the user must have continued typing after the request was issued, which
            // means several member variables (like mRemainingDirectoryLoad) are already
            // overwritten so shouldn't be touched here anymore.
            if (TextUtils.equals(constraint, mCurrentConstraint)) {
                if (results.count > 0) {
                    @SuppressWarnings("unchecked")
                    final ArrayList<TemporaryEntry> tempEntries =
                            (ArrayList<TemporaryEntry>) results.values;

                    for (TemporaryEntry tempEntry : tempEntries) {
                        putOneEntry(tempEntry, mParams.directoryId == Directory.DEFAULT,
                                mEntryMap, mNonAggregatedEntries, mExistingDestinations);
                    }
                }

                // If there are remaining directories, set up delayed message again.
                mRemainingDirectoryCount--;
                if (mRemainingDirectoryCount > 0) {
                    if (DEBUG) {
                        Log.d(TAG, "Resend delayed load message. Current mRemainingDirectoryLoad: "
                                + mRemainingDirectoryCount);
                    }
                    mDelayedMessageHandler.sendDelayedLoadMessage();
                }

                // If this directory result has some items, or there are no more directories that
                // we are waiting for, clear the temp results
                if (results.count > 0 || mRemainingDirectoryCount == 0) {
                    // Clear the temp entries
                    clearTempEntries();
                }
            }

            // Show the list again without "waiting" message.
            updateEntries(constructEntryList(false,
                    mEntryMap, mNonAggregatedEntries, mExistingDestinations));
        }
    }

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final LayoutInflater mInflater;
    private Account mAccount;
    private final int mPreferredMaxResultCount;
    private final Handler mHandler = new Handler();

    /**
* {@link #mEntries} is responsible for showing every result for this Adapter. To
* construct it, we use {@link #mEntryMap}, {@link #mNonAggregatedEntries}, and
* {@link #mExistingDestinations}.
*
* First, each destination (an email address or a phone number) with a valid contactId is
* inserted into {@link #mEntryMap} and grouped by the contactId. Destinations without valid
* contactId (possible if they aren't in local storage) are stored in
* {@link #mNonAggregatedEntries}.
* Duplicates are removed using {@link #mExistingDestinations}.
*
* After having all results from Cursor objects, all destinations in mEntryMap are copied to
* {@link #mEntries}. If the number of destinations is not enough (i.e. less than
* {@link #mPreferredMaxResultCount}), destinations in mNonAggregatedEntries are also used.
*
* These variables are only used in UI thread, thus should not be touched in
* performFiltering() methods.
*/
    private LinkedHashMap<Long, List<RecipientEntry>> mEntryMap;
    private List<RecipientEntry> mNonAggregatedEntries;
    private Set<String> mExistingDestinations;
    /** Note: use {@link #updateEntries(List)} to update this variable. */
    private List<RecipientEntry> mEntries;
    private List<RecipientEntry> mTempEntries;

    /** The number of directories this adapter is waiting for results. */
    private int mRemainingDirectoryCount;

    /**
* Used to ignore asynchronous queries with a different constraint, which may happen when
* users type characters quickly.
*/
    private CharSequence mCurrentConstraint;

    private final LruCache<Uri, byte[]> mPhotoCacheMap;

    /**
* Handler specific for maintaining "Waiting for more contacts" message, which will be shown
* when:
* - there are directories to be searched
* - results from directories are slow to come
*/
    private final class DelayedMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (mRemainingDirectoryCount > 0) {
                updateEntries(constructEntryList(true,
                        mEntryMap, mNonAggregatedEntries, mExistingDestinations));
            }
        }

        public void sendDelayedLoadMessage() {
            sendMessageDelayed(obtainMessage(MESSAGE_SEARCH_PENDING, 0, 0, null),
                    MESSAGE_SEARCH_PENDING_DELAY);
        }

        public void removeDelayedLoadMessage() {
            removeMessages(MESSAGE_SEARCH_PENDING);
        }
    }

    private final DelayedMessageHandler mDelayedMessageHandler = new DelayedMessageHandler();

    /**
* Constructor for email queries.
*/
    public BaseRecipientAdapter(Context context) {
        this(context, DEFAULT_PREFERRED_MAX_RESULT_COUNT, QUERY_TYPE_EMAIL);
    }

    public BaseRecipientAdapter(Context context, int preferredMaxResultCount) {
        this(context, preferredMaxResultCount, QUERY_TYPE_EMAIL);
    }

    public BaseRecipientAdapter(int queryMode, Context context) {
        this(context, DEFAULT_PREFERRED_MAX_RESULT_COUNT, queryMode);
    }

    public BaseRecipientAdapter(int queryMode, Context context, int preferredMaxResultCount) {
        this(context, preferredMaxResultCount, queryMode);
    }

    public BaseRecipientAdapter(Context context, int preferredMaxResultCount, int queryMode) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mInflater = LayoutInflater.from(context);
        mPreferredMaxResultCount = preferredMaxResultCount;
        mPhotoCacheMap = new LruCache<Uri, byte[]>(PHOTO_CACHE_SIZE);
        mQueryType = queryMode;

        if (queryMode == QUERY_TYPE_EMAIL) {
            mQuery = Queries.EMAIL;
            mQuery2 = null;
        } else if (queryMode == QUERY_TYPE_PHONE) {
            mQuery = Queries.PHONE;
            mQuery2 = null;
        } else if (queryMode == QUERY_TYPE_BOTH) {
            mQuery = Queries.PHONE;
            mQuery2 = Queries.EMAIL;
        } else {
            mQuery = Queries.EMAIL;
            mQuery2 = null;
            Log.e(TAG, "Unsupported query type: " + queryMode);
        }
    }

    public int getQueryType() {
        return mQueryType;
    }

    /**
* Set the account when known. Causes the search to prioritize contacts from that account.
*/
    @Override
    public void setAccount(Account account) {
        mAccount = account;
    }

    /** Will be called from {@link AutoCompleteTextView} to prepare auto-complete list. */
    @Override
    public Filter getFilter() {
        return new DefaultFilter();
    }

    private List<DirectorySearchParams> setupOtherDirectories(Cursor directoryCursor) {
        final PackageManager packageManager = mContext.getPackageManager();
        final List<DirectorySearchParams> paramsList = new ArrayList<DirectorySearchParams>();
        DirectorySearchParams preferredDirectory = null;
        while (directoryCursor.moveToNext()) {
            final long id = directoryCursor.getLong(DirectoryListQuery.ID);

            // Skip the local invisible directory, because the default directory already includes
            // all local results.
            if (id == Directory.LOCAL_INVISIBLE) {
                continue;
            }

            final DirectorySearchParams params = new DirectorySearchParams();
            final String packageName = directoryCursor.getString(DirectoryListQuery.PACKAGE_NAME);
            final int resourceId = directoryCursor.getInt(DirectoryListQuery.TYPE_RESOURCE_ID);
            params.directoryId = id;
            params.displayName = directoryCursor.getString(DirectoryListQuery.DISPLAY_NAME);
            params.accountName = directoryCursor.getString(DirectoryListQuery.ACCOUNT_NAME);
            params.accountType = directoryCursor.getString(DirectoryListQuery.ACCOUNT_TYPE);
            if (packageName != null && resourceId != 0) {
                try {
                    final Resources resources =
                            packageManager.getResourcesForApplication(packageName);
                    params.directoryType = resources.getString(resourceId);
                    if (params.directoryType == null) {
                        Log.e(TAG, "Cannot resolve directory name: "
                                + resourceId + "@" + packageName);
                    }
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "Cannot resolve directory name: "
                            + resourceId + "@" + packageName, e);
                }
            }

            // If an account has been provided and we found a directory that
            // corresponds to that account, place that directory second, directly
            // underneath the local contacts.
            if (mAccount != null && mAccount.name.equals(params.accountName) &&
                    mAccount.type.equals(params.accountType)) {
                preferredDirectory = params;
            } else {
                paramsList.add(params);
            }
        }

        if (preferredDirectory != null) {
            paramsList.add(1, preferredDirectory);
        }

        return paramsList;
    }

    /**
* Starts search in other directories using {@link Filter}. Results will be handled in
* {@link DirectoryFilter}.
*/
    private void startSearchOtherDirectories(
            CharSequence constraint, List<DirectorySearchParams> paramsList, int limit) {
        final int count = paramsList.size();
        // Note: skipping the default partition (index 0), which has already been loaded
        for (int i = 1; i < count; i++) {
            final DirectorySearchParams params = paramsList.get(i);
            params.constraint = constraint;
            if (params.filter == null) {
                params.filter = new DirectoryFilter(params);
            }
            params.filter.setLimit(limit);
            params.filter.filter(constraint);
        }

        // Directory search started. We may show "waiting" message if directory results are slow
        // enough.
        mRemainingDirectoryCount = count - 1;
        mDelayedMessageHandler.sendDelayedLoadMessage();
    }

    private void putOneEntry(TemporaryEntry entry, boolean isAggregatedEntry,
            LinkedHashMap<Long, List<RecipientEntry>> entryMap,
            List<RecipientEntry> nonAggregatedEntries,
            Set<String> existingDestinations) {
        if (existingDestinations.contains(entry.destination)) {
            return;
        }

        existingDestinations.add(entry.destination);

        if (!isAggregatedEntry) {
            nonAggregatedEntries.add(RecipientEntry.constructTopLevelEntry(
                    entry.displayName,
                    entry.displayNameSource,
                    entry.destination, entry.destinationType, entry.destinationLabel,
                    entry.contactId, entry.dataId, entry.thumbnailUriString));
        } else if (entryMap.containsKey(entry.contactId)) {
            // We already have a section for the person.
            final List<RecipientEntry> entryList = entryMap.get(entry.contactId);
            entryList.add(RecipientEntry.constructSecondLevelEntry(
                    entry.displayName,
                    entry.displayNameSource,
                    entry.destination, entry.destinationType, entry.destinationLabel,
                    entry.contactId, entry.dataId, entry.thumbnailUriString));
        } else {
            final List<RecipientEntry> entryList = new ArrayList<RecipientEntry>();
            entryList.add(RecipientEntry.constructTopLevelEntry(
                    entry.displayName,
                    entry.displayNameSource,
                    entry.destination, entry.destinationType, entry.destinationLabel,
                    entry.contactId, entry.dataId, entry.thumbnailUriString));
            entryMap.put(entry.contactId, entryList);
        }
    }

    /**
* Constructs an actual list for this Adapter using {@link #mEntryMap}. Also tries to
* fetch a cached photo for each contact entry (other than separators), or request another
* thread to get one from directories.
*/
    private List<RecipientEntry> constructEntryList(
            boolean showMessageIfDirectoryLoadRemaining,
            LinkedHashMap<Long, List<RecipientEntry>> entryMap,
            List<RecipientEntry> nonAggregatedEntries,
            Set<String> existingDestinations) {
        final List<RecipientEntry> entries = new ArrayList<RecipientEntry>();
        int validEntryCount = 0;
        for (Map.Entry<Long, List<RecipientEntry>> mapEntry : entryMap.entrySet()) {
            final List<RecipientEntry> entryList = mapEntry.getValue();
            final int size = entryList.size();
            for (int i = 0; i < size; i++) {
                RecipientEntry entry = entryList.get(i);
                entries.add(entry);
                tryFetchPhoto(entry);
                validEntryCount++;
            }
            if (validEntryCount > mPreferredMaxResultCount) {
                break;
            }
        }
        if (validEntryCount <= mPreferredMaxResultCount) {
            for (RecipientEntry entry : nonAggregatedEntries) {
                if (validEntryCount > mPreferredMaxResultCount) {
                    break;
                }
                entries.add(entry);
                tryFetchPhoto(entry);

                validEntryCount++;
            }
        }

        return entries;
    }

    /** Resets {@link #mEntries} and notify the event to its parent ListView. */
    private void updateEntries(List<RecipientEntry> newEntries) {
        mEntries = newEntries;
        notifyDataSetChanged();
    }

    private void cacheCurrentEntries() {
        mTempEntries = mEntries;
    }

    private void clearTempEntries() {
        mTempEntries = null;
    }

    private List<RecipientEntry> getEntries() {
        return mTempEntries != null ? mTempEntries : mEntries;
    }

    private void tryFetchPhoto(final RecipientEntry entry) {
        final Uri photoThumbnailUri = entry.getPhotoThumbnailUri();
        if (photoThumbnailUri != null) {
            final byte[] photoBytes = mPhotoCacheMap.get(photoThumbnailUri);
            if (photoBytes != null) {
                entry.setPhotoBytes(photoBytes);
                // notifyDataSetChanged() should be called by a caller.
            } else {
                if (DEBUG) {
                    Log.d(TAG, "No photo cache for " + entry.getDisplayName()
                            + ". Fetch one asynchronously");
                }
                fetchPhotoAsync(entry, photoThumbnailUri);
            }
        }
    }

    private void fetchPhotoAsync(final RecipientEntry entry, final Uri photoThumbnailUri) {
        final AsyncTask<Void, Void, Void> photoLoadTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                final Cursor photoCursor = mContentResolver.query(
                        photoThumbnailUri, PhotoQuery.PROJECTION, null, null, null);
                if (photoCursor != null) {
                    try {
                        if (photoCursor.moveToFirst()) {
                            final byte[] photoBytes = photoCursor.getBlob(PhotoQuery.PHOTO);
                            entry.setPhotoBytes(photoBytes);

                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mPhotoCacheMap.put(photoThumbnailUri, photoBytes);
                                    notifyDataSetChanged();
                                }
                            });
                        }
                    } finally {
                        photoCursor.close();
                    }
                }
                return null;
            }
        };
        photoLoadTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    protected void fetchPhoto(final RecipientEntry entry, final Uri photoThumbnailUri) {
        byte[] photoBytes = mPhotoCacheMap.get(photoThumbnailUri);
        if (photoBytes != null) {
            entry.setPhotoBytes(photoBytes);
            return;
        }
        final Cursor photoCursor = mContentResolver.query(photoThumbnailUri, PhotoQuery.PROJECTION,
                null, null, null);
        if (photoCursor != null) {
            try {
                if (photoCursor.moveToFirst()) {
                    photoBytes = photoCursor.getBlob(PhotoQuery.PHOTO);
                    entry.setPhotoBytes(photoBytes);
                    mPhotoCacheMap.put(photoThumbnailUri, photoBytes);
                }
            } finally {
                photoCursor.close();
            }
        }
    }

    private Cursor doQuery(Queries.Query query, CharSequence constraint, int limit, Long directoryId) {
        if (query == null) return null;
        final Uri.Builder builder = query.getContentFilterUri().buildUpon()
                .appendPath(constraint.toString())
                .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                        String.valueOf(limit + ALLOWANCE_FOR_DUPLICATES));
        if (directoryId != null) {
            builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                    String.valueOf(directoryId));
        }
        if (mAccount != null) {
            builder.appendQueryParameter(PRIMARY_ACCOUNT_NAME, mAccount.name);
            builder.appendQueryParameter(PRIMARY_ACCOUNT_TYPE, mAccount.type);
        }
        final long start = System.currentTimeMillis();
        final Cursor cursor = mContentResolver.query(
                builder.build(), query.getProjection(), null, null, null);
        final long end = System.currentTimeMillis();
        if (DEBUG) {
            Log.d(TAG, "Time for autocomplete (query: " + constraint
                    + ", directoryId: " + directoryId + ", num_of_results: "
                    + (cursor != null ? cursor.getCount() : "null") + "): "
                    + (end - start) + " ms");
        }
        return cursor;
    }

    // TODO: This won't be used at all. We should find better way to quit the thread..
    /*public void close() {
mEntries = null;
mPhotoCacheMap.evictAll();
if (!sPhotoHandlerThread.quit()) {
Log.w(TAG, "Failed to quit photo handler thread, ignoring it.");
}
}*/

    @Override
    public int getCount() {
        final List<RecipientEntry> entries = getEntries();
        return entries != null ? entries.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return getEntries().get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return RecipientEntry.ENTRY_TYPE_SIZE;
    }

    @Override
    public int getItemViewType(int position) {
        return getEntries().get(position).getEntryType();
    }

    @Override
    public boolean isEnabled(int position) {
        return getEntries().get(position).isSelectable();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final RecipientEntry entry = getEntries().get(position);
        String displayName = entry.getDisplayName();
        String destination = entry.getDestination();
        if (TextUtils.isEmpty(displayName) || TextUtils.equals(displayName, destination)) {
            displayName = destination;

            // We only show the destination for secondary entries, so clear it
            // only for the first level.
            if (entry.isFirstLevel()) {
                destination = null;
            }
        }

        final View itemView = convertView != null ? convertView : mInflater.inflate(
                getItemLayout(), parent, false);
        final TextView displayNameView = (TextView) itemView.findViewById(getDisplayNameId());
        final TextView destinationView = (TextView) itemView.findViewById(getDestinationId());
        final TextView destinationTypeView = (TextView) itemView
                .findViewById(getDestinationTypeId());
        final ImageView imageView = (ImageView) itemView.findViewById(getPhotoId());
        displayNameView.setText(displayName);
        if (!TextUtils.isEmpty(destination)) {
            destinationView.setText(destination);
        } else {
            destinationView.setText(null);
        }
        if (destinationTypeView != null) {
            final Queries.Query query;
            if (Mms.isPhoneNumber(destination)) {
                query = Queries.PHONE;
            } else if (Mms.isEmailAddress(destination)) {
                query = Queries.EMAIL;
            } else {
                query = null;
            }
            if (query == null) {
                destinationTypeView.setText("");
            } else {
                final CharSequence destinationType = query
                        .getTypeLabel(mContext.getResources(), entry.getDestinationType(),
                                entry.getDestinationLabel()).toString().toUpperCase();
                destinationTypeView.setText(destinationType);
            }
        }

        if (entry.isFirstLevel()) {
            displayNameView.setVisibility(View.VISIBLE);
            if (imageView != null) {
                imageView.setVisibility(View.VISIBLE);
                final byte[] photoBytes = entry.getPhotoBytes();
                if (photoBytes != null && imageView != null) {
                    final Bitmap photo = BitmapFactory.decodeByteArray(photoBytes, 0,
                            photoBytes.length);
                    imageView.setImageBitmap(photo);
                } else {
                    imageView.setImageResource(getDefaultPhotoResource());
                }
            }
        } else {
            displayNameView.setVisibility(View.GONE);
            if (imageView != null) {
                imageView.setVisibility(View.INVISIBLE);
            }
        }
        return itemView;
    }

    /**
* Returns a layout id for each item inside auto-complete list.
*
* Each View must contain two TextViews (for display name and destination) and one ImageView
* (for photo). Ids for those should be available via {@link #getDisplayNameId()},
* {@link #getDestinationId()}, and {@link #getPhotoId()}.
*/
    protected int getItemLayout() {
        return R.layout.chips_recipient_dropdown_item;
    }

    /**
* Returns a resource ID representing an image which should be shown when ther's no relevant
* photo is available.
*/
    protected int getDefaultPhotoResource() {
        return R.drawable.ic_contact_picture;
    }

    /**
* Returns an id for TextView in an item View for showing a display name. By default
* {@link android.R.id#title} is returned.
*/
    protected int getDisplayNameId() {
        return android.R.id.title;
    }

    /**
* Returns an id for TextView in an item View for showing a destination
* (an email address or a phone number).
* By default {@link android.R.id#text1} is returned.
*/
    protected int getDestinationId() {
        return android.R.id.text1;
    }

    /**
* Returns an id for TextView in an item View for showing the type of the destination.
* By default {@link android.R.id#text2} is returned.
*/
    protected int getDestinationTypeId() {
        return android.R.id.text2;
    }

    /**
* Returns an id for ImageView in an item View for showing photo image for a person. In default
* {@link android.R.id#icon} is returned.
*/
    protected int getPhotoId() {
        return android.R.id.icon;
    }
}
