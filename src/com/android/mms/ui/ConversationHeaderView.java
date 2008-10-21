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
import android.graphics.Typeface;
import android.provider.Telephony.Mms;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.HideReturnsTransformationMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * This class manages the view for given conversation.
 */
public class ConversationHeaderView extends RelativeLayout {
    private ConversationHeader mConversationHeader;
    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDateView;
    private View mAttachmentView;
    private View mUnreadIndicator;
    private View mErrorIndicator;

    public ConversationHeaderView(Context context) {
        super(context);
    }

    public ConversationHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFromView = (TextView) findViewById(R.id.from);
        mSubjectView = (TextView) findViewById(R.id.subject);
        mDateView = (TextView) findViewById(R.id.date);
        mAttachmentView = findViewById(R.id.attachment);
        mUnreadIndicator = findViewById(R.id.unread_indicator);
        mErrorIndicator = findViewById(R.id.error);
    }

    public ConversationHeader getConversationHeader() {
        return mConversationHeader;
    }

    /**
     * Only used for header binding.
     */
    public void bind(String title, String explain) {
        mFromView.setText(title);
        mSubjectView.setText(explain);
    }

    private CharSequence formatMessage(ConversationHeader ch) {
        final int size = android.R.style.TextAppearance_Small;
        final int color = android.R.styleable.Theme_textColorSecondary;
        CharSequence from = (Mms.getDisplayAddress(mContext, ch.getFrom())).replace(';', ',');
        SpannableStringBuilder buf = new SpannableStringBuilder(from);

        if (ch.getMessageCount() > 1) {
            buf.append(" (" + ch.getMessageCount() + ") ");
        }

        int before = buf.length();
        if (ch.hasDraft()) {
            buf.append(" ");
            buf.append(mContext.getResources().getString(R.string.has_draft));
            buf.setSpan(new TextAppearanceSpan(mContext, size, color), before,
                    buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            buf.setSpan(new ForegroundColorSpan(
                    mContext.getResources().getColor(R.drawable.text_color_red)),
                    before, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return buf;
    }

    public final void bind(Context context, ConversationHeader ch) {
        mConversationHeader = ch;

        if (ch.hasAttachment()) {
            mAttachmentView.setVisibility(VISIBLE);
        }

        boolean isRead = ch.isRead();
        Typeface typeFace = isRead
                ? Typeface.DEFAULT
                : Typeface.DEFAULT_BOLD;

        // Date
        mDateView.setText(ch.getDate());
        mDateView.setTypeface(typeFace);

        // From
        mFromView.setText(formatMessage(ch));
        mFromView.setTypeface(typeFace);

        // Subject
        mSubjectView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());

        if (ch.getSubject() != null) {
            mSubjectView.setText(ch.getSubject());
        } else {
            mSubjectView.setText("");
        }

        // Unread
        if (isRead) {
            mUnreadIndicator.setVisibility(View.INVISIBLE);
        } else {
            mUnreadIndicator.setVisibility(View.VISIBLE);
        }

        // Transmission error indicator.
        if (ch.hasError()) {
            mErrorIndicator.setVisibility(View.VISIBLE);
        } else {
            mErrorIndicator.setVisibility(View.INVISIBLE);
        }
    }
}
