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

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.mms.R;

/**
 * An adapter to store icons.
 */
public class IconListAdapter extends ArrayAdapter<IconListAdapter.IconListItem> {
    protected LayoutInflater mInflater;
    private static final int mResource = R.layout.icon_list_item;
    private ViewHolder mViewHolder;

    static class ViewHolder {
        private View mView;
        private TextView mTextView;
        private ImageView mImageView;

        public ViewHolder(View view) {
            mView = view;
        }

        public TextView getTextView() {
            if (mTextView == null) {
                mTextView = (TextView) mView.findViewById(R.id.text1);
            }

            return mTextView;
        }

        public ImageView getImageView() {
            if (mImageView == null) {
                mImageView = (ImageView) mView.findViewById(R.id.icon);
            }

            return mImageView;
        }
    }
    public IconListAdapter(Context context,
            List<IconListItem> items) {
        super(context, mResource, items);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = mInflater.inflate(mResource, parent, false);
            mViewHolder = new ViewHolder(view);
            view.setTag(mViewHolder);
        } else {
            view = convertView;
            mViewHolder = (ViewHolder) view.getTag();
        }

        // Set text field
        TextView text = mViewHolder.getTextView();
        text.setText(getItem(position).getTitle());

        // Set resource icon
        ImageView image = mViewHolder.getImageView();
        image.setImageResource(getItem(position).getResource());

        return view;
    }

    public static class IconListItem {
        private final String mTitle;
        private final int mResource;

        public IconListItem(String title, int resource) {
            mResource = resource;
            mTitle = title;
        }

        public String getTitle() {
            return mTitle;
        }

        public int getResource() {
            return mResource;
        }
    }
}
