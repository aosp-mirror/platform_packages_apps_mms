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
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

public class MSubBackgroundImageSpan extends ReplacementSpan implements ParcelableSpan {

    private BackgroundImageSpan mBackgroundImageSpan;


    public MSubBackgroundImageSpan(int id, Drawable drawable) {
        mBackgroundImageSpan = new BackgroundImageSpan(id, drawable);
    }

    public MSubBackgroundImageSpan(Parcel src) {
        mBackgroundImageSpan = new BackgroundImageSpan(src);
    }

    public void draw(Canvas canvas, int width,float x,int top, int y, int bottom, Paint paint) {
        mBackgroundImageSpan.draw(canvas, width, x, top, y, bottom, paint);
    }

    public void updateDrawState(TextPaint tp) {
    }

    public int getSpanTypeId() {
        return mBackgroundImageSpan.getSpanTypeId();
    }

    public int describeContents() {
        return mBackgroundImageSpan.describeContents();
    }

    public void writeToParcel(Parcel dest, int flags) {
        mBackgroundImageSpan.writeToParcel(dest, flags);

    }

    public void convertToDrawable(Context context) {
        mBackgroundImageSpan.convertToDrawable(context);
    }

    /**
     * M: convert a style text that contain BackgroundImageSpan, Parcek only pass resource id,
     * after Parcel, we need to convert resource id to Drawable.
     */
    public static void convert(CharSequence text , Context context) {
        BackgroundImageSpan.convert(text, context);
    }

    public void draw(Canvas canvas, CharSequence text, int start, int end,
            float x, int top, int y, int bottom, Paint paint) {
        mBackgroundImageSpan.draw(canvas, text, start, end, x, top, y, bottom, paint);
    }

    public int getSize(Paint paint, CharSequence text, int start, int end,
            FontMetricsInt fm) {
        return mBackgroundImageSpan.getSize(paint, text, start, end, fm);
    }
}
