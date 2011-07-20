/*
 * Copyright (C) 2011 Google Inc.
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.ImageView;

public class ImageViewDivot extends ImageView {
    private Paint mPaint = new Paint();
    private Path mPath = new Path();
    private int mPosition;
    private float mDensity;

    private static final float CORNER_OFFSET = 12;
    private static final float EDGE_LENGTH = 10;

    public static final int LEFT_UPPER = 1;
    public static final int LEFT_MIDDLE = 2;
    public static final int LEFT_LOWER = 3;

    public static final int RIGHT_UPPER = 4;
    public static final int RIGHT_MIDDLE = 5;
    public static final int RIGHT_LOWER = 6;

    public static final int TOP_LEFT = 7;
    public static final int TOP_MIDDLE = 8;
    public static final int TOP_RIGHT = 9;

    public static final int BOTTOM_LEFT = 10;
    public static final int BOTTOM_MIDDLE = 11;
    public static final int BOTTOM_RIGHT = 12;

    private static final String [] sPositionChoices = new String [] {
        "",
        "left_upper",
        "left_middle",
        "left_lower",

        "right_upper",
        "right_middle",
        "right_lower",

        "top_left",
        "top_middle",
        "top_right",

        "bottom_left",
        "bottom_middle",
        "bottom_right",
    };

    public ImageViewDivot(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize(attrs);
    }

    public ImageViewDivot(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(attrs);
    }

    public ImageViewDivot(Context context) {
        super(context);
        initialize(null);
    }

    private void initialize(AttributeSet attrs) {
        if (attrs != null) {
            mPosition = attrs.getAttributeListValue(null, "position", sPositionChoices, -1);
        }

        mDensity = getContext().getResources().getDisplayMetrics().density;
        mPaint.setColor(0xffffffff);
        mPaint.setAntiAlias(true);
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        computePath();
        c.drawPath(mPath, mPaint);
    }

    public void setPosition(int position) {
        mPosition = position;
        invalidate();
    }

    private void computePath() {
        mPath.reset();
        final float edgeLength = EDGE_LENGTH * mDensity;

        final float left = 0 - 1F;
        final float right = (getRight() - getLeft()) + 1F;
        final float top = 0 - 1F;
        final float bottom = (getBottom() - getTop()) + 1F;
        final float middle = (getRight() - getLeft()) / 2F;

        final float cornerOffset = CORNER_OFFSET * mDensity;

        switch (mPosition) {
            case LEFT_UPPER: {
                mPath.moveTo(left, top + cornerOffset);
                mPath.lineTo(left + edgeLength, top + cornerOffset + edgeLength);
                mPath.lineTo(left, top + cornerOffset + (2 * edgeLength));

                break;
            }
            case RIGHT_UPPER: {
                mPath.moveTo(right, top + cornerOffset);
                mPath.lineTo(right - edgeLength, top + cornerOffset + edgeLength);
                mPath.lineTo(right, top + cornerOffset + (2 * edgeLength));

                break;
            }
            case BOTTOM_MIDDLE: {
                mPath.moveTo(middle - edgeLength, bottom);
                mPath.lineTo(middle, bottom - edgeLength);
                mPath.lineTo(middle + edgeLength, bottom);
                break;
            }
        }
        mPath.close();
    }

}
