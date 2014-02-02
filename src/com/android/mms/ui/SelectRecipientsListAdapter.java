/*
* Copyright (C) 2013 The CyanogenMod Project
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

package com.android.mms.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.SectionIndexer;

import com.android.mms.R;
import com.android.mms.data.Group;
import com.android.mms.data.PhoneNumber;
import com.android.mms.data.RecipientsListLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SelectRecipientsListAdapter extends ArrayAdapter<RecipientsListLoader.Result>
        implements SectionIndexer, AbsListView.RecyclerListener {
    private final LayoutInflater mInflater;
    private String[] mSections;
    private int[] mPositions;

    public SelectRecipientsListAdapter(Context context,
            List<RecipientsListLoader.Result> items) {
        super(context, R.layout.select_recipients_list_item, items);
        mInflater = LayoutInflater.from(context);

        ArrayList<String> sections = new ArrayList<String>();
        ArrayList<Integer> positions = new ArrayList<Integer>();
        String groupIndex = context.getString(R.string.fastscroll_index_groups);
        String lastSectionIndex = null;
        boolean hasSections = true;

        for (int i = 0; i < items.size(); i++) {
            RecipientsListLoader.Result item = items.get(i);
            String index;

            if (item.phoneNumber == null) {
                index = groupIndex;
            } else {
                index = item.phoneNumber.getSectionIndex();
            }

            if (index == null) {
                hasSections = false;
                break;
            }

            if (!TextUtils.equals(index, lastSectionIndex)) {
                sections.add(index);
                positions.add(i);
                lastSectionIndex = index;
            }
        }

        if (!hasSections) {
            sections.clear();
            sections.add("");
            positions.clear();
            positions.add(0);
        }

        int count = sections.size();
        mSections = new String[count];
        mPositions = new int[count];
        for (int i = 0; i < count; i++) {
            mSections[i] = sections.get(i);
            mPositions[i] = positions.get(i);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup viewGroup) {
        SelectRecipientsListItem view;

        if (convertView == null) {
            view = (SelectRecipientsListItem) mInflater.inflate(
                    R.layout.select_recipients_list_item, viewGroup, false);
        } else {
            view = (SelectRecipientsListItem) convertView;
        }

        bindView(position, view);
        return view;
    }

    @Override
    public void onMovedToScrapHeap(View view) {
        unbindView(view);
    }

    public void unbindView(View view) {
        if (view instanceof SelectRecipientsListItem) {
            SelectRecipientsListItem srli = (SelectRecipientsListItem) view;
            srli.unbind();
        }
    }

    private void bindView(int position, SelectRecipientsListItem view) {
        final RecipientsListLoader.Result item = getItem(position);

        if (item.group == null) {
            PhoneNumber phoneNumber = item.phoneNumber;
            PhoneNumber lastNumber = position != 0
                    ? getItem(position - 1).phoneNumber : null;
            PhoneNumber nextNumber = position != getCount() - 1
                    ? getItem(position + 1).phoneNumber : null;
            long contactId = phoneNumber.getContactId();
            long lastContactId = lastNumber != null ? lastNumber.getContactId() : -1;
            long nextContactId = nextNumber != null ? nextNumber.getContactId() : -1;

            boolean showHeader = Arrays.binarySearch(mPositions, position) >= 0;
            boolean showFooter = contactId != nextContactId;
            boolean isFirst = contactId != lastContactId;

            view.bind(getContext(), phoneNumber, showHeader, showFooter, isFirst);
        } else {
            view.bind(getContext(), item.group, position == 0);
        }
    }

    @Override
    public int getPositionForSection(int section) {
        if (section < 0 || section >= mSections.length) {
            return -1;
        }

        return mPositions[section];
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position < 0 || position >= getCount()) {
            return -1;
        }

        int index = Arrays.binarySearch(mPositions, position);

        /*
* Consider this example: section positions are 0, 3, 5; the supplied
* position is 4. The section corresponding to position 4 starts at
* position 3, so the expected return value is 1. Binary search will not
* find 4 in the array and thus will return -insertPosition-1, i.e. -3.
* To get from that number to the expected value of 1 we need to negate
* and subtract 2.
*/
        return index >= 0 ? index : -index - 2;
    }

    @Override
    public Object[] getSections() {
        return mSections;
    }
}
