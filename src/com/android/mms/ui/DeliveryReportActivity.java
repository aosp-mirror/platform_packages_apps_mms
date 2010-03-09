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
import com.google.android.mms.pdu.PduHeaders;
import android.database.sqlite.SqliteWrapper;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is the UI for displaying a delivery report:
 *
 * This activity can handle the following parameters from the intent
 * by which it is launched:
 *
 * thread_id long The id of the conversation from which to get the recipients
 *      for the report.
 * message_id long The id of the message about which a report should be displayed.
 * message_type String The type of message (Sms or Mms).  This is used in
 *      conjunction with the message id to retrive the particular message that
 *      the report will be about.
 */
public class DeliveryReportActivity extends ListActivity {
    private static final String LOG_TAG = "DeliveryReportActivity";

    static final String[] MMS_REPORT_REQUEST_PROJECTION = new String[] {
        Mms.Addr.ADDRESS,       //0
        Mms.DELIVERY_REPORT,    //1
        Mms.READ_REPORT         //2
    };

    static final String[] MMS_REPORT_STATUS_PROJECTION = new String[] {
        Mms.Addr.ADDRESS,       //0
        "delivery_status",      //1
        "read_status"           //2
    };

    static final String[] SMS_REPORT_STATUS_PROJECTION = new String[] {
        Sms.ADDRESS,            //0
        Sms.STATUS              //1
    };

    // These indices must sync up with the projections above.
    static final int COLUMN_RECIPIENT           = 0;
    static final int COLUMN_DELIVERY_REPORT     = 1;
    static final int COLUMN_READ_REPORT         = 2;
    static final int COLUMN_DELIVERY_STATUS     = 1;
    static final int COLUMN_READ_STATUS         = 2;

