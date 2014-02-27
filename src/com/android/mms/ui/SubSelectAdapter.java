package com.android.mms.ui;

import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telephony.SubscriptionController.SubInfoRecord;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import com.android.mms.R;
import com.android.internal.widget.SubscriptionView;

public class SubSelectAdapter extends BaseAdapter {
    private LayoutInflater mInf;
    private String mPreferenceKey;
    private Context mContext;
    private List<SubInfoRecord> mList;

    public SubSelectAdapter(Context context, String preferenceKey, List<SubInfoRecord> list) {
        mInf = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
        mPreferenceKey = preferenceKey;
        mList = list;
    }


    @Override
    public int getCount() {
        return mList.size();
    }

    @Override
    public Object getItem(int position) {
        return mList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = mInf.inflate(R.layout.sub_select_item, null);
        SubscriptionView subView = (SubscriptionView) view.findViewById(R.id.subItem);
        subView.setThemeType(SubscriptionView.LIGHT_THEME);
        subView.setSubInfo(mList.get(position));
        CheckBox subCheckBox = (CheckBox) view.findViewById(R.id.subCheckBox);
        if (MessagingPreferenceActivity.MANAGE_SIM_MESSAGE_MODE.equals(mPreferenceKey)) {
            subCheckBox.setVisibility(View.GONE);
        } else {
            subCheckBox.setChecked(isChecked(position));
        }
        return view;
    }

    /**
     * get the related preference data by position to find whether 
     * @param position
     * @return whether has checked 
     */
    public boolean isChecked(int position) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getBoolean(Long.toString((mList.get(position)).mSubId) + "_" + mPreferenceKey,
                false);
    }

}
