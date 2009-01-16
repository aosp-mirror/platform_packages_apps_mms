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

import com.android.mms.R;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * An adapter to store icons and strings for attachment type list.
 */
public class AttachmentTypeSelectorAdapter extends IconListAdapter {
    public final static int MODE_WITH_SLIDESHOW    = 0;
    public final static int MODE_WITHOUT_SLIDESHOW = 1;

    public final static int ADD_IMAGE               = 0;
    public final static int TAKE_PICTURE            = 1;
    public final static int ADD_VIDEO               = 2;
    public final static int RECORD_VIDEO            = 3;
    public final static int ADD_SOUND               = 4;
    public final static int RECORD_SOUND            = 5;
    public final static int ADD_SLIDESHOW           = 6;

    public AttachmentTypeSelectorAdapter(Context context, int mode) {
        super(context, getData(mode, context));
    }

    protected static List<IconListItem> getData(int mode, Context context) {
        List<IconListItem> data = new ArrayList<IconListItem>(7);
        addItem(data, context.getString(R.string.attach_image),
                R.drawable.ic_launcher_gallery);

        addItem(data, context.getString(R.string.attach_take_photo),
                R.drawable.ic_launcher_camera);

        addItem(data, context.getString(R.string.attach_video),
                R.drawable.ic_launcher_video_player);

        addItem(data, context.getString(R.string.attach_record_video),
                R.drawable.ic_launcher_camera_record);

        addItem(data, context.getString(R.string.attach_sound),
                R.drawable.ic_launcher_musicplayer_2);

        addItem(data, context.getString(R.string.attach_record_sound),
                R.drawable.ic_launcher_record_audio);

        if (mode == MODE_WITH_SLIDESHOW) {
            addItem(data, context.getString(R.string.attach_slideshow),
                    R.drawable.ic_launcher_slideshow_add_sms);
        }

        return data;
    }

    protected static void addItem(List<IconListItem> data, String title, int resource) {
        IconListItem temp = new IconListItem(title, resource);
        data.add(temp);
    }
}