    private long mMessageId;
    private String mMessageType;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.delivery_report_activity);

        Intent intent = getIntent();
        mMessageId = getMessageId(icicle, intent);
        mMessageType = getMessageType(icicle, intent);

        initListView();
        initListAdapter();
    }

    private void initListView() {
        // Add the header for the list view.
        LayoutInflater inflater = getLayoutInflater();
        View header = inflater.inflate(R.layout.delivery_report_header, null);
        getListView().addHeaderView(header, null, true);
    }

    private void initListAdapter() {
        List<DeliveryReportItem> items = getReportItems();
        if (items == null) {
            items = new ArrayList<DeliveryReportItem>(1);
            items.add(new DeliveryReportItem("", getString(R.string.status_none)));
            Log.w(LOG_TAG, "cursor == null");
        }
        setListAdapter(new DeliveryReportAdapter(this, items));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDeliveryReport();
    }

    private void refreshDeliveryReport() {
        ListView list = getListView();
        list.invalidateViews();
        list.requestFocus();
    }

    private long getMessageId(Bundle icicle, Intent intent) {
        long msgId = 0L;

        if (icicle != null) {
            msgId = icicle.getLong("message_id");
        }

        if (msgId == 0L) {
            msgId = intent.getLongExtra("message_id", 0L);
        }

        return msgId;
    }

    private String getMessageType(Bundle icicle, Intent intent) {
        String msgType = null;

        if (icicle != null) {
            msgType = icicle.getString("message_type");
        }

        if (msgType == null) {
            msgType = intent.getStringExtra("message_type");
        }

        return msgType;
    }

    private List<DeliveryReportItem> getReportItems() {
        if (mMessageType.equals("sms")) {
            return getSmsReportItems();
        } else {
            return getMmsReportItems();
        }
    }

    private List<DeliveryReportItem> getSmsReportItems() {
        String selection = "_id = " + mMessageId;
        Cursor c = SqliteWrapper.query(this, getContentResolver(), Sms.CONTENT_URI,
                              SMS_REPORT_STATUS_PROJECTION, selection, null, null);
        if (c == null) {
            return null;
        }

        try {
            if (c.getCount() <= 0) {
                return null;
            }

            List<DeliveryReportItem> items = new ArrayList<DeliveryReportItem>();
            while (c.moveToNext()) {
                items.add(new DeliveryReportItem(
                                getString(R.string.recipient_label) + c.getString(COLUMN_RECIPIENT),
                                getString(R.string.status_label) + 
                                    getSmsStatusText(c.getInt(COLUMN_DELIVERY_STATUS))));
            }
            return items;
        } finally {
            c.close();
        }
    }

    private String getMmsReportStatusText(
            MmsReportRequest request,
            Map<String, MmsReportStatus> reportStatus) {
        if (reportStatus == null) {
            // haven't received any reports.
            return getString(R.string.status_pending);
        }

        String recipient = request.getRecipient();
        recipient = (Mms.isEmailAddress(recipient))?
                Mms.extractAddrSpec(recipient): PhoneNumberUtils.stripSeparators(recipient);
        MmsReportStatus status = queryStatusByRecipient(reportStatus, recipient);
        if (status == null) {
            // haven't received any reports.
            return getString(R.string.status_pending);
        }

        if (request.isReadReportRequested()) {
            if (status.readStatus != 0) {
                switch (status.readStatus) {
                    case PduHeaders.READ_STATUS_READ:
                        return getString(R.string.status_read);
                    case PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ:
                        return getString(R.string.status_unread);
                }
            }
        }

        switch (status.deliveryStatus) {
            case 0: // No delivery report received so far.
                return getString(R.string.status_pending);
            case PduHeaders.STATUS_FORWARDED:
            case PduHeaders.STATUS_RETRIEVED:
                return getString(R.string.status_received);
            case PduHeaders.STATUS_REJECTED:
                return getString(R.string.status_rejected);
            default:
                return getString(R.string.status_failed);
        }
    }

    private static MmsReportStatus queryStatusByRecipient(
            Map<String, MmsReportStatus> status, String recipient) {
        Set<String> recipientSet = status.keySet();
        Iterator<String> iterator = recipientSet.iterator();
        while (iterator.hasNext()) {
            String r = iterator.next();
            if (Mms.isEmailAddress(recipient)) {
                if (TextUtils.equals(r, recipient)) {
                    return status.get(r);
                }
            }
            else if (PhoneNumberUtils.compare(r, recipient)) {
                return status.get(r);
            }
        }
        return null;
    }

    private List<DeliveryReportItem> getMmsReportItems() {
        List<MmsReportRequest> reportReqs = getMmsReportRequests();
        if (null == reportReqs) {
            return null;
        }

        if (reportReqs.size() == 0) {
            return null;
        }

        Map<String, MmsReportStatus> reportStatus = getMmsReportStatus();
        List<DeliveryReportItem> items = new ArrayList<DeliveryReportItem>();
        for (MmsReportRequest reportReq : reportReqs) {
            String statusText = getString(R.string.status_label) + 
                getMmsReportStatusText(reportReq, reportStatus);
            items.add(new DeliveryReportItem(getString(R.string.recipient_label) + 
                    reportReq.getRecipient(), statusText));
        }
        return items;
    }

    private Map<String, MmsReportStatus> getMmsReportStatus() {
        Uri uri = Uri.withAppendedPath(Mms.REPORT_STATUS_URI,
                                       String.valueOf(mMessageId));
        Cursor c = SqliteWrapper.query(this, getContentResolver(), uri,
                       MMS_REPORT_STATUS_PROJECTION, null, null, null);

        if (c == null) {
            return null;
        }

        try {
            Map<String, MmsReportStatus> statusMap =
                    new HashMap<String, MmsReportStatus>();

            while (c.moveToNext()) {
                String recipient = c.getString(COLUMN_RECIPIENT);
                recipient = (Mms.isEmailAddress(recipient))?
                                        Mms.extractAddrSpec(recipient):
                                            PhoneNumberUtils.stripSeparators(recipient);
                MmsReportStatus status = new MmsReportStatus(
                                        c.getInt(COLUMN_DELIVERY_STATUS),
                                        c.getInt(COLUMN_READ_STATUS));
                statusMap.put(recipient, status);
            }
            return statusMap;
        } finally {
            c.close();
        }
    }

    private List<MmsReportRequest> getMmsReportRequests() {
        Uri uri = Uri.withAppendedPath(Mms.REPORT_REQUEST_URI,
                                       String.valueOf(mMessageId));
        Cursor c = SqliteWrapper.query(this, getContentResolver(), uri,
                      MMS_REPORT_REQUEST_PROJECTION, null, null, null);

        if (c == null) {
            return null;
        }

        try {
            if (c.getCount() <= 0) {
                return null;
            }

            List<MmsReportRequest> reqList = new ArrayList<MmsReportRequest>();
            while (c.moveToNext()) {
                reqList.add(new MmsReportRequest(
                                c.getString(COLUMN_RECIPIENT),
                                c.getInt(COLUMN_DELIVERY_REPORT),
                                c.getInt(COLUMN_READ_REPORT)));
            }
            return reqList;
        } finally {
            c.close();
        }
    }

    private String getSmsStatusText(int status) {
        if (status == Sms.STATUS_NONE) {
            // No delivery report requested
            return getString(R.string.status_none);
        } else if (status >= Sms.STATUS_FAILED) {
            // Failure
            return getString(R.string.status_failed);
        } else if (status >= Sms.STATUS_PENDING) {
            // Pending
            return getString(R.string.status_pending);
        } else {
            // Success
            return getString(R.string.status_received);
        }
    }

    private static final class MmsReportStatus {
        final int deliveryStatus;
        final int readStatus;

        public MmsReportStatus(int drStatus, int rrStatus) {
            deliveryStatus = drStatus;
            readStatus = rrStatus;
        }
    }

    private static final class MmsReportRequest {
        private final String mRecipient;
        private final boolean mIsDeliveryReportRequsted;
        private final boolean mIsReadReportRequested;

        public MmsReportRequest(String recipient, int drValue, int rrValue) {
            mRecipient = recipient;
            mIsDeliveryReportRequsted = drValue == PduHeaders.VALUE_YES;
            mIsReadReportRequested = rrValue == PduHeaders.VALUE_YES;
        }

        public String getRecipient() {
            return mRecipient;
        }

        public boolean isDeliveryReportRequested() {
            return mIsDeliveryReportRequsted;
        }

        public boolean isReadReportRequested() {
            return mIsReadReportRequested;
        }
    }
}
