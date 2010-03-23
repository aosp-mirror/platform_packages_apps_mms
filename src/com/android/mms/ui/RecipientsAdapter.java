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

import com.android.common.ArrayListCursor;
import com.android.mms.R;
import com.android.mms.data.Contact;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
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

    public static final int CONTACT_ID_INDEX = 1;
    public static final int TYPE_INDEX       = 2;
    public static final int NUMBER_INDEX     = 3;
    public static final int LABEL_INDEX      = 4;
    public static final int NAME_INDEX       = 5;

    private static final String[] PROJECTION_PHONE = {
        Phone._ID,                  // 0
        Phone.CONTACT_ID,           // 1
        Phone.TYPE,                 // 2
        Phone.NUMBER,               // 3
        Phone.LABEL,                // 4
        Phone.DISPLAY_NAME,         // 5
    };

    private static final String SORT_ORDER = Contacts.TIMES_CONTACTED + " DESC,"
            + Contacts.DISPLAY_NAME + "," + Phone.TYPE;

    private final Context mContext;
    private final ContentResolver mContentResolver;

    public RecipientsAdapter(Context context) {
        // Note that the RecipientsAdapter doesn't support auto-requeries. If we
        // want to respond to changes in the contacts we're displaying in the drop-down,
        // code using this adapter would have to add a line such as:
        //   mRecipientsAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
        // See ComposeMessageActivity for an example.
        super(context, R.layout.recipient_filter_item, null, false /* no auto-requery */);
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    @Override
    public final CharSequence convertToString(Cursor cursor) {
        String number = cursor.getString(RecipientsAdapter.NUMBER_INDEX);
        if (number == null) {
            return "";
        }
        number = number.trim();

        String name = cursor.getString(RecipientsAdapter.NAME_INDEX);
        int type = cursor.getInt(RecipientsAdapter.TYPE_INDEX);

        String label = cursor.getString(RecipientsAdapter.LABEL_INDEX);
        CharSequence displayLabel = Phone.getDisplayLabel(mContext, type, label);

        if (name == null) {
            name = "";
        } else {
            // Names with commas are the bane of the recipient editor's existence.
            // We've worked around them by using spans, but there are edge cases
            // where the spans get deleted. Furthermore, having commas in names
            // can be confusing to the user since commas are used as separators
            // between recipients. The best solution is to simply remove commas
            // from names.
            name = name.replace(", ", " ")
                       .replace(",", " ");  // Make sure we leave a space between parts of names.
        }

        String nameAndNumber = Contact.formatNameAndNumber(name, number);

        SpannableString out = new SpannableString(nameAndNumber);
        int len = out.length();

        if (!TextUtils.isEmpty(name)) {
            out.setSpan(new Annotation("name", name), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            out.setSpan(new Annotation("name", number), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String person_id = cursor.getString(RecipientsAdapter.CONTACT_ID_INDEX);
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
        CharSequence labelText = Phone.getDisplayLabel(mContext, type,
                cursor.getString(LABEL_INDEX));
        // When there's no label, getDisplayLabel() returns a CharSequence of length==1 containing
        // a unicode non-breaking space. Need to check for that and consider that as "no label".
        if (labelText.length() == 0 ||
                (labelText.length() == 1 && labelText.charAt(0) == '\u00A0')) {
            label.setVisibility(View.GONE);
        } else {
            label.setText(labelText);
            label.setVisibility(View.VISIBLE);
        }

        TextView number = (TextView) view.findViewById(R.id.number);
        number.setText(cursor.getString(NUMBER_INDEX));
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
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
        }

        Uri uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(cons));
        /*
         * if we decide to filter based on phone types use a selection
         * like this.
        String selection = String.format("%s=%s OR %s=%s OR %s=%s",
                Phone.TYPE,
                Phone.TYPE_MOBILE,
                Phone.TYPE,
                Phone.TYPE_WORK_MOBILE,
                Phone.TYPE,
                Phone.TYPE_MMS);
         */
        Cursor phoneCursor =
            mContentResolver.query(uri,
                    PROJECTION_PHONE,
                    null, //selection,
                    null,
                    SORT_ORDER);

        if (phone.length() > 0) {
            ArrayList result = new ArrayList();
            result.add(Integer.valueOf(-1));                    // ID
            result.add(Long.valueOf(-1));                       // CONTACT_ID
            result.add(Integer.valueOf(Phone.TYPE_CUSTOM));     // TYPE
            result.add(phone);                                  // NUMBER

            /*
             * The "\u00A0" keeps Phone.getDisplayLabel() from deciding
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

            if ((c >= '0') && (c <= '9')) {
                continue;
            }
            if ((c == ' ') || (c == '-') || (c == '(') || (c == ')') || (c == '.') || (c == '+')
                    || (c == '#') || (c == '*')) {
                continue;
            }
            if ((c >= 'A') && (c <= 'Z')) {
                continue;
            }
            if ((c >= 'a') && (c <= 'z')) {
                continue;
            }

            return false;
        }

        return true;
    }
}
