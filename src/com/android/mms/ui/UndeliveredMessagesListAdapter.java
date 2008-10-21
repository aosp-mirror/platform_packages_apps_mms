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

import static com.android.mms.ui.UndeliveredMessagesActivity.COLUMN_ID;
import static com.android.mms.ui.UndeliveredMessagesActivity.COLUMN_MMS_ERROR_TYPE;
import static com.android.mms.ui.UndeliveredMessagesActivity.COLUMN_MMS_MSG_TYPE;
import static com.android.mms.ui.UndeliveredMessagesActivity.COLUMN_MSG_TYPE;
import static com.android.mms.ui.UndeliveredMessagesActivity.COLUMN_SMS_ADDRESS;
import static com.android.mms.ui.UndeliveredMessagesActivity.COLUMN_SMS_BODY;
import static com.android.mms.ui.UndeliveredMessagesActivity.COLUMN_SMS_DATE;
import static com.android.mms.ui.UndeliveredMessagesActivity.COLUMN_SMS_STATUS;

import com.android.mms.R;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendReq;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * The back-end data adapter of undelivered messages list.
 */
public class UndeliveredMessagesListAdapter extends CursorAdapter {
    private static final String TAG = "UndeliveredMessagesListAdapter";

    private final LayoutInflater mInflater;
    private final ListView mListView;

    public UndeliveredMessagesListAdapter(Context context, Cursor c,
            ListView listView) {
        super(context, c);

        mInflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mListView = listView;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        String type = cursor.getString(COLUMN_MSG_TYPE);
        int errorType = cursor.getInt(COLUMN_MMS_ERROR_TYPE);
        int status = cursor.getInt(COLUMN_SMS_STATUS);
        if (type.equals("mms")) {
            bindMmsView(view, context, cursor);
        } else if(type.equals("sms")) {
            bindSmsView(view, context, cursor);
        }

        // Show error icon for all undelivered messages.
        ImageView errIcon = (ImageView) view.findViewById(R.id.error);
        if ((type.equals("mms") && (errorType < MmsSms.ERR_TYPE_GENERIC_PERMANENT))
                || (type.equals("sms") && (status == Sms.STATUS_PENDING))) {
            errIcon.setImageResource(R.drawable.ic_dialog_email_pending);
        } else {
            errIcon.setImageResource(R.drawable.ic_sms_error);
        }
        errIcon.setVisibility(View.VISIBLE);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mInflater.inflate(R.layout.conversation_header, parent, false);
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        mListView.setSelection(mListView.getCount());
    }

    private void bindSmsView(View view, Context context, Cursor cursor) {
        // Set contact and message body
        String contact = Sms.getDisplayAddress(
                mContext, cursor.getString(COLUMN_SMS_ADDRESS));
        contact = mContext.getString(R.string.to_label) + contact;
        TextView addrView = (TextView) view.findViewById(R.id.from);
        addrView.setText(contact);

        String body = cursor.getString(COLUMN_SMS_BODY);
        TextView bodyView = (TextView) view.findViewById(R.id.subject);
        bodyView.setText(body);

        // Set time stamp
        TextView timeStamp = (TextView) view.findViewById(R.id.date);
        long date = cursor.getLong(COLUMN_SMS_DATE);
        String time = MessageUtils.formatTimeStampString(context, date);
        timeStamp.setText(time);
    }

    private void bindMmsView(View view, Context context, Cursor cursor) {
        if (cursor.getInt(COLUMN_MMS_MSG_TYPE)
                != PduHeaders.MESSAGE_TYPE_SEND_REQ) {
            return;
        }

        long msgId = cursor.getLong(COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, msgId);
        MultimediaMessagePdu msg;
        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(
                    mContext).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Cannot load: " + uri);
            return;
        }

        TextView bodyView = (TextView) view.findViewById(R.id.subject);
        EncodedStringValue subject = msg.getSubject();
        bodyView.setText(subject == null ? "" : subject.getString());

        // Get display address.
        String contact = Mms.getDisplayAddress(
                mContext, EncodedStringValue.concat(((SendReq) msg).getTo()));
        contact = mContext.getString(R.string.to_label) + contact;
        TextView addrView = (TextView) view.findViewById(R.id.from);
        addrView.setText(contact);

        String timeStamp = MessageUtils.formatTimeStampString(
                mContext, ((SendReq) msg).getDate() * 1000L);
        TextView timeStampView = (TextView) view.findViewById(R.id.date);
        timeStampView.setText(timeStamp);
    }
}
