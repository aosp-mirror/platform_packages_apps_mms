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

package com.android.mms.transaction;

import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF;

import com.android.mms.R;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.ConversationList;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.android.mms.ui.UndeliveredMessagesActivity;
import com.android.mms.util.AddressUtils;

import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.util.SqliteWrapper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class is used to update the notification indicator. It will check whether
 * there are unread messages. If yes, it would show the notification indicator,
 * otherwise, hide the indicator.
 */
public class MessagingNotification {
    public static final String NOTIFICATION_CLICK_RECEIVER =
            "com.android.mms.transaction.NotificationClickReceiver";
    private static final String TAG = "MessagingNotification";

    private static final int NOTIFICATION_ID = 123;
    public static final int MESSAGE_FAILED_NOTIFICATION_ID = 789;
    public static final int DOWNLOAD_FAILED_NOTIFICATION_ID = 531;

    // This must be consistent with the column constants below.
    private static final String[] MMS_STATUS_PROJECTION = new String[] {
        Mms.THREAD_ID, Mms.DATE, Mms._ID, Mms.SUBJECT, Mms.SUBJECT_CHARSET };

    // This must be consistent with the column constants below.
    private static final String[] SMS_STATUS_PROJECTION = new String[] {
        Sms.THREAD_ID, Sms.DATE, Sms.ADDRESS, Sms.SUBJECT, Sms.BODY };

    // These must be consistent with MMS_STATUS_PROJECTION and
    // SMS_STATUS_PROJECTION.
    private static final int COLUMN_THREAD_ID   = 0;
    private static final int COLUMN_DATE        = 1;
    private static final int COLUMN_MMS_ID      = 2;
    private static final int COLUMN_SMS_ADDRESS = 2;
    private static final int COLUMN_SUBJECT     = 3;
    private static final int COLUMN_SUBJECT_CS  = 4;
    private static final int COLUMN_SMS_BODY    = 4;

    private static final String NEW_INCOMING_SM_CONSTRAINT =
            "(" + Sms.TYPE + " = " + Sms.MESSAGE_TYPE_INBOX
            + " AND " + Sms.READ + " = 0)";

    private static final String NEW_INCOMING_MM_CONSTRAINT =
            "(" + Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_INBOX
            + " AND " + Mms.READ + "=0"
            + " AND (" + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_NOTIFICATION_IND
            + " OR " + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_RETRIEVE_CONF + "))";

    private static final MmsSmsNotificationInfoComparator INFO_COMPARATOR =
            new MmsSmsNotificationInfoComparator();

    private MessagingNotification() {
    }

    /**
     * Checks to see if there are any unread messages or delivery
     * reports.  Shows the most recent notification if there is one.
     *
     * @param context the context to use
     */
    public static void updateNewMessageIndicator(Context context) {
        updateNewMessageIndicator(context, false);
    }

    /**
     * Checks to see if there are any unread messages or delivery
     * reports.  Shows the most recent notification if there is one.
     *
     * @param context the context to use
     * @param isNew if notify a new message comes, it should be true, otherwise, false.
     */
    public static void updateNewMessageIndicator(Context context, boolean isNew) {
        SortedSet<MmsSmsNotificationInfo> accumulator =
                new TreeSet<MmsSmsNotificationInfo>(INFO_COMPARATOR);

        int count = 0;
        count += accumulateNotificationInfo(
                accumulator, getMmsNewMessageNotificationInfo(context));
        count += accumulateNotificationInfo(
                accumulator, getSmsNewMessageNotificationInfo(context));

        cancelNotification(context, NOTIFICATION_ID);
        if (!accumulator.isEmpty()) {
            accumulator.first().deliver(context, isNew, count);
        }
    }

    /**
     * Deletes any delivery report notifications for the specified
     * thread, then checks to see if there are any unread messages or
     * delivery reports.  Shows the most recent notification if there
     * is one.
     *
     * @param context the context to use
     * @param threadId the thread for which to clear delivery notifications
     */
    public static void updateNewMessageIndicator(
            Context context, long threadId) {
        updateNewMessageIndicator(context);
    }

    private static final int accumulateNotificationInfo(
            SortedSet set, MmsSmsNotificationInfo info) {
        if (info != null) {
            set.add(info);

            return info.mCount;
        }

        return 0;
    }

    private static final class MmsSmsNotificationInfo {
        public Intent mClickIntent;
        public String mDescription;
        public int mIconResourceId;
        public CharSequence mTicker;
        public long mTimeMillis;
        public String mTitle;
        public int mCount;

        public MmsSmsNotificationInfo(
                Intent clickIntent, String description, int iconResourceId,
                CharSequence ticker, long timeMillis, String title, int count) {
            mClickIntent = clickIntent;
            mDescription = description;
            mIconResourceId = iconResourceId;
            mTicker = ticker;
            mTimeMillis = timeMillis;
            mTitle = title;
            mCount = count;
        }

