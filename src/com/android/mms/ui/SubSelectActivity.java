/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;

import com.android.internal.telephony.PhoneConstants;

import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.transaction.SimFullReceiver;
import com.android.internal.telephony.TelephonyIntents;

public class SubSelectActivity extends ListActivity {

    private static String TAG = "SubSelectActivity";

    // If intent has longArrayExtra with key EXTRA_APPOINTED_SUBS, activity
    // only show subs in the longArrayExtra. If intent doesn't has the extra
    // value,activity will show all active subs.
    public static final String EXTRA_APPOINTED_SUBS = "subsArray";
    private List<SubInfoRecord> mSubInfoList = new ArrayList<SubInfoRecord>();
    private String mPreferenceKey;
    private int mPreferenceTitleId;
    private SubSelectAdapter mAdapter;
    private int mOldSubCount = 0;
    private int[] mAppointedSubArray = null;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getExtraValues(getIntent());
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            Log.d(TAG, "onCreate preference key is: " + mPreferenceKey);
        }
        setTitle(mPreferenceTitleId);
        //add action bar
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        registerReceiver(mSimStateReceiver,
                new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED));
        List<SubInfoRecord> oldSubList = SubscriptionManager
                        .getActiveSubInfoList();
        if (oldSubList != null) {
            mOldSubCount = oldSubList.size();
        }
        setAdapter();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mSimStateReceiver);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getExtraValues(intent);
        setTitle(mPreferenceTitleId);
        mAppointedSubArray = intent.getIntArrayExtra(EXTRA_APPOINTED_SUBS);
        refreshAdapter();
    }

    private void getExtraValues(Intent intent) {
        mPreferenceKey = intent.getStringExtra(MessagingPreferenceActivity.PREFERENCE_KEY);
        mPreferenceTitleId = intent
                .getIntExtra(MessagingPreferenceActivity.PREFERENCE_TITLE_ID, -1);
        mAppointedSubArray = intent.getIntArrayExtra(EXTRA_APPOINTED_SUBS);
    }

    private void initialSubInfoList() {
        int simCount = TelephonyManager.getDefault().getSimCount();
        mSubInfoList.clear();
        for (int slotId = 0; slotId < simCount; slotId++) {
            List<SubInfoRecord> subInfoRecordInOneSim = SubscriptionManager.getSubInfoUsingSlotId(
                    slotId);
            if (subInfoRecordInOneSim == null || subInfoRecordInOneSim.size() == 0) {
                continue;
            } else {
                SubInfoRecord infoRecord;
                for (int i = 0; i < subInfoRecordInOneSim.size(); i++) {
                    infoRecord = subInfoRecordInOneSim.get(i);
                    // mNeedShowSubArray == null means intent isn't specified
                    if (mAppointedSubArray == null || isSubIdInNeededShowArray(
                            infoRecord.getSubscriptionId())) {
                        mSubInfoList.add(infoRecord);
                    }
                }
            }
        }
        if (mSubInfoList == null || mSubInfoList.size() == 0) {
            finish();
            return;
        }
    }

    private void setAdapter() {
        initialSubInfoList();
        mAdapter = new SubSelectAdapter(this, mPreferenceKey, mSubInfoList);
        setListAdapter(mAdapter);
    }

    private void refreshAdapter() {
        initialSubInfoList();
        mAdapter.setPreferenceKey(mPreferenceKey);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // start manage SIM message activity if preference is MANAGE_SIM_MESSAGE_MODE.
        // else change the preference data.
        if (MessagingPreferenceActivity.MANAGE_SIM_MESSAGE_MODE.equals(mPreferenceKey)) {
            startManageSimMessages(position);
        } else {
            boolean isChecked =  mAdapter.isChecked(position);
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this)
                    .edit();
            editor.putBoolean(Integer.toString(mSubInfoList.get(position).getSubscriptionId())
                    + "_" + mPreferenceKey, (!isChecked));
            editor.apply();
            CheckBox subCheckBox = (CheckBox) v.findViewById(R.id.subCheckBox);
            subCheckBox.setChecked(!isChecked);
        }
    }

    public void startManageSimMessages(int position) {
        Intent it = new Intent();
        it.setClass(this, ManageSimMessages.class);
        it.putExtra(PhoneConstants.SUBSCRIPTION_KEY, mSubInfoList.get(position).getSubscriptionId());
        startActivity(it);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // The user clicked on the Messaging icon in the action bar. Take them back from
                // wherever they came from
                finish();
                return true;
        }
        return false;
    }

    /**
     *  listen SIM hot plug action, finish this activity if SIM count changed.
     */
    private final BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                List<SubInfoRecord> nowSubList = SubscriptionManager
                        .getActiveSubInfoList();
                if (nowSubList == null || nowSubList.size() != mOldSubCount) {
                    Log.d(TAG, "sub count changed");
                    finish();
                }
            }
        }
    };

    private boolean isSubIdInNeededShowArray(int subId) {
        for (int id : mAppointedSubArray) {
            if (subId == id) {
                return true;
            }
        }
        return false;
    }
}
