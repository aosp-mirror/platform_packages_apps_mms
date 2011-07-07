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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class DivitDrawable extends Drawable {
    private Paint mPaint = new Paint();
    private Path mPath = new Path();
    private int mPosition;
    private float mDensity;

    private static final float CORNER_OFFSET = 10;
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

    public DivitDrawable(int position, float density) {
        mPosition = position;
        mPaint.setColor(0xffffffff);
        mPaint.setAntiAlias(true);
        mDensity = density;
    }

    private void computePath() {
        final float edgeLength = EDGE_LENGTH * mDensity;
        final Rect bounds = getBounds();
        switch (mPosition) {
        case LEFT_UPPER: {
            final float left = -1;
            final float top = CORNER_OFFSET * mDensity;

            mPath.moveTo(left, top);
            mPath.lineTo(edgeLength, top + edgeLength);
            mPath.lineTo(left, top + (2 * edgeLength));

            break;
        }
        case RIGHT_UPPER: {
            final float right = bounds.right + 1;
            final float top = CORNER_OFFSET * mDensity;

            mPath.moveTo(right, top);
            mPath.lineTo(right - edgeLength, top + edgeLength);
            mPath.lineTo(right, top + (2 * edgeLength));

            break;
        }
        case BOTTOM_MIDDLE: {
            final float middle = bounds.exactCenterX();
            final float bottom = bounds.bottom + 1;

            mPath.moveTo(middle - edgeLength, bottom);
            mPath.lineTo(middle, bottom - edgeLength);
            mPath.lineTo(middle + edgeLength, bottom);
            break;
        }
        }
        mPath.close();
    }

    @Override
    public void draw(Canvas canvas) {
        computePath();
        canvas.drawPath(mPath, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        // ignore
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // ignore
    }

    @Override
    public int getOpacity() {
        return 0;
    }
}