        public void deliver(Context context, boolean isNew, int count) {
            updateNotification(
                    context, mClickIntent, mDescription, mIconResourceId,
                    isNew, mTicker, mTimeMillis, mTitle, count);
        }

        public long getTime() {
            return mTimeMillis;
        }
    }

    private static final class MmsSmsNotificationInfoComparator
            implements Comparator<MmsSmsNotificationInfo> {
        public int compare(
                MmsSmsNotificationInfo info1, MmsSmsNotificationInfo info2) {
            return Long.signum(info2.getTime() - info1.getTime());
        }
    }

    public static final MmsSmsNotificationInfo getMmsNewMessageNotificationInfo(
            Context context) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = SqliteWrapper.query(context, resolver, Mms.CONTENT_URI,
                            MMS_STATUS_PROJECTION, NEW_INCOMING_MM_CONSTRAINT,            
                            null, Mms.DATE + " desc");

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long msgId = cursor.getLong(COLUMN_MMS_ID);
                    Uri msgUri = Mms.CONTENT_URI.buildUpon().appendPath(
                            Long.toString(msgId)).build();
                    String address = AddressUtils.getFrom(context, msgUri);
                    String subject = getMmsSubject(
                            cursor.getString(COLUMN_SUBJECT), cursor.getInt(COLUMN_SUBJECT_CS));
                    long threadId = cursor.getLong(COLUMN_THREAD_ID);
                    long timeMillis = cursor.getLong(COLUMN_DATE) * 1000;

                    return getNewMessageNotificationInfo(
                            address, subject, context,
                            R.drawable.stat_notify_mms, null, threadId,
                            timeMillis, cursor.getCount());
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    public static final MmsSmsNotificationInfo getSmsNewMessageNotificationInfo(
            Context context) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = SqliteWrapper.query(context, resolver, Sms.CONTENT_URI,
                            SMS_STATUS_PROJECTION, NEW_INCOMING_SM_CONSTRAINT,
                            null, Sms.DATE + " desc");

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    String address = cursor.getString(COLUMN_SMS_ADDRESS);
                    String body = cursor.getString(COLUMN_SMS_BODY);
                    long threadId = cursor.getLong(COLUMN_THREAD_ID);
                    long timeMillis = cursor.getLong(COLUMN_DATE);

                    return getNewMessageNotificationInfo(
                            address, body, context, R.drawable.stat_notify_sms,
                            null, threadId, timeMillis, cursor.getCount());
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    private static final MmsSmsNotificationInfo getNewMessageNotificationInfo(
            String address,
            String body,
            Context context,
            int iconResourceId,
            String subject,
            long threadId,
            long timeMillis,
            int count) {
        Intent clickIntent = getAppIntent();
        clickIntent.setData(
                Uri.withAppendedPath(
                        clickIntent.getData(), Long.toString(threadId)));
        clickIntent.setAction(Intent.ACTION_VIEW);

        String senderInfo = buildTickerMessage(
                context, address, null, null).toString();
        String senderInfoName = senderInfo.substring(
                0, senderInfo.length() - 2);
        CharSequence ticker = buildTickerMessage(
                context, address, subject, body);

        return new MmsSmsNotificationInfo(
                clickIntent, body, iconResourceId, ticker, timeMillis,
                senderInfoName, count);
    }

    public static void cancelNotification(Context context, int notificationId) {
        NotificationManager nm = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);

