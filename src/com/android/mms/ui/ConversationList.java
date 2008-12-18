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
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.util.ContactNameCache;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.util.SqliteWrapper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Contacts;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Sms.Conversations;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.concurrent.ConcurrentHashMap;

/**
 * This activity provides a list view of existing conversations.
 */
public class ConversationList extends ListActivity {
    private static final String TAG = "ConversationList";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = Config.LOGV && DEBUG;

    private static final int THREAD_LIST_QUERY_TOKEN = 1701;
    private static final int SEARCH_TOKEN            = 1702;

    private static final int DELETE_CONVERSATION_TOKEN = 1801;
    
    // IDs of the main menu items.
    private static final int MENU_COMPOSE_NEW            = 0;
    private static final int MENU_SEARCH                 = 1;
    private static final int MENU_UNDELIVERED_MESSAGES   = 2;
    private static final int MENU_DELETE_ALL             = 3;
    private static final int MENU_PREFERENCES            = 4;
    private static final int MENU_VIEW_BROADCAST_THREADS = 5;

    // IDs of the context menu items for the list of conversations.
    public static final int MENU_DELETE               = 0;
    private static final int MENU_VIEW                 = 1;

    private Cursor mCursor;
    private final Object mCursorLock = new Object();
    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private CharSequence mTitle;
    private Uri mBaseUri;
    private String mQuery;
    private String[] mProjection;
    private int mQueryToken;
    private String mFilter;
    private boolean mSearchFlag;
    private CachingNameStore mCachingNameStore;

    /**
     * An interface that's passed down to ListAdapters to use
     * for looking up the names of contact numbers.
     */
    public static interface CachingNameStore {
        // Returns comma-separated list of contact's display names
        // given a semicolon-delimited string of canonical phone
        // numbers.
        public String getContactNames(String addresses);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.conversation_list_screen);

        mQueryHandler = new ThreadListQueryHandler(getContentResolver());

        ListView listView = getListView();
        LayoutInflater inflater = LayoutInflater.from(this);
        ConversationHeaderView headerView = (ConversationHeaderView)
                inflater.inflate(R.layout.conversation_header, listView, false);
        headerView.bind(getString(R.string.new_message),
                getString(R.string.create_new_message));
        listView.addHeaderView(headerView, null, true);

        listView.setOnCreateContextMenuListener(mConvListOnCreateContextMenuListener);
        listView.setOnKeyListener(mThreadListKeyListener);

        mCachingNameStore = new CachingNameStoreImpl(this);

        if (savedInstanceState != null) {
            mBaseUri = (Uri) savedInstanceState.getParcelable("base_uri");
            mSearchFlag = savedInstanceState.getBoolean("search_flag");
            mFilter = savedInstanceState.getString("filter");
            mQueryToken = savedInstanceState.getInt("query_token");
        }

        handleCreationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Handle intents that occur after the activity has already been created.
        handleCreationIntent(intent);
    }

    protected void handleCreationIntent(Intent intent) {
        // Handle intents that occur upon creation of the activity.
        initNormalQueryArgs();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mListAdapter != null) {
            mListAdapter.initDraftCache();  // we might have a draft now
            mListAdapter.registerObservers();
        }

        getContentResolver().delete(Threads.OBSOLETE_THREADS_URI, null, null);

        synchronized (mCursorLock) {
            if (mCursor == null) {
                startAsyncQuery();
            } else {
                SqliteWrapper.requery(this, mCursor);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("base_uri", mBaseUri);
        outState.putInt("query_token", mQueryToken);
        outState.putBoolean("search_flag", mSearchFlag);
        if (mSearchFlag) {
            outState.putString("filter", mFilter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mListAdapter != null) {
            mListAdapter.unregisterObservers();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        synchronized (mCursorLock) {
            if (mCursor != null) {
                if (mListAdapter != null) {
                    mListAdapter.changeCursor(null);
                }
                mCursor.close();
                mCursor = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mCursor != null) {
            mCursor.close();
        }
    }

    private void initNormalQueryArgs() {
        Uri.Builder builder = Threads.CONTENT_URI.buildUpon();
        builder.appendQueryParameter("simple", "true");
        mBaseUri = builder.build();
        mQuery = null;
        mProjection = ConversationListAdapter.PROJECTION;
        mQueryToken = THREAD_LIST_QUERY_TOKEN;
        mTitle = getString(R.string.app_label);
    }

    private void startAsyncQuery() {
        try {
            synchronized (mCursorLock) {
                setTitle(getString(R.string.refreshing));
                setProgressBarIndeterminateVisibility(true);

                mQueryHandler.cancelOperation(THREAD_LIST_QUERY_TOKEN);
                // FIXME: I have to pass the mQueryToken as cookie since the
                // AsyncQueryHandler.onQueryComplete() method doesn't provide
                // the same token as what I input here.
                mQueryHandler.startQuery(0, mQueryToken, mBaseUri, mProjection, mQuery, null,
                        Conversations.DEFAULT_SORT_ORDER);
            }
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        menu.add(0, MENU_COMPOSE_NEW, 0, R.string.menu_compose_new).setIcon(
                R.drawable.ic_menu_compose);
        // Removed search as part of b/1205708
        //menu.add(0, MENU_SEARCH, 0, R.string.menu_search).setIcon(
        //        R.drawable.ic_menu_search).setAlphabeticShortcut(SearchManager.MENU_KEY);
        if ((mCursor != null) && (mCursor.getCount() > 0) && !mSearchFlag) {
            menu.add(0, MENU_DELETE_ALL, 0, R.string.menu_delete_all).setIcon(
                    R.drawable.ic_menu_delete);
        }

        // Check undelivered messages
        Cursor mmsCursor = SqliteWrapper.query(this, getContentResolver(),
                                Mms.Outbox.CONTENT_URI, null, null, null, null);
        Cursor smsCursor = SqliteWrapper.query(this, getContentResolver(),
                                Uri.withAppendedPath(Sms.CONTENT_URI, "undelivered"),
                                null, null, null, null);
        if (((mmsCursor != null) && (mmsCursor.getCount() > 0)) ||
                ((smsCursor != null) && (smsCursor.getCount() > 0))) {
                menu.add(0, MENU_UNDELIVERED_MESSAGES, 0, R.string.menu_undelivered_messages).setIcon(
                        R.drawable.ic_menu_undelivered);
        }
        if (mmsCursor != null) {
            mmsCursor.close();
        }
        if (smsCursor != null) {
            smsCursor.close();
        }

        menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences).setIcon(
                R.drawable.ic_menu_preferences);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case MENU_COMPOSE_NEW:
                createNewMessage();
                break;
            case MENU_SEARCH:
                onSearchRequested();
                break;
            case MENU_DELETE_ALL:
                confirmDeleteDialog(new DeleteThreadListener(-1L), true);
                break;
            case MENU_UNDELIVERED_MESSAGES: {
                Intent intent = new Intent(this, UndeliveredMessagesActivity.class);
                startActivityIfNeeded(intent, -1);
                break;
            }
            case MENU_PREFERENCES: {
                Intent intent = new Intent(this, MessagingPreferenceActivity.class);
                startActivityIfNeeded(intent, -1);
                break;
            }
            default:
                return true;
        }
        return false;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "onListItemClick: position=" + position + ", id=" + id);
        }

        if (position == 0) {
            createNewMessage();
        } else if (v instanceof ConversationHeaderView) {
            ConversationHeaderView headerView = (ConversationHeaderView) v;
            ConversationHeader ch = headerView.getConversationHeader();

            // TODO: The 'from' view of the ConversationHeader was
            // repurposed to be the cached display value, rather than
            // the old raw value, which openThread() wanted.  But it
            // turns out openThread() doesn't need it:
            // ComposeMessageActivity will load it.  That's not ideal,
            // though, as it's an SQLite query.  So fix this later to
            // save some latency on starting ComposeMessageActivity.
            String somethingDelimitedAddresses = null;
            openThread(ch.getThreadId(), somethingDelimitedAddresses);
        }
    }

    private void createNewMessage() {
        Intent intent = new Intent(this, ComposeMessageActivity.class);
        startActivity(intent);
    }

    private void openThread(long threadId, String address) {
        Intent intent = new Intent(this, ComposeMessageActivity.class);
        intent.putExtra("thread_id", threadId);
        if (!TextUtils.isEmpty(address)) {
            intent.putExtra("address", address);
        }
        startActivity(intent);
    }

    private final OnCreateContextMenuListener mConvListOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            String address = MessageUtils.getRecipientsByIds(
                    ConversationList.this,
                    mCursor.getString(ConversationListAdapter.COLUMN_RECIPIENTS_IDS));
            // The Recipient IDs column is separated with semicolons for some reason.
            // We should fix this in the content provider rework.
            CharSequence from = (ContactNameCache.getInstance().getContactName(
                    ConversationList.this, address)).replace(';', ',');
            menu.setHeaderTitle(from);

            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            if (info.position > 0) {
                menu.add(0, MENU_VIEW, 0, R.string.menu_view);
                menu.add(0, MENU_DELETE, 0, R.string.menu_delete);
            }
        }
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        long threadId = mCursor.getLong(ConversationListAdapter.COLUMN_ID);
        switch (item.getItemId()) {
            case MENU_DELETE: {
                DeleteThreadListener l = new DeleteThreadListener(threadId);
                confirmDeleteDialog(l, false);
                break;
            }
            case MENU_VIEW: {
                String address = null;
                if (mListAdapter.isSimpleMode()) {
                    address = MessageUtils.getRecipientsByIds(
                                this,
                                mCursor.getString(ConversationListAdapter.COLUMN_RECIPIENTS_IDS));
                } else {
                    String msgType = mCursor.getString(ConversationListAdapter.COLUMN_MESSAGE_TYPE);
                    if (msgType.equals("sms")) {
                        address = mCursor.getString(ConversationListAdapter.COLUMN_SMS_ADDRESS);
                    } else {
                        address = MessageUtils.getAddressByThreadId(this, threadId);
                   }
                }
                openThread(threadId, address);
                break;
            }
            default:
                break;
        }

        return super.onContextItemSelected(item);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        // We override this method to avoid restarting the entire
        // activity when the keyboard is opened (declared in
        // AndroidManifest.xml).  Because the only translatable text
        // in this activity is "New Message", which has the full width
        // of phone to work with, localization shouldn't be a problem:
        // no abbreviated alternate words should be needed even in
        // 'wide' languages like German or Russian.

        super.onConfigurationChanged(newConfig);
        if (DEBUG) Log.v(TAG, "onConfigurationChanged: " + newConfig);
    }

    private void confirmDeleteDialog(OnClickListener listener, boolean deleteAll) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(deleteAll
                ? R.string.confirm_delete_all_conversations
                : R.string.confirm_delete_conversation);

        builder.show();
    }

    private final OnKeyListener mThreadListKeyListener = new OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DEL: {
                        long id = getListView().getSelectedItemId();
                        if (id > 0) {
                            DeleteThreadListener l = new DeleteThreadListener(
                                    id);
                            confirmDeleteDialog(l, false);
                        }
                        return true;
                    }
                    case KeyEvent.KEYCODE_BACK: {
                        if (mSearchFlag) {
                            mSearchFlag = false;
                            initNormalQueryArgs();
                            startAsyncQuery();

                            return true;
                        }
                        break;
                    }
                }
            }
            return false;
        }
    };

    private class DeleteThreadListener implements OnClickListener {
        private final Uri mDeleteUri;
        private final long mThreadId;

        public DeleteThreadListener(long threadId) {
            mThreadId = threadId;

            if (threadId != -1) {
                mDeleteUri = ContentUris.withAppendedId(
                        Threads.CONTENT_URI, threadId);
            } else {
                mDeleteUri = Threads.CONTENT_URI;
            }
        }

        public void onClick(DialogInterface dialog, int whichButton) {
            MessageUtils.handleReadReport(ConversationList.this, mThreadId,
                    PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, new Runnable() {
                public void run() {
                    mQueryHandler.startDelete(DELETE_CONVERSATION_TOKEN,
                            null, mDeleteUri, null, null);
                }
            });
        }
    }

    private final class ThreadListQueryHandler extends AsyncQueryHandler {
        public ThreadListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            synchronized (mCursorLock) {
                if (mCursor != null) {
                    mCursor.close();
                }

                if (cursor != null) {
                    mCursor = cursor;
                    switch ((Integer) cookie) {
                        case THREAD_LIST_QUERY_TOKEN:
                            mListAdapter = new ConversationListAdapter(
                                    ConversationList.this,
                                    cursor,
                                    true,  // simple (non-search)
                                    mListAdapter,
                                    mCachingNameStore);
                            break;
                        case SEARCH_TOKEN:
                            mListAdapter = new ConversationListAdapter(
                                    ConversationList.this,
                                    cursor,
                                    false,  // non-simple (search)
                                    mListAdapter,
                                    mCachingNameStore);
                            break;
                        default:
                            Log.e(TAG, "Bad query token: " + token);
                            break;
                    }

                    ConversationList.this.setListAdapter(mListAdapter);
                } else {
                    Log.e(TAG, "Cannot init the cursor for the thread list.");
                    finish();
                }

                setTitle(mTitle);
                setProgressBarIndeterminateVisibility(false);
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            switch (token) {
            case DELETE_CONVERSATION_TOKEN:
                // Update the notification for new messages since they
                // may be deleted.
                MessagingNotification.updateNewMessageIndicator(ConversationList.this);
                // Update the notification for failed messages since they
                // may be deleted.
                MessagingNotification.updateSendFailedNotification(ConversationList.this);
                break;
            }
        }
    }

    /**
     * This implements the CachingNameStore interface defined above
     * which we pass down to each newly-created ListAdapater, so they
     * share a common, reused cached between activity resumes, not
     * having to hit the Contacts providers all the time.
     */
    private static final class CachingNameStoreImpl implements CachingNameStore {
        private static final String TAG = "ConversationList/CachingNameStoreImpl";
        private final ConcurrentHashMap<String, String> mCachedNames =
                new ConcurrentHashMap<String, String>();
        private final ContentObserver mPhonesObserver;
        private final Context mContext;

        public CachingNameStoreImpl(Context ctxt) {
            mContext = ctxt;
            mPhonesObserver = new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfUpdate) {
                        mCachedNames.clear();
                    }
                };
            ctxt.getContentResolver().registerContentObserver(
                    Contacts.Phones.CONTENT_URI,
                    true, mPhonesObserver);
        }

        // Returns comma-separated list of contact's display names
        // given a semicolon-delimited string of canonical phone
        // numbers, getting data either from cache or via a blocking
        // call to a provider.
        public String getContactNames(String addresses) {
            String value = mCachedNames.get(addresses);
            if (value != null) {
                return value;
            }
            String[] values = addresses.split(";");
            if (values.length < 2) {
                if (DEBUG) Log.v(TAG, "Looking up name: " + addresses);
                ContactNameCache cache = ContactNameCache.getInstance();
                value = (cache.getContactName(mContext, addresses)).replace(';', ',');
            } else {
                int length = 0;
                for (int i = 0; i < values.length; ++i) {
                    values[i] = getContactNames(values[i]);
                    length += values[i].length() + 2;  // 2 for ", "
                }
                StringBuilder sb = new StringBuilder(length);
                sb.append(values[0]);
                for (int i = 1; i < values.length; ++i) {
                    sb.append(", ");
                    sb.append(values[i]);
                }
                value = sb.toString();
            }
            mCachedNames.put(addresses, value);
            return value;
        }

    }
}
