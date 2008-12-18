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

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import com.android.mms.R;

/**
 * An adapter to store icons and strings for layout selector list.
 */
public class LayoutSelectorAdapter extends IconListAdapter {
    public LayoutSelectorAdapter(Context context) {
        super(context, getData(context));
    }

    protected static List<IconListItem> getData(Context context) {
        List<IconListItem> data = new ArrayList<IconListItem>(2);
         addItem(data, context.getString(R.string.select_top_text),
                R.drawable.ic_mms_text_top);
         addItem(data, context.getString(R.string.select_bottom_text),
                R.drawable.ic_mms_text_bottom);

        return data;
    }

    protected static void addItem(List<IconListItem> data, String title, int resource) {
        IconListItem temp = new IconListItem(title, resource);
        data.add(temp);
    }
}
