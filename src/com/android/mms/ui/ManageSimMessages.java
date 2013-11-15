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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Telephony.Sms;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mms.R;
import com.android.mms.transaction.MessagingNotification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RILConstants.SimCardID;
import com.android.internal.telephony.TelephonyIntents;
import java.util.ArrayList;
import android.telephony.SmsMessage;
import com.android.mms.util.BrcmDualSimUtils;

/**
 * Displays a list of the SMS messages stored on the ICC.
 */
public class ManageSimMessages extends Activity
        implements View.OnCreateContextMenuListener {
    private static final Uri ICC_URI = Uri.parse("content://sms/icc");
    private static final String TAG = "ManageSimMessages";
    private static final int MENU_COPY_TO_PHONE_MEMORY = 0;
    private static final int MENU_DELETE_FROM_SIM = 1;
    private static final int MENU_VIEW = 2;
    private static final int OPTION_MENU_DELETE_ALL = 0;

    private static final int SHOW_LIST = 0;
    private static final int SHOW_EMPTY = 1;
    private static final int SHOW_BUSY = 2;
    private int mState;


    private ContentResolver mContentResolver;
    private Cursor mCursor = null;
    private ListView mSimList;
    private TextView mMessage;
    private MessageListAdapter mListAdapter = null;
    private AsyncQueryHandler mQueryHandler = null;

    public static final int SIM_FULL_NOTIFICATION_ID = 234;

    static final String STR_SIM2 = "icc2";
    private static final Uri SIM2_URI = Uri.parse("content://sms/icc2");
    public static final String SIM_NAME_EXTRA = "com.android.mms.ui.SimName";
    private Uri mSimUrl = ICC_URI;
    private SimCardID mSimCardId;
    private final ContentObserver simChangeObserver =
            new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfUpdate) {
            refreshMessageList();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        mContentResolver = getContentResolver();
        mQueryHandler = new QueryHandler(mContentResolver, this);
        setContentView(R.layout.sim_list);
        mSimList = (ListView) findViewById(R.id.messages);
        mMessage = (TextView) findViewById(R.id.empty_message);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        init();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);

        init();
    }

    private void init() {
        Intent intent = getIntent();
        if(intent.hasExtra(SIM_NAME_EXTRA)) {
            String simName = intent.getStringExtra(SIM_NAME_EXTRA);
            Log.d(TAG, "-------getStringExtra simName=" + simName);
            if (STR_SIM2.equals(simName)) {
                mSimUrl = SIM2_URI;
                mSimCardId = SimCardID.ID_ONE;
            } else {
                mSimUrl = ICC_URI; // URI of SIM1
                mSimCardId = SimCardID.ID_ZERO;
            }
        } else {
            // if not explicitly stated in intent extra, use URI of SIM1
            mSimUrl = ICC_URI;
        }
        Log.d(TAG, "------mSimUrl=" + mSimUrl);
        MessagingNotification.cancelNotification(getApplicationContext(),
                SIM_FULL_NOTIFICATION_ID);

        updateState(SHOW_BUSY);
        startQuery();
    }

    private class QueryHandler extends AsyncQueryHandler {
        private final ManageSimMessages mParent;

        public QueryHandler(
                ContentResolver contentResolver, ManageSimMessages parent) {
            super(contentResolver);
            mParent = parent;
        }

        @Override
        protected void onQueryComplete(
                int token, Object cookie, Cursor cursor) {
            mCursor = cursor;
            if (mCursor != null) {
                if (!mCursor.moveToFirst()) {
                    // Let user know the SIM is empty
                    updateState(SHOW_EMPTY);
                } else if (mListAdapter == null) {
                    final int simId = (SIM2_URI.equals(mSimUrl) ? SimCardID.ID_ONE.toInt() : SimCardID.ID_ZERO.toInt());
                    // Note that the MessageListAdapter doesn't support auto-requeries. If we
                    // want to respond to changes we'd need to add a line like:
                    //   mListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
                    // See ComposeMessageActivity for an example.
                    Log.d(TAG, "onQueryComplete:simId: "+simId);
                    mListAdapter = new MessageListAdapter(
                            mParent, mCursor, mSimList, false, null, simId);
                    mSimList.setAdapter(mListAdapter);
                    mSimList.setOnCreateContextMenuListener(mParent);
                    updateState(SHOW_LIST);
                } else {
                    mListAdapter.changeCursor(mCursor);
                    updateState(SHOW_LIST);
                }
                startManagingCursor(mCursor);
            } else {
                // Let user know the SIM is empty
                updateState(SHOW_EMPTY);
            }
            // Show option menu when query complete.
            invalidateOptionsMenu();
        }
    }

    private void startQuery() {
        try {
            /* Change the ICC_URI to mSimURl */
            mQueryHandler.startQuery(0, null, mSimUrl, null, null, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
        updateEFSMSReadStatus();
    }

    private void refreshMessageList() {
        updateState(SHOW_BUSY);
        if (mCursor != null) {
            stopManagingCursor(mCursor);
            mCursor.close();
        }
        startQuery();
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        menu.add(0, MENU_COPY_TO_PHONE_MEMORY, 0,
                 R.string.sim_copy_to_phone_memory);
        menu.add(0, MENU_DELETE_FROM_SIM, 0, R.string.sim_delete);

        // TODO: Enable this once viewMessage is written.
        // menu.add(0, MENU_VIEW, 0, R.string.sim_view);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException exception) {
            Log.e(TAG, "Bad menuInfo.", exception);
            return false;
        }

        final Cursor cursor = (Cursor) mListAdapter.getItem(info.position);

        switch (item.getItemId()) {
            case MENU_COPY_TO_PHONE_MEMORY:
                copyToPhoneMemory(cursor);
                return true;
            case MENU_DELETE_FROM_SIM:
                confirmDeleteDialog(new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        updateState(SHOW_BUSY);
                        deleteFromSim(cursor);
                        dialog.dismiss();
                    }
                }, R.string.confirm_delete_SIM_message);
                return true;
            case MENU_VIEW:
                viewMessage(cursor);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerSimChangeObserver();

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        registerReceiver(mIccCardAbsentReceiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        mContentResolver.unregisterContentObserver(simChangeObserver);

        unregisterReceiver(mIccCardAbsentReceiver);
    }

    private void registerSimChangeObserver() {
        /* Change the ICC_URI to mSimURl */
        mContentResolver.registerContentObserver(
                mSimUrl, true, simChangeObserver);
    }

    private void copyToPhoneMemory(Cursor cursor) {
        String address = cursor.getString(
                cursor.getColumnIndexOrThrow("address"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        Long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
        int simId = SIM2_URI.equals(mSimUrl)? SimCardID.ID_ONE.toInt():SimCardID.ID_ZERO.toInt();

        try {
            if (isIncomingMessage(cursor)) {
                Sms.Inbox.addMessage(mContentResolver, address, body, null, date, true /* read */, simId);
            } else {
                Sms.Sent.addMessage(mContentResolver, address, body, null, date, simId);
            }
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private boolean isIncomingMessage(Cursor cursor) {
        int messageStatus = cursor.getInt(
                cursor.getColumnIndexOrThrow("status"));

        return (messageStatus == SmsManager.STATUS_ON_ICC_READ) ||
               (messageStatus == SmsManager.STATUS_ON_ICC_UNREAD);
    }

    private void deleteFromSim(Cursor cursor) {
        String messageIndexString =
                cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
        /* Change the ICC_URI to mSimURl */
        Uri simUri = mSimUrl.buildUpon().appendPath(messageIndexString).build();

        SqliteWrapper.delete(this, mContentResolver, simUri, null, null);
    }

    private void deleteAllFromSim() {
        Cursor cursor = (Cursor) mListAdapter.getCursor();

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int count = cursor.getCount();

                for (int i = 0; i < count; ++i) {
                    deleteFromSim(cursor);
                    cursor.moveToNext();
                }
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (mState == SHOW_LIST && (null != mCursor) && (mCursor.getCount() > 0)) {
            menu.add(0, OPTION_MENU_DELETE_ALL, 0, R.string.menu_delete_messages).setIcon(
                    android.R.drawable.ic_menu_delete);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTION_MENU_DELETE_ALL:
                confirmDeleteDialog(new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        updateState(SHOW_BUSY);
                        deleteAllFromSim();
                        dialog.dismiss();
                    }
                }, R.string.confirm_delete_all_SIM_messages);
                break;

            case android.R.id.home:
                // The user clicked on the Messaging icon in the action bar. Take them back from
                // wherever they came from
                finish();
                break;
        }

        return true;
    }

    private void confirmDeleteDialog(OnClickListener listener, int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(messageId);

        builder.show();
    }

    private int simUri2SimId(Uri uri) {
        if(uri.equals(SIM2_URI)) {
            return SimCardID.ID_ONE.toInt();
        } else {
            return SimCardID.ID_ZERO.toInt();
        }
    }

    private void updateState(int state) {
        if (mState == state) {
            return;
        }

        int simId = simUri2SimId(mSimUrl);
        mState = state;
        switch (state) {
            case SHOW_LIST:
                mSimList.setVisibility(View.VISIBLE);
                mMessage.setVisibility(View.GONE);
                if(SimCardID.ID_ONE.toInt() == simId) {
                    setTitle(getString(R.string.sim2_manage_messages_title));
                } else {
                    if (BrcmDualSimUtils.isSupportDualSim()) {
                        setTitle(getString(R.string.sim1_manage_messages_title));
                    } else {
                setTitle(getString(R.string.sim_manage_messages_title));
                    }
                }
                setProgressBarIndeterminateVisibility(false);
                mSimList.requestFocus();
                break;
            case SHOW_EMPTY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.VISIBLE);
                if(SimCardID.ID_ONE.toInt() == simId) {
                    setTitle(getString(R.string.sim2_manage_messages_title));
                } else {
                    if (BrcmDualSimUtils.isSupportDualSim()) {
                        setTitle(getString(R.string.sim1_manage_messages_title));
                    } else {
                        setTitle(getString(R.string.sim_manage_messages_title));
                    }
                }
                setProgressBarIndeterminateVisibility(false);
                break;
            case SHOW_BUSY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.GONE);
                setTitle(getString(R.string.refreshing));
                setProgressBarIndeterminateVisibility(true);
                break;
            default:
                Log.e(TAG, "Invalid State");
        }
    }

    private void viewMessage(Cursor cursor) {
        // TODO: Add this.
    }
    private class IccReadUpdateRunnable implements Runnable {
        public void run() {
            final SmsManager smsMgr = SmsManager.getDefault((SIM2_URI.equals(mSimUrl) ? SimCardID.ID_ONE : SimCardID.ID_ZERO));
            ArrayList<SmsMessage> messagesList = smsMgr.getAllMessagesFromIcc();
            SmsMessage sms;

            for(int i = messagesList.size()-1; i >= 0; --i) {
                sms = messagesList.get(i);
                if(SmsManager.STATUS_ON_ICC_UNREAD == sms.getStatusOnIcc()) {
                    smsMgr.updateMessageOnIcc(sms.getIndexOnIcc(), SmsManager.STATUS_ON_ICC_READ, sms.getPdu());
                }
            }
        }
    }

    // GCF 8.2.2: EF SMS read bit has to be set after it's been read
    private void updateEFSMSReadStatus() {
        Log.i(TAG, "updateEFSMSReadStatus");
        // Initiate a thread for SIM SMS read status update since SIM IO may be slow
        Thread t= new Thread(new IccReadUpdateRunnable());
        t.start();
    }

    /**
     * Receives SIM Absent intent.
     * When a broadcasted intent of SIM absent is received,
     * call setup activity of the relative SIM should be finished.
     */
    BroadcastReceiver mIccCardAbsentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                final String iccCardState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                final SimCardID simCardId = (SimCardID)(intent.getExtra("simId", SimCardID.ID_ZERO));
                if (iccCardState.equals(IccCardConstants.INTENT_VALUE_ICC_ABSENT)
                        && mSimCardId == simCardId) {
                    Log.d(TAG, "IccCard.MSG_SIM_STATE_ABSENT simCardId = " + simCardId);
                    makeThisFinish();
                }
            }
        }
    };

    /**
     * Finish this activity.
     * This is called when SIM removed.
     */
    private void makeThisFinish() {
        this.finish();
    }
}

