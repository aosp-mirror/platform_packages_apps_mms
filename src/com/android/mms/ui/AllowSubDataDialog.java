package com.android.mms.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.android.internal.widget.SubscriptionView;
import com.android.mms.R;
import android.telephony.SubscriptionManager;
import android.telephony.SubInfoRecord;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

public class AllowSubDataDialog {
    
    public static final int DIALOG_MESSAGE_TYPE_SEND = 0;
    public static final int DIALOG_MESSAGE_TYPE_RETRIEVE = 0;

    public static AlertDialog showAllowSubDataDialog(Context context, OnClickListener positiveListener,
            OnClickListener negativeListener, OnCancelListener cancelListener, int type, long subIndex) {
        Resources r = context.getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View contents = View.inflate(context, R.layout.allow_sim_data_dialog_view, null);
        if (type == DIALOG_MESSAGE_TYPE_SEND) {
            builder.setTitle(R.string.subDataDialog_title_send);
        } else {
            builder.setTitle(R.string.subDataDialog_title_download);
        }
        TextView text = (TextView) contents.findViewById(R.id.warning_message);
        text.setText(formatWarmingMessage(context, type, subIndex));
        builder.setView(contents);

        builder.setPositiveButton(r.getString(R.string.subDataDialog_positive), positiveListener);
        builder.setNegativeButton(r.getString(R.string.subDataDialog_negative), negativeListener);
        builder.setOnCancelListener(cancelListener);
        return builder.show();
    }
    
    private static CharSequence formatWarmingMessage(Context context, int type, long subIndex) {
        Resources r = context.getResources();
        SpannableStringBuilder buffer = new SpannableStringBuilder();
        SubInfoRecord subInfo = SubscriptionManager.getSubInfoUsingSubId(context, subIndex);
        String subName = null;
        if (subInfo != null) {
            subName = subInfo.mDisplayName;
        }
        if (subName == null) {
            String defaultSubName = r.getString(R.string.default_sim_name);
            subName = String.format(defaultSubName, subIndex);
        }
        if (subName.length() > 7) {
            StringBuilder buf = new StringBuilder();
            buf.append(subName.substring(0, 4) + "..." + subName.substring(subName.length() - 1));
            subName = buf.toString();
        }
        String stringBeforeSubInfo;
        String stringAfterSubInfo;
        if (type == DIALOG_MESSAGE_TYPE_SEND) {
            stringBeforeSubInfo = r.getString(R.string.subDataDialog_send_1) + " ";
            stringAfterSubInfo = " " + r.getString(R.string.subDataDialog_send_2);
            
        } else {
            stringBeforeSubInfo = r.getString(R.string.subDataDialog_download_1) + " ";
            stringAfterSubInfo = " " + r.getString(R.string.subDataDialog_download_2);
        }
        int spanStart = stringBeforeSubInfo.length();
        int spanEnd = spanStart + subName.length();
        buffer.append(stringBeforeSubInfo);
        buffer.append(subName);
        buffer.append(stringAfterSubInfo);
        if (subInfo != null) {
            int colorRes = subInfo.mSimIconRes[1];
            Drawable drawable = context.getResources().getDrawable(colorRes);
            int color = subInfo.mColor;
            buffer.setSpan(new MSubBackgroundImageSpan(colorRes, drawable), spanStart, spanEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            buffer.setSpan(new ForegroundColorSpan(color), spanStart, spanEnd, 
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return buffer;
    }

    
    
}
