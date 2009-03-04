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
import com.android.mms.ui.RecipientList.Recipient;
import com.google.android.mms.util.SqliteWrapper;

import android.content.ContentResolver;
import android.content.Context;
import com.android.internal.database.ArrayListCursor;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.provider.Contacts;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.Phones;
import android.telephony.PhoneNumberUtils;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * This adapter is used to filter contacts on both name and number.
 */
public class RecipientsAdapter extends ResourceCursorAdapter {

    public static final int PERSON_ID_INDEX = 1;
    public static final int TYPE_INDEX      = 2;
    public static final int NUMBER_INDEX    = 3;
    public static final int LABEL_INDEX     = 4;
    public static final int NAME_INDEX      = 5;

    private static final String[] PROJECTION_PHONE = {
        Contacts.Phones._ID,        // 0
        Contacts.Phones.PERSON_ID,  // 1
        Contacts.Phones.TYPE,       // 2
        Contacts.Phones.NUMBER,     // 3
        Contacts.Phones.LABEL,      // 4
        Contacts.Phones.NAME,       // 5
    };

    private static final String SORT_ORDER = "name, type";

    private final Context mContext;
    private final ContentResolver mContentResolver;

    public RecipientsAdapter(Context context) {
        super(context, R.layout.recipient_filter_item, null);
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    @Override
    public final CharSequence convertToString(Cursor cursor) {
        String name = cursor.getString(RecipientsAdapter.NAME_INDEX);
        int type = cursor.getInt(RecipientsAdapter.TYPE_INDEX);
        String number = cursor.getString(RecipientsAdapter.NUMBER_INDEX).trim();

        String label = cursor.getString(RecipientsAdapter.LABEL_INDEX);
        CharSequence displayLabel = Phones.getDisplayLabel(mContext, type, label);

        if (number.length() == 0) {
            return number;
        }

        if (name == null) {
            name = "";
        }
        
        String nameAndNumber = Recipient.buildNameAndNumber(name, number);

        SpannableString out = new SpannableString(nameAndNumber);
        int len = out.length();

        if (!TextUtils.isEmpty(name)) {
            out.setSpan(new Annotation("name", name), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            out.setSpan(new Annotation("name", number), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String person_id = cursor.getString(RecipientsAdapter.PERSON_ID_INDEX);
        out.setSpan(new Annotation("person_id", person_id), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new Annotation("label", displayLabel.toString()), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new Annotation("number", number), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return out;
    }

    @Override
    public final void bindView(View view, Context context, Cursor cursor) {
        TextView name = (TextView) view.findViewById(R.id.name);
        name.setText(cursor.getString(NAME_INDEX));

        TextView label = (TextView) view.findViewById(R.id.label);
        int type = cursor.getInt(TYPE_INDEX);
        label.setText(Phones.getDisplayLabel(mContext, type, cursor.getString(LABEL_INDEX)));

        TextView number = (TextView) view.findViewById(R.id.number);
        number.setText("(" + cursor.getString(NUMBER_INDEX) + ")");
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        String wherePhone = null;
        String whereEmail = null;
        String phone = "";
        String cons = null;

        if (constraint != null) {
            cons = constraint.toString();

            if (usefulAsDigits(cons)) {
                phone = PhoneNumberUtils.convertKeypadLettersToDigits(cons);
                if (phone.equals(cons)) {
                    phone = "";
                } else {
                    phone = phone.trim();
                }
            }

            String filter = DatabaseUtils.sqlEscapeString(cons + '%');
            String filterLastName = DatabaseUtils.sqlEscapeString("% " + cons + '%');

            StringBuilder s = new StringBuilder();
            s.append("((name LIKE ");
            s.append(filter);
            s.append(") OR (name LIKE ");
            s.append(filterLastName);
            s.append(") OR (REPLACE(REPLACE(REPLACE(REPLACE(number, ' ', ''), '(', ''), ')', ''), '-', '') LIKE ");
            s.append(filter);
            s.append(")) AND type = ");
            s.append(Phones.TYPE_MOBILE);
            wherePhone = s.toString();
        }

        Cursor phoneCursor = SqliteWrapper.query(mContext, mContentResolver,
                Phones.CONTENT_URI, PROJECTION_PHONE, wherePhone, null, SORT_ORDER);

        if (phone.length() > 0) {
            ArrayList result = new ArrayList();
            result.add(Integer.valueOf(-1));                    // ID
            result.add(Long.valueOf(-1));                       // PERSON_ID
            result.add(Integer.valueOf(Phones.TYPE_CUSTOM));    // TYPE
            result.add(phone);                                  // NUMBER

            /*
             * The "\u00A0" keeps Phones.getDisplayLabel() from deciding
             * to display the default label ("Home") next to the transformation
             * of the letters into numbers.
             */
            result.add("\u00A0");                               // LABEL
            result.add(cons);                                   // NAME

            ArrayList<ArrayList> wrap = new ArrayList<ArrayList>();
            wrap.add(result);

            ArrayListCursor translated = new ArrayListCursor(PROJECTION_PHONE, wrap);

            return new MergeCursor(new Cursor[] { translated, phoneCursor });
        } else {
            return phoneCursor;
        }
    }

    /**
     * Returns true if all the characters are meaningful as digits
     * in a phone number -- letters, digits, and a few punctuation marks.
     */
    private boolean usefulAsDigits(CharSequence cons) {
        int len = cons.length();

        for (int i = 0; i < len; i++) {
            char c = cons.charAt(i);

            if ((c == ' ') || (c == '-') || (c == '(') || (c == ')') || (c == '.')) {
                continue;
            }
            if ((c >= 'A') && (c <= 'Z')) {
                continue;
            }
            if ((c >= 'a') && (c <= 'z')) {
                continue;
            }
            if ((c >= '0') && (c <= '9')) {
                continue;
            }

            return false;
        }

        return true;
    }
}
