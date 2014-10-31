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
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.style.ReplacementSpan;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.util.Log;

/**
 * combine ImageSpan and BackgroundColorSpan,
 * make this a ReplacementSpan as to make do not break the text and NOT effect by the bidi algorithmss
 */
public class BackgroundImageSpan extends ReplacementSpan implements ParcelableSpan {
    private static final String TAG = "BackgroundImageSpan";
    private Bitmap mBitmap;
    private Drawable mDrawable;
    private int mWidth = -1;

    /**
     * new BackgroundImageSpan use resource id and Drawable
     * @param id the drawable resource id
     * @param drawable Drawable related to the id
     * @internal
     * @hide
     */
    public BackgroundImageSpan(Drawable drawable) {
        mDrawable = drawable;
    }

    /**
     * @hide
     * @internal
     */
    public BackgroundImageSpan(Parcel src) {
        mBitmap = Bitmap.CREATOR.createFromParcel(src);
    }

    /**
     * @hide
     * @internal
     */
    public void draw(Canvas canvas, int width,float x,int top, int y, int bottom, Paint paint) {
        if (mDrawable == null) {//if no backgroundImage just don't do any draw
            Log.e(TAG, "mDrawable is null draw()");
            return;
        }
        Drawable drawable = mDrawable;
        canvas.save();

        canvas.translate(x, top); // translate to the left top point
        mDrawable.setBounds(0, 0, width, (bottom - top));
        drawable.draw(canvas);
        canvas.restore();
    }

    @Override
    public void updateDrawState(TextPaint tp) {
    }

    /**
     * return a special type identifier for this span class
     * @hide
     * @internal
     * @Override
     */
    public int getSpanTypeId() {
        return 0;
    }

    /**
     * describe the kinds of special objects contained in this Parcelable's marshalled representation
     * @hide
     * @internal
     * @Override
     */
    public int describeContents() {
        return 0;
    }

    /**
     * flatten this object in to a Parcel
     * @hide
     * @internal
     * @Override
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mBitmap, flags);
    }

    /**
     * @hide
     * @internal
     */
    public void convertToDrawable(Context context) {
        if (mDrawable == null) {
            mDrawable = new BitmapDrawable(context.getResources(), mBitmap);
        }
    }

    /**
     * convert a style text that contain BackgroundImageSpan, Parcek only pass resource id,
     * after Parcel, we need to convert resource id to Drawable.
     * @hide
     * @internal
     */
    public static void convert(CharSequence text , Context context) {
        if (!(text instanceof SpannableStringBuilder)) {
            return;
        }
        SpannableStringBuilder builder = (SpannableStringBuilder)text;
        BackgroundImageSpan[] spans = builder.getSpans(0, text.length(), BackgroundImageSpan.class);
        if (spans == null || spans.length == 0) {
            return;
        }
        for (int i = 0; i < spans.length; i++) {
            spans[i].convertToDrawable(context);
        }
    }

    /**
     * draw the span
     * @hide
     * @internal
     * @Override
     */
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        // draw image
        draw(canvas, mWidth,x,top, y, bottom, paint);
        // draw text
        // the paint is already updated
        canvas.drawText(text,start,end, x,y, paint);
    }

    /**
     * get size of the span
     * @hide
     * @internal
     * @Override
     */
    public int getSize(Paint paint, CharSequence text, int start, int end,
            FontMetricsInt fm) {
        float size = paint.measureText(text, start, end);
        if (fm != null && paint != null) {
            paint.getFontMetricsInt(fm);
        }
        mWidth = (int)size;
        return mWidth;
    }
}
