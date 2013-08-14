/**
 * Copyright (C) 2013 Intel Corporation, All Rights Reserved
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

import com.android.mms.R;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

/**
 * Class that represents an adapter for ListView , implements methods of
 * abstract class BaseAdapter and contains a holder class for TextView and
 * CheckBox
 */

public class ArrayOptions extends BaseAdapter {

    private Context mContext;
    private ArrayList<ListItemComponents> mItems;

    public ArrayOptions(Context context, ArrayList<ListItemComponents> items) {
        mContext = context;
        mItems = items;
    }

    /**
     * Method that returns item number from ListView
     * @return number of items
     */
    @Override
    public int getCount() {
        return mItems != null ? mItems.size() : 0;
    }

    /**
     * Method that returns the item at <position>
     * @param position
     * @return ListItemComponents
     */
    @Override
    public ListItemComponents getItem(int position) {
        return mItems != null ? mItems.get(position) : null;
    }

    /**
     * Method that returns the item id at <position>
     * @param position
     * @return item
     */
    @Override
    public long getItemId(int position) {
        return mItems != null ? mItems.get(position).getId() : -1;
    }

    /**
     * Holder that contains CheckBox and TextView
     */
    class Holder {
        CheckBox cb;
        TextView tv;
    }

    /**
     * Method that initializes and changes view at scroll
     * @param position view parent
     * @return current view of listview
     */
    @Override
    public View getView(int position, View view, ViewGroup parent) {
        Holder holder = null;
        if (view == null) {
            view = View.inflate(mContext, R.layout.listview_item, null);
            holder = new Holder();
            holder.cb = (CheckBox) view.findViewById(R.id.checkBox);
            holder.tv = (TextView) view.findViewById(R.id.value);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }
        ListItemComponents item = mItems.get(position);
        holder.tv.setText(item.getName());
        holder.cb.setChecked(item.isChecked());
        return view;
    }

}
