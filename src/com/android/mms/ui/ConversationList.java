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
import com.android.mms.MmsApp;
import com.android.mms.LogTag;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.util.DraftCache;
import com.android.mms.util.Recycler;

import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.util.SqliteWrapper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Contacts;
import android.provider.Contacts.Intents.Insert;
import android.provider.Telephony.Mms;
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

/**
 * This activity provides a list view of existing conversations.
 */
public class ConversationList extends ListActivity
            implements DraftCache.OnDraftChangedListener {
    private static final String TAG = "ConversationList";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG;

    private static final int THREAD_LIST_QUERY_TOKEN = 1701;

    private static final int DELETE_CONVERSATION_TOKEN = 1801;

    // IDs of the main menu items.
    public static final int MENU_COMPOSE_NEW          = 0;
    public static final int MENU_SEARCH               = 1;
    public static final int MENU_DELETE_ALL           = 3;
    public static final int MENU_PREFERENCES          = 4;

    // IDs of the context menu items for the list of conversations.
    public static final int MENU_DELETE               = 0;
    public static final int MENU_VIEW                 = 1;
    public static final int MENU_VIEW_CONTACT         = 2;
    public static final int MENU_ADD_TO_CONTACTS      = 3;

    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private CharSequence mTitle;
    private SharedPreferences mPrefs;
    private Handler mHandler;

    static private final String CHECKED_MESSAGE_LIMITS = "checked_message_limits";

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

        initListAdapter();

        handleCreationIntent(getIntent());

        mHandler = new Handler();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean checkedMessageLimits = mPrefs.getBoolean(CHECKED_MESSAGE_LIMITS, false);
        if (DEBUG) Log.v(TAG, "checkedMessageLimits: " + checkedMessageLimits);
        if (!checkedMessageLimits || DEBUG) {
            runOneTimeStorageLimitCheckForLegacyMessages();
        }
    }

    private void initListAdapter() {
        mListAdapter = new ConversationListAdapter(this, null);
        setListAdapter(mListAdapter);
    }

    /**
     * Checks to see if the number of MMS and SMS messages are under the limits for the
     * recycler. If so, it will automatically turn on the recycler setting. If not, it
     * will prompt the user with a message and point them to the setting to manually
     * turn on the recycler.
     */
    public synchronized void runOneTimeStorageLimitCheckForLegacyMessages() {
        if (Recycler.isAutoDeleteEnabled(this)) {
            if (DEBUG) Log.v(TAG, "recycler is already turned on");
            // The recycler is already turned on. We don't need to check anything or warn
            // the user, just remember that we've made the check.
            markCheckedMessageLimit();
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                if (Recycler.checkForThreadsOverLimit(ConversationList.this)) {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit TRUE");
                    // Dang, one or more of the threads are over the limit. Show an activity
                    // that'll encourage the user to manually turn on the setting. Delay showing
                    // this activity until a couple of seconds after the conversation list appears.
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            Intent intent = new Intent(ConversationList.this,
                                    WarnOfStorageLimitsActivity.class);
                            startActivity(intent);
                        }
                    }, 2000);
                } else {
                    if (DEBUG) Log.v(TAG, "checkForThreadsOverLimit silently turning on recycler");
                    // No threads were over the limit. Turn on the recycler by default.
                    runOnUiThread(new Runnable() {
                        public void run() {
                            SharedPreferences.Editor editor = mPrefs.edit();
                            editor.putBoolean(MessagingPreferenceActivity.AUTO_DELETE, true);
                            editor.commit();
                        }
                    });
                }
                // Remember that we don't have to do the check anymore when starting MMS.
                runOnUiThread(new Runnable() {
                    public void run() {
                        markCheckedMessageLimit();
                    }
                });
            }
        }).start();
    }

    /**
     * Mark in preferences that we've checked the user's message limits. Once checked, we'll
     * never check them again, unless the user wipe-data or resets the device.
     */
    private void markCheckedMessageLimit() {
        if (DEBUG) Log.v(TAG, "markCheckedMessageLimit");
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(CHECKED_MESSAGE_LIMITS, true);
        editor.commit();
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

        DraftCache.getInstance().addOnDraftChangedListener(this);

        Conversation.cleanup(this);

        if (Log.isLoggable(LogTag.APP, Log.DEBUG)) {
            log("onResume: refresh draft cache");
        }

        // Make sure the draft cache is up to date.
        DraftCache.getInstance().refresh();

        startAsyncQuery();

        Contact.invalidateCache();
    }

    @Override
    protected void onPause() {
        super.onPause();

        DraftCache.getInstance().removeOnDraftChangedListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        mListAdapter.changeCursor(null);
    }

    public void onDraftChanged(long threadId, boolean hasDraft) {
        // Run notifyDataSetChanged() on the main thread.
        mQueryHandler.post(new Runnable() {
            public void run() {
                mListAdapter.notifyDataSetChanged();
            }
        });
    }

    private void initNormalQueryArgs() {
        mTitle = getString(R.string.app_label);
    }

    private void startAsyncQuery() {
        try {
            setTitle(getString(R.string.refreshing));
            setProgressBarIndeterminateVisibility(true);

            Conversation.startQueryForAll(mQueryHandler, THREAD_LIST_QUERY_TOKEN);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        menu.add(0, MENU_COMPOSE_NEW, 0, R.string.menu_compose_new).setIcon(
                com.android.internal.R.drawable.ic_menu_compose);

        if (mListAdapter.getCount() > 0) {
            menu.add(0, MENU_DELETE_ALL, 0, R.string.menu_delete_all).setIcon(
                    android.R.drawable.ic_menu_delete);
        }

        menu.add(0, MENU_SEARCH, 0, android.R.string.search_go).
            setIcon(android.R.drawable.ic_menu_search).
            setAlphabeticShortcut(android.app.SearchManager.MENU_KEY);

        menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences).setIcon(
                android.R.drawable.ic_menu_preferences);

        return true;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null /*appData*/, false);
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
            openThread(ch.getThreadId());
        }
    }

    private void createNewMessage() {
        startActivity(ComposeMessageActivity.createIntent(this, 0));
    }

    private void openThread(long threadId) {
        startActivity(ComposeMessageActivity.createIntent(this, threadId));
    }

    public static Intent createAddContactIntent(String address) {
        // address must be a single recipient
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(Contacts.People.CONTENT_ITEM_TYPE);
        if (Mms.isEmailAddress(address)) {
            intent.putExtra(Insert.EMAIL, address);
        } else {
            intent.putExtra(Insert.PHONE, address);
        }

        return intent;
    }

    private final OnCreateContextMenuListener mConvListOnCreateContextMenuListener =
        new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            Cursor cursor = mListAdapter.getCursor();
            if (cursor.getPosition() < 0) {
                return;
            }
            Conversation conv = Conversation.from(ConversationList.this, cursor);
            ContactList recipients = conv.getRecipients();
            menu.setHeaderTitle(recipients.formatNames(","));

            AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) menuInfo;
            if (info.position > 0) {
                menu.add(0, MENU_VIEW, 0, R.string.menu_view);

                // Only show if there's a single recipient
                if (recipients.size() == 1) {
                    // do we have this recipient in contacts?
                    if (recipients.get(0).existsInDatabase()) {
                        menu.add(0, MENU_VIEW_CONTACT, 0, R.string.menu_view_contact);
                    } else {
                        menu.add(0, MENU_ADD_TO_CONTACTS, 0, R.string.menu_add_to_contacts);
                    }
                }
                menu.add(0, MENU_DELETE, 0, R.string.menu_delete);
            }
        }
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Cursor cursor = mListAdapter.getCursor();
        if (cursor.getPosition() >= 0) {
            Conversation conv = Conversation.from(ConversationList.this, cursor);
            long threadId = conv.getThreadId();
            switch (item.getItemId()) {
            case MENU_DELETE: {
                DeleteThreadListener l = new DeleteThreadListener(threadId);
                confirmDeleteDialog(l, false);
                break;
            }
            case MENU_VIEW: {
                openThread(threadId);
                break;
            }
            case MENU_VIEW_CONTACT: {
                Contact contact = conv.getRecipients().get(0);
                Intent intent = new Intent(Intent.ACTION_VIEW, contact.getUri());
                startActivity(intent);
                break;
            }
            case MENU_ADD_TO_CONTACTS: {
                String address = conv.getRecipients().get(0).getNumber();
                startActivity(createAddContactIntent(address));
                break;
            }
            default:
                break;
            }
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
        builder.setPositiveButton(R.string.delete_unlocked, listener);
        builder.setNeutralButton(R.string.delete_all, listener);
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
                }
            }
            return false;
        }
    };

    private class DeleteThreadListener implements OnClickListener {
        private final long mThreadId;

        public DeleteThreadListener(long threadId) {
            mThreadId = threadId;
        }

        public void onClick(DialogInterface dialog, final int whichButton) {
            MessageUtils.handleReadReport(ConversationList.this, mThreadId,
                    PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, new Runnable() {
                public void run() {
                    int token = DELETE_CONVERSATION_TOKEN;
                    boolean deleteLocked = whichButton == DialogInterface.BUTTON_NEUTRAL;
                    if (mThreadId == -1) {
                        Conversation.startDeleteAll(mQueryHandler, token, deleteLocked);
                        DraftCache.getInstance().refresh();
                    } else {
                        Conversation.startDelete(mQueryHandler, token, deleteLocked, mThreadId);
                        DraftCache.getInstance().setDraftState(mThreadId, false);
                    }
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
            switch (token) {
            case THREAD_LIST_QUERY_TOKEN:
                mListAdapter.changeCursor(cursor);
                setTitle(mTitle);
                setProgressBarIndeterminateVisibility(false);
                break;
            default:
                Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            switch (token) {
            case DELETE_CONVERSATION_TOKEN:
                // Make sure the conversation cache reflects the threads in the DB.
                Conversation.init(ConversationList.this);

                // Update the notification for new messages since they
                // may be deleted.
                MessagingNotification.updateNewMessageIndicator(ConversationList.this);
                // Update the notification for failed messages since they
                // may be deleted.
                MessagingNotification.updateSendFailedNotification(ConversationList.this);

                // Make sure the list reflects the delete
                startAsyncQuery();

                onContentChanged();
                break;
            }
        }
    }

    private void log(String format, Object... args) {
        String s = String.format(format, args);
        Log.d(TAG, "[" + Thread.currentThread().getId() + "] " + s);
    }
}
