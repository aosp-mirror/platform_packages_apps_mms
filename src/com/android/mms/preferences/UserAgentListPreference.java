package com.android.mms.preferences;

import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.ui.MessagingPreferenceActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.os.Parcelable;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.widget.SeekBar;
import android.widget.EditText;
import android.widget.Toast;

public class UserAgentListPreference extends ListPreference {
    private Context mContext;
    private String mUserAgent;
    private boolean mDialogShowing;
    private EditText mUserAgentCustom;

    public UserAgentListPreference(Context context) {
        super(context);
        mContext = context;
    }

    public UserAgentListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(mContext);
            mUserAgent = prefs.getString(
                    MessagingPreferenceActivity.USER_AGENT,
                    MmsConfig.getUserAgent());
            if (mUserAgent.equals("custom")) {
                showDialog();
            }
        }
    }

    private void showDialog() {
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View v = inflater.inflate(R.layout.custom_user_agent_dialog, null);
        mUserAgentCustom = (EditText) v.findViewById(R.id.CustomUserAgentEditText);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mUserAgentCustom.setText(prefs.getString(MessagingPreferenceActivity.USER_AGENT_CUSTOM, "Android-Mms/2.0"));

        new AlertDialog.Builder(mContext)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setTitle(R.string.pref_title_mms_user_agent)
            .setView(v)
            .setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    mDialogShowing = false;
                }
            })
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    mDialogShowing = false;
                }
            })
            .setPositiveButton(android.R.string.ok,new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    mDialogShowing = false;
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                    SharedPreferences.Editor settings = prefs.edit();
                    settings.putString(MessagingPreferenceActivity.USER_AGENT_CUSTOM,
                            mUserAgentCustom.getText().toString());
                    settings.commit();

                    Toast.makeText(mContext,
                            mContext.getString(R.string.pref_mms_user_agent_custom_set) +
                            mUserAgentCustom.getText().toString(),
                            Toast.LENGTH_LONG).show();
                }
            }).show();
        mDialogShowing = true;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        if (mDialogShowing) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(mContext);
            mUserAgent = prefs.getString(
                    MessagingPreferenceActivity.USER_AGENT,
                    MmsConfig.getUserAgent());
            showDialog();
        }
    }

    @Override
    protected View onCreateDialogView() {
        mDialogShowing = false;
        return super.onCreateDialogView();
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
