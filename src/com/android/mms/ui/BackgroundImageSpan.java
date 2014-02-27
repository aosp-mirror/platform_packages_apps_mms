/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.android.mms.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.style.ReplacementSpan;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;

/**
 * combine ImageSpan and BackgroundColorSpan,
 * make this a ReplacementSpan as to make do not break the text and NOT effect by the bidi algorithmss
 */
public class BackgroundImageSpan extends ReplacementSpan implements ParcelableSpan {
    private Drawable mDrawable;
    private int mImageId;
    private int mWidth = -1;

    /**
     * new BackgroundImageSpan use resource id and Drawable
     * @param id the drawable resource id
     * @param drawable Drawable related to the id
     * @internal
     * @hide
     */
    public BackgroundImageSpan(int id, Drawable drawable) {
        mImageId = id;
        mDrawable = drawable;
    }

    /**
     * @hide
     * @internal
     */
    public BackgroundImageSpan(Parcel src) {
        mImageId = src.readInt();
    }

    /**
     * @hide
     * @internal
     */
    public void draw(Canvas canvas, int width,float x,int top, int y, int bottom, Paint paint) {
        if (mDrawable == null) {//if no backgroundImage just don't do any draw
            throw new IllegalStateException("should call convertToDrawable() first");
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
        dest.writeInt(mImageId);
    }

    /**
     * @hide
     * @internal
     */
    public void convertToDrawable(Context context) {
        if (mDrawable == null) {
            mDrawable = context.getResources().getDrawable(mImageId);
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
