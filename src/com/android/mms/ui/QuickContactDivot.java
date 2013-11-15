/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.QuickContactBadge;

import com.android.mms.R;
import com.android.mms.util.BrcmDualSimUtils;

public class QuickContactDivot extends QuickContactBadge implements Divot{
    private Drawable mDrawable;
    private int mDrawableIntrinsicWidth;
    private int mDrawableIntrinsicHeight;
    private int mPosition;

    // The screen density.  Multiple this by dips to get pixels.
    private float mDensity;

    private int mSimId;
    private Drawable mSimIcon;

    public QuickContactDivot(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize(attrs);
    }

    public QuickContactDivot(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(attrs);
    }

    public QuickContactDivot(Context context) {
        super(context);
        initialize(null);
    }

    private void initialize(AttributeSet attrs) {
        if (attrs != null) {
            mPosition = attrs.getAttributeListValue(null, "position", sPositionChoices, -1);
        }

        Resources r = getContext().getResources();
        mDensity = r.getDisplayMetrics().density;

        setDrawable();
    }

    private void setDrawable() {
        Resources r = getContext().getResources();

        switch (mPosition) {
            case LEFT_UPPER:
            case LEFT_MIDDLE:
            case LEFT_LOWER:
                mDrawable = r.getDrawable(R.drawable.msg_bubble_right);
                break;

            case RIGHT_UPPER:
            case RIGHT_MIDDLE:
            case RIGHT_LOWER:
                mDrawable = r.getDrawable(R.drawable.msg_bubble_left);
                break;

//            case TOP_LEFT:
//            case TOP_MIDDLE:
//            case TOP_RIGHT:
//                mDrawable = r.getDrawable(R.drawable.msg_bubble_bottom);
//                break;
//
//            case BOTTOM_LEFT:
//            case BOTTOM_MIDDLE:
//            case BOTTOM_RIGHT:
//                mDrawable = r.getDrawable(R.drawable.msg_bubble_top);
//                break;
        }
        mDrawableIntrinsicWidth = mDrawable.getIntrinsicWidth();
        mDrawableIntrinsicHeight = mDrawable.getIntrinsicHeight();
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        c.save();
        computeBounds(c);
        mDrawable.draw(c);
        drawSimIcon(c);
        c.restore();
    }

    public void setPosition(int position) {
        mPosition = position;
        setDrawable();
        invalidate();
    }

    public int getPosition() {
        return mPosition;
    }

    public float getCloseOffset() {
        return CORNER_OFFSET * mDensity;  // multiply by density to get pixels
    }

    public ImageView asImageView() {
        return this;
    }

    public void assignContactFromEmail(String emailAddress) {
        assignContactFromEmail(emailAddress, true);
    }

    public float getFarOffset() {
        return getCloseOffset() + mDrawableIntrinsicHeight;
    }

    private void computeBounds(Canvas c) {
        final int left = 0;
        final int top = 0;
        final int right = getWidth();
        final int middle = right / 2;
        final int bottom = getHeight();

        final int cornerOffset = (int) getCloseOffset();

        switch (mPosition) {
            case RIGHT_UPPER:
                mDrawable.setBounds(
                        right - mDrawableIntrinsicWidth,
                        top + cornerOffset,
                        right,
                        top + cornerOffset + mDrawableIntrinsicHeight);
                break;

            case LEFT_UPPER:
                mDrawable.setBounds(
                        left,
                        top + cornerOffset,
                        left + mDrawableIntrinsicWidth,
                        top + cornerOffset + mDrawableIntrinsicHeight);
                break;

            case BOTTOM_MIDDLE:
                int halfWidth = mDrawableIntrinsicWidth / 2;
                mDrawable.setBounds(
                        (int)(middle - halfWidth),
                        (int)(bottom - mDrawableIntrinsicHeight),
                        (int)(middle + halfWidth),
                        (int)(bottom));

                break;
        }
    }

    public void setSimIcon(int simPos)
    {
        if ( !BrcmDualSimUtils.isSupportDualSim() ) {
            return;
        }

        Resources r = getContext().getResources();
        Drawable drawIcon = null;
        if (simPos == 0) {
            drawIcon = r.getDrawable(R.drawable.ic_brcm_sim_1);
        }
        else if (simPos == 1) {
            drawIcon = r.getDrawable(R.drawable.ic_brcm_sim_2);
        }
        else {
            drawIcon = null;
        }
        if(mSimIcon != drawIcon) {
            mSimIcon = drawIcon;
            invalidate();
        }
    }
    private void drawSimIcon(Canvas c) {
        if ( !BrcmDualSimUtils.isSupportDualSim() ) {
            return;
        }

        if(mSimIcon == null) {
            return;
        }
        final int nHeight = mSimIcon.getIntrinsicHeight();
        final int nWidth = mSimIcon.getIntrinsicWidth();
        final int left = 0;
        final int bottom = getHeight();
        final int top = bottom - nHeight;
        final int right = nWidth;
        mSimIcon.setBounds(left, top, right, bottom);
        mSimIcon.draw(c);
    }
}
