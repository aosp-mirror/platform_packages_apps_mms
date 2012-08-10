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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mms.R;
import com.android.mms.data.Contact;

/**
 * This class displays the status for a single recipient of a message.  It is used in
 * the ListView of DeliveryReportActivity.
 */
public class DeliveryReportListItem extends LinearLayout {
    private TextView mRecipientView;
    private TextView mStatusView;
    private TextView mDeliveryDateView;
    private ImageView mIconView;

    DeliveryReportListItem(Context context) {
        super(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mRecipientView = (TextView) findViewById(R.id.recipient);
        mStatusView = (TextView) findViewById(R.id.status);
        mDeliveryDateView = (TextView) findViewById(R.id.delivery_date);
        mIconView = (ImageView) findViewById(R.id.icon);
    }

    public DeliveryReportListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public final void bind(String recipient, String status, String deliveryDate) {
        // Recipient
        if (!TextUtils.isEmpty(recipient)) {
            mRecipientView.setText(Contact.get(recipient, false).getName());
        } else {
            mRecipientView.setText("");
        }
        // Status text
        mStatusView.setText(status);

        // Status icon
        Context context = getContext();
        String receivedStr = context.getString(R.string.status_received);
        String failedStr = context.getString(R.string.status_failed);
        String pendingStr = context.getString(R.string.status_pending);
        String rejectStr = context.getString(R.string.status_rejected);

        if (status.compareTo(receivedStr) == 0) {
            mIconView.setImageResource(R.drawable.ic_sms_mms_delivered);
        } else if (status.compareTo(failedStr) == 0) {
            mIconView.setImageResource(R.drawable.ic_sms_mms_not_delivered);
        } else if (status.compareTo(pendingStr) == 0) {
            mIconView.setImageResource(R.drawable.ic_sms_mms_pending);
        } else if (status.compareTo(rejectStr) == 0) {
            // FIXME: need replace ic_sms_mms_not_delivered by a rejected icon.
            mIconView.setImageResource(R.drawable.ic_sms_mms_not_delivered);
        } else {
            // No status report or unknown
        }

        if (TextUtils.isEmpty(deliveryDate)) {
            mDeliveryDateView.setVisibility(View.GONE);
        } else {
            mDeliveryDateView.setVisibility(View.VISIBLE);
            mDeliveryDateView.setText(deliveryDate);
        }
    }
}
