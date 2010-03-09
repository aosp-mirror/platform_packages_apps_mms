package com.android.mms.transaction;

import java.util.ArrayList;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.telephony.SmsManager;
import android.util.Log;

import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.google.android.mms.MmsException;
import android.provider.Telephony.Sms;
import com.android.mms.ui.MessageUtils;

public class SmsSingleRecipientSender extends SmsMessageSender {

    private final boolean mRequestDeliveryReport;
    private String mDest;
    private Uri mUri;

    public SmsSingleRecipientSender(Context context, String dest, String msgText, long threadId,
            boolean requestDeliveryReport, Uri uri) {
        super(context, null, msgText, threadId);
        mRequestDeliveryReport = requestDeliveryReport;
        mDest = dest;
        mUri = uri;
    }

    public boolean sendMessage(long token) throws MmsException {
        if (mMessageText == null) {
            // Don't try to send an empty message, and destination should be just
            // one.
            throw new MmsException("Null message body or have multiple destinations.");
        }
        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> messages = null;
        if ((MmsConfig.getEmailGateway() != null) &&
                (Mms.isEmailAddress(mDest) || MessageUtils.isAlias(mDest))) {
            String msgText;
            msgText = mDest + " " + mMessageText;
            mDest = MmsConfig.getEmailGateway();
            messages = smsManager.divideMessage(msgText);
        } else {
           messages = smsManager.divideMessage(mMessageText);
           // remove spaces from destination number (e.g. "801 555 1212" -> "8015551212")
           mDest = mDest.replaceAll(" ", "");
        }
        int messageCount = messages.size();

        if (messageCount == 0) {
            // Don't try to send an empty message.
            throw new MmsException("SmsMessageSender.sendMessage: divideMessage returned " +
                    "empty messages. Original message is \"" + mMessageText + "\"");
        }

        boolean moved = Sms.moveMessageToFolder(mContext, mUri, Sms.MESSAGE_TYPE_OUTBOX, 0);
        if (!moved) {
            throw new MmsException("SmsMessageSender.sendMessage: couldn't move message " +
                    "to outbox: " + mUri);
        }

        ArrayList<PendingIntent> deliveryIntents =  new ArrayList<PendingIntent>(messageCount);
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            if (mRequestDeliveryReport) {
                // TODO: Fix: It should not be necessary to
                // specify the class in this intent.  Doing that
                // unnecessarily limits customizability.
                deliveryIntents.add(PendingIntent.getBroadcast(
                        mContext, 0,
                        new Intent(
                                MessageStatusReceiver.MESSAGE_STATUS_RECEIVED_ACTION,
                                mUri,
                                mContext,
                                MessageStatusReceiver.class),
                        0));
            }
            Intent intent  = new Intent(SmsReceiverService.MESSAGE_SENT_ACTION,
                    mUri,
                    mContext,
                    SmsReceiver.class);
            if (i == messageCount -1) {
                intent.putExtra(SmsReceiverService.EXTRA_MESSAGE_SENT_SEND_NEXT, true);
            }
            sentIntents.add(PendingIntent.getBroadcast(
                    mContext, 0, intent, 0));
        }
        try {
            smsManager.sendMultipartTextMessage(mDest, mServiceCenter, messages, sentIntents, deliveryIntents);
        } catch (Exception ex) {
            throw new MmsException("SmsMessageSender.sendMessage: caught " + ex +
                    " from SmsManager.sendTextMessage()");
        }
        if (Log.isLoggable(LogTag.TRANSACTION, Log.VERBOSE)) {
            log("sendMessage: address=" + mDest + ", threadId=" + mThreadId +
                    ", uri=" + mUri + ", msgs.count=" + messageCount);
        }
        return false;
    }

    private void log(String msg) {
        Log.d(LogTag.TAG, "[SmsSingleRecipientSender] " + msg);
    }
}
