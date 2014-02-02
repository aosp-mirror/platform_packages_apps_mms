/*
* Copyright (C) 2013 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.mms.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Group;
import com.android.mms.data.PhoneNumber;

public class SelectRecipientsListItem extends LinearLayout implements Contact.UpdateListener {

    private static Drawable sDefaultContactImage;

    private static final int MSG_UPDATE_AVATAR = 1;

    private static Handler sHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_AVATAR:
                    SelectRecipientsListItem item = (SelectRecipientsListItem) msg.obj;
                    item.updateAvatarView();
                    break;
            }
        }
    };

    private View mHeader;
    private View mFooter;
    private TextView mSeparator;
    private TextView mNameView;
    private TextView mNumberView;
    private TextView mLabelView;
    private QuickContactBadge mAvatarView;
    private CheckBox mCheckBox;

    private Contact mContact;

    public SelectRecipientsListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (sDefaultContactImage == null) {
            sDefaultContactImage =
                    context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeader = findViewById(R.id.header);
        mFooter = findViewById(R.id.footer);
        mSeparator = (TextView) findViewById(R.id.separator);
        mNameView = (TextView) findViewById(R.id.name);
        mNumberView = (TextView) findViewById(R.id.number);
        mLabelView = (TextView) findViewById(R.id.label);
        mAvatarView = (QuickContactBadge) findViewById(R.id.avatar);
        mCheckBox = (CheckBox) findViewById(R.id.checkbox);
    }

    @Override
    public void onUpdate(Contact updated) {
        if (updated == mContact) {
            sHandler.obtainMessage(MSG_UPDATE_AVATAR, this).sendToTarget();
        }
    }

    private void updateAvatarView() {
        if (mContact == null) {
            // we were unbound in the meantime
            return;
        }

        Drawable avatarDrawable = mContact.getAvatar(mContext, sDefaultContactImage);

        if (mContact.existsInDatabase()) {
            mAvatarView.assignContactUri(mContact.getUri());
        } else {
            mAvatarView.assignContactFromPhone(mContact.getNumber(), true);
        }

        mAvatarView.setImageDrawable(avatarDrawable);
        mAvatarView.setVisibility(View.VISIBLE);
    }

    public final void bind(Context context, final PhoneNumber phoneNumber,
            boolean showHeader, boolean showFooter, boolean isFirst) {
        if (showHeader) {
            String index = phoneNumber.getSectionIndex();
            mHeader.setVisibility(View.VISIBLE);
            mSeparator.setText(index != null ? index.toUpperCase() : "");
        } else {
            mHeader.setVisibility(View.GONE);
        }

        if (showFooter) {
            mFooter.setVisibility(View.VISIBLE);
        } else {
            mFooter.setVisibility(View.GONE);
        }

        if (isFirst) {
            mNameView.setVisibility(View.VISIBLE);

            if (mContact == null) {
                mContact = Contact.get(phoneNumber.getNumber(), false);
                Contact.addListener(this);
            }
            updateAvatarView();
        } else {
            mNameView.setVisibility(View.GONE);
            mAvatarView.setVisibility(View.INVISIBLE);
        }

        mNumberView.setText(phoneNumber.getNumber());
        mNameView.setText(phoneNumber.getName());
        mLabelView.setText(Phone.getTypeLabel(getResources(),
                phoneNumber.getType(), phoneNumber.getLabel()));
        mLabelView.setVisibility(View.VISIBLE);
        mCheckBox.setChecked(phoneNumber.isChecked());
    }

    public final void bind(Context context, final Group group, boolean showHeader) {
        if (showHeader) {
            mHeader.setVisibility(View.VISIBLE);
            mSeparator.setText(R.string.groups_header);
        } else {
            mHeader.setVisibility(View.GONE);
        }

        mFooter.setVisibility(View.VISIBLE);
        mNameView.setVisibility(View.VISIBLE);

        SpannableStringBuilder groupTitle = new SpannableStringBuilder(group.getTitle());
        int before = groupTitle.length();

        groupTitle.append(" ");
        groupTitle.append(Integer.toString(group.getSummaryCount()));
        groupTitle.setSpan(new ForegroundColorSpan(
                context.getResources().getColor(R.color.message_count_color)),
                before, groupTitle.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        mNameView.setText(groupTitle);

        mNumberView.setVisibility(View.VISIBLE);
        mNumberView.setText(group.getAccountName());
        mLabelView.setVisibility(View.GONE);
        mCheckBox.setChecked(group.isChecked());
        mAvatarView.setVisibility(View.GONE);
    }

    public void unbind() {
        Contact.removeListener(this);
        mContact = null;
    }
}
