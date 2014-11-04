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

import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telephony.SubInfoRecord;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import com.android.mms.R;

public class SubSelectAdapter extends BaseAdapter {
    private LayoutInflater mInf;
    private String mPreferenceKey;
    private Context mContext;
    private List<SubInfoRecord> mList;

    public SubSelectAdapter(Context context, String preferenceKey, List<SubInfoRecord> list) {
        mInf = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
        mPreferenceKey = preferenceKey;
        mList = list;
    }


    @Override
    public int getCount() {
        return mList == null ? 0 : mList.size();
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
        View view = mInf.inflate(R.layout.sub_select_item, null);
        SubscriptionView subView;
        if (convertView != null && (convertView instanceof SubscriptionView)) {
            subView = (SubscriptionView) convertView;
        } else {
            subView = (SubscriptionView) view.findViewById(R.id.subItem);
        }
        subView.setThemeType(SubscriptionView.LIGHT_THEME);
        subView.setSubInfo(mList.get(position));
        CheckBox subCheckBox = (CheckBox) view.findViewById(R.id.subCheckBox);
        if (MessagingPreferenceActivity.MANAGE_SIM_MESSAGE_MODE.equals(mPreferenceKey)) {
            subCheckBox.setVisibility(View.GONE);
        } else {
            subCheckBox.setChecked(isChecked(position));
        }
        return view;
    }

    /**
     * get the related preference data by position to find whether
     * @param position
     * @return whether has checked
     */
    public boolean isChecked(int position) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getBoolean(Integer.toString(mList.get(position).getSubscriptionId())
                + "_" + mPreferenceKey, false);
    }

    /**
     * set the mPreferenceKey
     *
     * @param preferenceKey
     */
    public void setPreferenceKey(String preferenceKey) {
        mPreferenceKey = preferenceKey;
    }
}