        nm.cancel(notificationId);
    }

    private static Intent getAppIntent() {
        Intent appIntent = new Intent(Intent.ACTION_MAIN, Threads.CONTENT_URI);

        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return appIntent;
   }

    private static void updateNotification(
            Context context,
            Intent clickIntent,
            String description,
            int iconRes,
            boolean isNew,
            CharSequence ticker,
            long timeMillis,
            String title,
            int count) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        if (!sp.getBoolean(
                    MessagingPreferenceActivity.NOTIFICATION_ENABLED, true)) {
            return;
        }

        Notification notification = new Notification(
                iconRes, ticker, timeMillis);
        PendingIntent pendingIntent;

        if (count > 1) {
            String multiDescription = context.getString(R.string.notification_multiple);
            String multiTitle = context.getString(R.string.notification_multiple_title);

            Intent multiIntent = getAppIntent();
            multiIntent.setAction(Intent.ACTION_MAIN);
            multiIntent.setType("vnd.android-dir/mms-sms");
            pendingIntent =
                PendingIntent.getActivity(context, 0, multiIntent, 0);

            notification.setLatestEventInfo(
                    context, multiTitle, multiDescription.replaceAll("%s", Integer.toString(count)),
                    pendingIntent);
        } else {
            pendingIntent =
                PendingIntent.getActivity(context, 0, clickIntent, 0);

            notification.setLatestEventInfo(
                    context, title, description, pendingIntent);
        }

        if (isNew) {
            boolean vibrate = sp.getBoolean(MessagingPreferenceActivity.NOTIFICATION_VIBRATE, true);
            if (vibrate) {
                notification.defaults |= Notification.DEFAULT_VIBRATE;
            }

            String ringtoneStr = sp
                    .getString(MessagingPreferenceActivity.NOTIFICATION_RINGTONE, null);
            notification.sound = TextUtils.isEmpty(ringtoneStr) ? null : Uri.parse(ringtoneStr);
        }

        notification.flags |= Notification.FLAG_SHOW_LIGHTS;
        notification.ledARGB = 0xff00ff00;
        notification.ledOnMS = 500;
        notification.ledOffMS = 2000;

        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(NOTIFICATION_ID, notification);
    }

    protected static CharSequence buildTickerMessage(
            Context context, String address, String subject, String body) {
        String displayAddress = Mms.getDisplayAddress(context, address);

        StringBuilder buf = new StringBuilder(
                displayAddress == null
                ? ""
                : displayAddress.replace('\n', ' '));
        buf.append(':').append(' ');

        int offset = buf.length();
        if (!TextUtils.isEmpty(subject)) {
            subject = subject.replace('\n', ' ');
            buf.append(subject);
            buf.append(' ');
        }

        if (!TextUtils.isEmpty(body)) {
            body = body.replace('\n', ' ');
            buf.append(body);
        }

        SpannableString spanText = new SpannableString(buf.toString());
        spanText.setSpan(new StyleSpan(Typeface.BOLD), 0, offset,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spanText;
    }
    
    private static String getMmsSubject(String sub, int charset) {
        return TextUtils.isEmpty(sub) ? ""
                : new EncodedStringValue(charset, PduPersister.getBytes(sub)).getString();
    }
    
    public static void notifyDownloadFailed(Context context, long threadId) {
        notifyFailed(context, true, true, threadId);
    }

    public static void notifySendFailed(Context context, boolean isMms) {
        notifyFailed(context, isMms, false, 0);
    }

    private static void notifyFailed(Context context, boolean isMms, boolean isDownload, long threadId) {
        // TODO factor out common code for creating notifications
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        boolean enabled = sp.getBoolean(MessagingPreferenceActivity.NOTIFICATION_ENABLED, true);
        if (!enabled) {
            return;
        }
        
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent failedIntent = null;
        if (isDownload) {
            failedIntent = new Intent(context, ComposeMessageActivity.class);
            failedIntent.putExtra("thread_id", threadId);         
        } else {
            failedIntent = new Intent(context, UndeliveredMessagesActivity.class);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, failedIntent, 0);

        Notification notification = new Notification();
        notification.icon = isMms ? R.drawable.stat_notify_mms_failed : 
                R.drawable.stat_notify_sms_failed;

        String title = isDownload ?
                context.getString(R.string.message_download_failed_title) :
                    context.getString(R.string.message_send_failed_title);
        notification.tickerText = title;

        notification.setLatestEventInfo(context, title, 
                context.getString(R.string.message_failed_body),
                pendingIntent);

        boolean vibrate = sp.getBoolean(MessagingPreferenceActivity.NOTIFICATION_VIBRATE, true);
        if (vibrate) {
            notification.defaults |= Notification.DEFAULT_VIBRATE;
        }

        String ringtoneStr = sp
                .getString(MessagingPreferenceActivity.NOTIFICATION_RINGTONE, null);
        notification.sound = TextUtils.isEmpty(ringtoneStr) ? null : Uri.parse(ringtoneStr);

        if (isDownload) {
            nm.notify(DOWNLOAD_FAILED_NOTIFICATION_ID, notification);
        } else {
            nm.notify(MESSAGE_FAILED_NOTIFICATION_ID, notification);
        }
    }

    public static void updateSendFailedNotification(Context context) {
        Cursor mmsCursor = SqliteWrapper.query(context, context.getContentResolver(),
                Mms.Outbox.CONTENT_URI, null, null, null, null);
        Cursor smsCursor = SqliteWrapper.query(context, context.getContentResolver(),
                Uri.withAppendedPath(Sms.CONTENT_URI, "undelivered"),
                null, null, null, null);
        if ( (mmsCursor == null || mmsCursor.getCount() < 1) &&
                (smsCursor == null || smsCursor.getCount() < 1) ) {
            cancelNotification(context, MESSAGE_FAILED_NOTIFICATION_ID);
        }
    }
}
