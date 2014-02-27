package com.android.mms.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;

import com.android.internal.widget.SubscriptionView;
import com.android.mms.R;
import android.telephony.SubscriptionController;
import android.telephony.SubscriptionController.SubInfoRecord;
import android.view.View;
import android.widget.TextView;

public class AllowSubDataDialog {

    public static AlertDialog showAllowSubDataDialog(Context context, OnClickListener positiveListener,
            OnClickListener negativeListener, OnCancelListener cancelListener, long subIndex) {
        Resources r = context.getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.simDataDialog_title);
        View contents = View.inflate(context, R.layout.allow_sim_data_dialog_view, null);
        SubscriptionView subView = (SubscriptionView) (contents.findViewById(R.id.sub_info_view));
        SubInfoRecord subInfoRecord = SubscriptionController.getSubInfoUsingSubId(context, subIndex);
        if (subInfoRecord != null) {
            subView.setSubInfo(subInfoRecord);
        }
        subView.setThemeType(SubscriptionView.LIGHT_THEME);
        TextView text = (TextView) contents.findViewById(R.id.warning_message);
        text.setText(R.string.simDataDialog_text);
        builder.setView(contents);

        builder.setPositiveButton(r.getString(R.string.simDataDialog_positive), positiveListener);
        builder.setNegativeButton(r.getString(R.string.simDataDialog_negative), negativeListener);
        builder.setOnCancelListener(cancelListener);
        return builder.show();
    }

    
    
}
