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

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.SubInfoRecord;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.mms.R;

public class SubscriptionView extends LinearLayout {

    public static final int DARK_THEME = 0;
    public static final int LIGHT_THEME = 1;
    private static final int MIN_NUM_LENGTH = 4;

    private TextView mSubNameView;
    private TextView mSubNumView;
    private TextView mSubShortNumView;
    private RelativeLayout mSubColorView;
    private int mThemeType = DARK_THEME;
    private int mNumLength = MIN_NUM_LENGTH;

    public SubscriptionView(Context context) {
        this(context,null);
    }

    public SubscriptionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflator.inflate(R.layout.subscription_item_layout, null);
        addView(view);
        initViewElement(view);
    }

    /**
     * Set how many numbers to be shown for short number view, default is MIN_NUM_LENGTH which is 4
     * @param numLength The length of subscription number to be shown
     */
    public void setNumLength(int numLength) {
        mNumLength = numLength;
    }

    /**
     * Theme type used for subscription background color, dark or light, default is in dark theme
     * @param themeType The theme type 0 for dark, 1 for light
     */
    public void setThemeType(int themeType) {
        mThemeType = themeType;
    }

    private void initViewElement(View view) {
        mSubNameView = (TextView) view.findViewById(R.id.sub_name);
        mSubNumView = (TextView) view.findViewById(R.id.sub_number);
        mSubShortNumView = (TextView) view.findViewById(R.id.sub_short_number);
        mSubColorView = (RelativeLayout) view.findViewById(R.id.sub_color);
    }

    /**
     * By passing subscription information into view and set views based on Subscription information.
     * Update Sub name / number / color / shortNumber views
     * @param subInfo The SubInfoRecord of subscription
     */
    public void setSubInfo(SubInfoRecord subInfo) {
        if (subInfo != null) {
            setSubColor(subInfo.simIconRes[mThemeType]);
            setSubName(subInfo.displayName);
            setSubNum(subInfo.number);
            setSubShortNum(subInfo.displayNumberFormat, subInfo.number);
        }
    }

    /**
     * Set Sub color view
     * @param resId The color res Id getting from SubInfoRecord of subscription
     */
    public void setSubColor(int resId) {
        mSubColorView.setBackgroundResource(resId);
    }

    /**
     * Set subscription short number view
     * @param format The format of short number of subscription, first 4 / last 4 / no display
     * @param num The subscription number
     */
    public void setSubShortNum(int format, String num) {
        String formatNum = "";
        if (!TextUtils.isEmpty(num) && format != SubscriptionManager.DISPLAY_NUMBER_NONE) {
            if (num.length() <= mNumLength) {
                formatNum = num;
            } else {
                formatNum = format == SubscriptionManager.DISPLAY_NUMBER_FIRST ?
                                      num.substring(0,mNumLength) :
                                      num.substring(num.length() - mNumLength , num.length());
            }
            mSubShortNumView.setText(formatNum);
        }
        mSubShortNumView.setVisibility(TextUtils.isEmpty(formatNum) ? View.GONE : View.VISIBLE);
    }

    /**
     * Set subscription number view
     * @param num The number of subscription
     */
    public void setSubNum(String num) {
        if (num != null) {
            mSubNumView.setText(num);
        }
        mSubNumView.setVisibility(TextUtils.isEmpty(num) ? View.GONE : View.VISIBLE);
    }

    /**
     * Set subscription name view
     * @param name The name of subscription
     */
    public void setSubName(String name) {
        if (name != null) {
            mSubNameView.setText(name);
        }
        mSubNameView.setVisibility(TextUtils.isEmpty(name) ? View.GONE : View.VISIBLE);
    }
}
