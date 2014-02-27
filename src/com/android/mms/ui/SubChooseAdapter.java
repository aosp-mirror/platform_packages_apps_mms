package com.android.mms.ui;

import android.R.string;

import java.util.List;

import com.android.internal.widget.SubscriptionView;

import android.content.Context;
import android.telephony.SubscriptionController.SubInfoRecord;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class SubChooseAdapter extends BaseAdapter {

    public static class SubInfo {
        public SubInfoRecord mSubInfoRecord;
        public String mDisplay;
    }

    Context mContext;
    List<SubInfo> mList;

    public SubChooseAdapter(Context context, List<SubInfo> list) {
        mContext = context;
        mList = list;
    }
    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return mList.size();
    }

    @Override
    public Object getItem(int position) {
        // TODO Auto-generated method stub
        return mList.get(position);
    }

    @Override
    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SubscriptionView subView;
        if (convertView == null) {
            subView = new SubscriptionView(mContext);
        } else {
            subView = (SubscriptionView) convertView;
        }
        SubInfo subInfo = mList.get(position);
        SubInfoRecord subRecord = subInfo.mSubInfoRecord;
        //Set SIM color in Dark or Light
        subView.setThemeType(SubscriptionView.LIGHT_THEME);
        if (subRecord == null) {
            subView.setClickable(true);
            subView.setSubName(subInfo.mDisplay);
        } else {
            subView.setClickable(false);
            subView.setSubInfo(subRecord);
        }
        return subView;
    }

    public void setAdapterData(List<SubInfo> list) {
        mList = list;
    }

}
