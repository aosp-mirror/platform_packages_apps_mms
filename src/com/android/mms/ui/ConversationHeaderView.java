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
import android.graphics.drawable.Drawable;

import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * This class manages the view for given conversation.
 */
public class ConversationHeaderView extends RelativeLayout {
    private static final String TAG = "ConversationHeaderView";
    private static final boolean DEBUG = false;

    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDateView;
    private View mAttachmentView;
    private View mUnreadIndicator;
    private View mErrorIndicator;
    private ImageView mPresenceView;

    // For posting UI update Runnables from other threads:
    private Handler mHandler = new Handler();

    // Access to mConversationHeader is guarded by mConversationHeaderLock.
    private final Object mConversationHeaderLock = new Object();
    private ConversationHeader mConversationHeader;
    
    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);

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
        mPresenceView = (ImageView) findViewById(R.id.presence);
    }

    public void setPresenceIcon(int iconId) {
        if (iconId == 0) {
            mPresenceView.setVisibility(View.GONE);            
        } else {
            mPresenceView.setImageResource(iconId);
            mPresenceView.setVisibility(View.VISIBLE);
        }
    }

    public ConversationHeader getConversationHeader() {
        synchronized (mConversationHeaderLock) {
            return mConversationHeader;
        }
    }

    private void setConversationHeader(ConversationHeader header) {
        synchronized (mConversationHeaderLock) {
            mConversationHeader = header;
        }
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
        String from = ch.getFrom();
        if (from == null) {
            // The temporary text users see while the names of contacts are loading.
            // TODO: evaluate a better or prettier solution for this?
            from = "...";
        }

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
        
        // Unread messages are shown in bold
        if (!ch.isRead()) {
            buf.setSpan(STYLE_BOLD, 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return buf;
    }

    // Called by another thread that loaded an updated
    // ConversationHeader for us.  Note, however, that this view
    // might've been re-used for a different header in the meantime,
    // so we have to double-check that we still want this header.
    public void onHeaderLoaded(final ConversationHeader newHeader) {
        synchronized (mConversationHeaderLock) {
            if (mConversationHeader != newHeader) {
                // The user scrolled away before the item loaded and
                // this view has been repurposed.
                return;
            }

            // TODO: as an optimization, send a message to mHandler instead
            // of posting a Runnable.
            mHandler.post(new Runnable() {
                    public void run() {
                        synchronized (mConversationHeaderLock) {
                            if (mConversationHeader == newHeader) {
                                mFromView.setText(formatMessage(newHeader));
                                setPresenceIcon(newHeader.getPresenceResourceId());

                            }
                        }
                    }
                });
        }
    }

    public final void bind(Context context, final ConversationHeader ch) {
        if (DEBUG) Log.v(TAG, "bind()");

        ConversationHeader oldHeader = getConversationHeader();
        setConversationHeader(ch);

        LayoutParams attachmentLayout = (LayoutParams)mAttachmentView.getLayoutParams();
        boolean hasError = ch.hasError();
        // When there's an error icon, the attachment icon is left of the error icon.
        // When there is not an error icon, the attachment icon is left of the date text.
        // As far as I know, there's no way to specify that relationship in xml.
        if (hasError) {
            attachmentLayout.addRule(RelativeLayout.LEFT_OF, R.id.error);
        } else {
            attachmentLayout.addRule(RelativeLayout.LEFT_OF, R.id.date);
        }

        boolean hasAttachment = ch.hasAttachment();
        mAttachmentView.setVisibility(hasAttachment ? VISIBLE : GONE);

        // Date
        mDateView.setText(ch.getDate());

        // From.
        mFromView.setText(formatMessage(ch));

        // The From above may be incomplete (still loading), so we register ourselves
        // as a callback later to get woken up in onHeaderLoaded() when it changes.
        if (ch.getFrom() == null) {
            ch.setWaitingView(this);
        }

        mUnreadIndicator.setVisibility(ch.isRead() ? INVISIBLE : VISIBLE);

        // Subject
        mSubjectView.setText(ch.getSubject());
        LayoutParams subjectLayout = (LayoutParams)mSubjectView.getLayoutParams();
        // We have to make the subject left of whatever optional items are shown on the right.
        subjectLayout.addRule(RelativeLayout.LEFT_OF, hasAttachment ? R.id.attachment :
            (hasError ? R.id.error : R.id.date));

        // Transmission error indicator.
        mErrorIndicator.setVisibility(hasError ? VISIBLE : GONE);
    }
}
