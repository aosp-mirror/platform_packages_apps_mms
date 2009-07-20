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

import com.android.mms.ui.RecipientList.Recipient;
import com.android.mms.util.ContactInfoCache;

import android.content.Context;
import android.provider.Telephony.Mms;
import android.text.Annotation;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.util.Log;

import java.util.Iterator;

/**
 * Provide UI for editing the recipients of multi-media messages.
 */
public class RecipientsEditor extends MultiAutoCompleteTextView {
    private int mLongPressedPosition = -1;
    private final RecipientsEditorTokenizer mTokenizer;

    public RecipientsEditor(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.autoCompleteTextViewStyle);
        mTokenizer = new RecipientsEditorTokenizer(context, this);
        setTokenizer(mTokenizer);
        // For the focus to move to the message body when soft Next is pressed
        setImeOptions(EditorInfo.IME_ACTION_NEXT);

        /*
         * The point of this TextWatcher is that when the user chooses
         * an address completion from the AutoCompleteTextView menu, it
         * is marked up with Annotation objects to tie it back to the
         * address book entry that it came from.  If the user then goes
         * back and edits that part of the text, it no longer corresponds
         * to that address book entry and needs to have the Annotations
         * claiming that it does removed.
         */
        addTextChangedListener(new TextWatcher() {
            private Annotation[] mAffected;

            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
                mAffected = ((Spanned) s).getSpans(start, start + count,
                                                   Annotation.class);
            }

            public void onTextChanged(CharSequence s, int start,
                                      int before, int after) {

            }

            public void afterTextChanged(Editable s) {
                if (mAffected != null) {
                    for (Annotation a : mAffected) {
                        s.removeSpan(a);
                    }
                }

                mAffected = null;
            }
        });
    }

    public RecipientList getRecipientList() {
        return mTokenizer.getRecipientList();
    }

    public void populate(RecipientList list) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        Iterator<Recipient> iter = list.iterator();
        while (iter.hasNext()) {
            if (sb.length() != 0) {
                sb.append(", ");
            }

            Recipient r = iter.next();
            sb.append(r.toToken());
        }

        setText(sb);
    }

    private int pointToPosition(int x, int y) {
        x -= getCompoundPaddingLeft();
        y -= getExtendedPaddingTop();


        x += getScrollX();
        y += getScrollY();

        Layout layout = getLayout();
        if (layout == null) {
            return -1;
        }

        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);

        return off;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            mLongPressedPosition = pointToPosition(x, y);
        }

        return super.onTouchEvent(ev);
    }

    @Override
    protected ContextMenuInfo getContextMenuInfo() {
        if ((mLongPressedPosition >= 0)) {
            Spanned text = getText();
            if (mLongPressedPosition <= text.length()) {
                int start = mTokenizer.findTokenStart(text, mLongPressedPosition);
                int end = mTokenizer.findTokenEnd(text, start);

                if (end != start) {
                    Recipient r = getRecipientAt(getText(), start, end, mContext);
                    return new RecipientContextMenuInfo(r);
                }
            }
        }
        return null;
    }

    private static Recipient getRecipientAt(Spanned sp, int start, int end, Context context) {
        Annotation[] a = sp.getSpans(start, end, Annotation.class);
        String person_id = getAnnotation(a, "person_id");
        String name = getAnnotation(a, "name");
        String label = getAnnotation(a, "label");
        String bcc = getAnnotation(a, "bcc");
        String number = getAnnotation(a, "number");

        int displayEnd = -1;
        if (a.length > 0) {
            displayEnd = sp.getSpanEnd(a[0]);
        }

        Recipient r = new Recipient();

        r.name = name;
        r.label = label;
        r.bcc = bcc.equals("true");
        r.number = TextUtils.isEmpty(number) ? TextUtils.substring(sp, start, end) : number;
        r.displayEnd = displayEnd;
        
        if (TextUtils.isEmpty(r.name) && Mms.isEmailAddress(r.number)) {
            ContactInfoCache cache = ContactInfoCache.getInstance();
            r.name = cache.getDisplayName(context, r.number);
        }

        r.nameAndNumber = Recipient.buildNameAndNumber(r.name, r.number);
        
        if (person_id.length() > 0) {
            r.person_id = Long.parseLong(person_id);
        } else {
            r.person_id = -1;
        }

        return r;
    }

    private static String getAnnotation(Annotation[] a, String key) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].getKey().equals(key)) {
                return a[i].getValue();
            }
        }

        return "";
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isPopupShowing()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_COMMA:
                    ListAdapter adapter = getAdapter();
                    // There is at least one item in the dropdown list
                    // when isPopupShowing() is true.
                    Object selectedItem = adapter.getItem(0);
                    replaceText(convertSelectionToString(selectedItem));
                    dismissDropDown();
                    return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private class RecipientsEditorTokenizer
            extends MultiAutoCompleteTextView.CommaTokenizer
            implements MultiAutoCompleteTextView.Tokenizer {
        private final MultiAutoCompleteTextView mList;
        private final LayoutInflater mInflater;
        private final TextAppearanceSpan mLabelSpan;
        private final TextAppearanceSpan mTypeSpan;
        private final Context mContext;

        RecipientsEditorTokenizer(Context context, MultiAutoCompleteTextView list) {
            mInflater = LayoutInflater.from(context);
            mList = list;
            mContext = context;

            final int size = android.R.style.TextAppearance_Small;
            final int color = android.R.styleable.Theme_textColorSecondary;
            mLabelSpan = new TextAppearanceSpan(context, size, color);
            mTypeSpan = new TextAppearanceSpan(context, size, color);
        }

        public RecipientList getRecipientList() {
            Spanned sp = mList.getText();
            int len = sp.length();
            RecipientList rl = new RecipientList();
            
            int start = 0;
            int i = 0;
            while (i < len + 1) {
                if ((i == len) || (sp.charAt(i) == ',')) {
                    if (i > start) {
                        Recipient r = getRecipientAt(sp, start, i, mContext);

                        rl.add(r);

                        if (r.displayEnd > i) {
                            i = r.displayEnd;
                        }
                    }

                    i++;

                    while ((i < len) && (sp.charAt(i) == ' ')) {
                        i++;
                    }

                    start = i;
                } else {
                    i++;
                }
            }

            return rl;
        }
    }

    static class RecipientContextMenuInfo implements ContextMenuInfo {
        final Recipient recipient;

        RecipientContextMenuInfo(Recipient r) {
            recipient = r;
        }
    }
}
