package com.android.mms.ui;

import java.util.List;

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
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;

import com.android.internal.telephony.PhoneConstants;

import com.android.mms.R;
import com.android.internal.telephony.TelephonyIntents;
 
public class SubSelectActivity extends ListActivity {

    private static String TAG = "SubSelectActivity";
    private List<SubInfoRecord> mSubInfoList;
    private String mPreferenceKey;
    private SubSelectAdapter mAdapter;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mPreferenceKey = getIntent().getStringExtra(MessagingPreferenceActivity.PREFERENCE_KEY);
        Log.d(TAG, "onCreate preference key is: " + mPreferenceKey);
        registerReceiver(mSimStateReceiver, new IntentFilter(TelephonyIntents.ACTION_SIMINFO_UPDATED));
        setAdapter();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mSimStateReceiver);
        super.onDestroy();
    }

    private void setAdapter(){
        mSubInfoList = SubscriptionManager.getActivatedSubInfoList(this);
        if (mSubInfoList == null || mSubInfoList.size() == 0) {
            finish();
        }
        mAdapter = new SubSelectAdapter(this, mPreferenceKey, mSubInfoList);
        setListAdapter(mAdapter);
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
            editor.putBoolean(Long.toString((mSubInfoList.get(position)).mSubId) + "_"
                    + mPreferenceKey, (!isChecked));
            editor.apply();
            CheckBox subCheckBox = (CheckBox) v.findViewById(R.id.subCheckBox);
            subCheckBox.setChecked(!isChecked);
        }
    }

    public void startManageSimMessages(int position) {
        Intent it = new Intent();
        it.setClass(this, ManageSimMessages.class);
        it.putExtra(PhoneConstants.SUBSCRIPTION_KEY, mSubInfoList.get(position).mSubId);
        startActivity(it);
    }

    /**
     *  listen SIM hot plug action, finish this activity if SIM count changed.
     */
    private final BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIMINFO_UPDATED)) {
                int oldSubCount = mSubInfoList != null ? mSubInfoList.size() : 0;
                List<SubInfoRecord> nowSubList = SubscriptionManager
                        .getActivatedSubInfoList(getApplicationContext());
                if (nowSubList == null || nowSubList.size() != oldSubCount) {
                    Log.d(TAG, "sub count changed");
                    finish();
                }
            }
        }
    };
}
