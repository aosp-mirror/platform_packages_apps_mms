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

import android.R.string;

import java.util.List;

import android.content.Context;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.android.mms.R;

public class SubChooseAdapter extends BaseAdapter {

    Context mContext;
    List<SubInfoRecord> mList;
    public SubChooseAdapter(Context context, List<SubInfoRecord> list) {
        mContext = context;
        mList = list;
    }
    @Override
    public int getCount() {
        return mList.size();
    }

    @Override
    public Object getItem(int position) {
        return mList.get(position);
    }

    @Override
    public long getItemId(int position) {
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
        SubInfoRecord subRecord = mList.get(position);
        // Set theme of the item is LIGHT
        subView.setThemeType(SubscriptionView.LIGHT_THEME);
        if (subRecord.getSimSlotIndex() == SubscriptionManager.SIM_NOT_INSERTED) {
            subView.setSubName(subRecord.getDisplayName());
            subView.setSubNum(null);
            subView.findViewById(R.id.sub_color).setVisibility(View.GONE);
            subView.setClickable(true);
        } else {
            subView.setClickable(false);
            subView.setSubInfo(subRecord);
            subView.findViewById(R.id.sub_color).setVisibility(View.VISIBLE);
        }
        return subView;
    }

    public void setAdapterData(List<SubInfoRecord> list) {
        mList = list;
    }

}
