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

import com.android.internal.telephony.CallerInfo;
import com.android.mms.transaction.MessageSender;

import android.content.Context;
import android.provider.Telephony.Mms;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.util.Regex;

import java.util.ArrayList;
import java.util.Iterator;

public class RecipientList {
    private final ArrayList<Recipient> mRecipients = new ArrayList<Recipient>();
    private final ArrayList<Recipient> mInvalidRecipients = new ArrayList<Recipient>();

    public RecipientList() {
    }

    public int size() { return mRecipients.size(); }

    public int countInvalidRecipients() { return mInvalidRecipients.size(); }
    
    public void add(Recipient r) {
        if ((null != r) && Recipient.isValid(r.number)) {
            mRecipients.add(r.filter());
        } else {
            mInvalidRecipients.add(r);
        }
    }

    public Iterator<Recipient> iterator() { return mRecipients.iterator(); }

    public static class Recipient {
        public long person_id = -1;
        public String name;
        public CharSequence label;
        public String number;
        public boolean bcc;

        @Override
        public String toString() {
            return "{ name=" + name + " number= " + number +
                    " person_id=" + person_id + " label=" + label +
                    " bcc=" + bcc + " }";
        }

        /*
         * These are the characters that we permit in input but that we
         * filter out.
         */
        private static final String PHONE_NUMBER_SEPARATORS = " ()-.";

        public static boolean isValid(String recipient) {
            return isPhoneNumber(recipient) || Mms.isEmailAddress(recipient);
        }

        public static boolean isPhoneNumber(String recipient) {
            /*
             * Don't use Regex.PHONE_PATTERN because that is intended to
             * detect things that look like phone numbers in arbitrary text,
             * not to validate whether a given string is usable as a phone
             * number.
             *
             * Accept anything that contains nothing but digits and the
             * characters in PHONE_NUMBER_SEPARATORS, plus an optional
             * leading plus, as long as there is at least one digit.
             * Reject any other characters.
             */

            int len = recipient.length();
            int digits = 0;
            
            for (int i = 0; i < len; i++) {
                char c = recipient.charAt(i);

                if (Character.isDigit(c)) {
                    digits++;
                    continue;
                }
                if (PHONE_NUMBER_SEPARATORS.indexOf(c) >= 0) {
                    continue;
                }
                if (c == '+' && i == 0) {
                    continue;
                }

                return false;
            }

            if (digits == 0) {
                return false;
            } else {
                return true;
            }
        }

        public Recipient filter() {
            Recipient result = new Recipient();

            result.person_id = person_id;
            result.name = name;
            result.label = label;
            result.number = Mms.isEmailAddress(number)
                    ? number
                    : filterPhoneNumber(number);
            result.bcc = bcc;
            return result;
        }

        /**
         * Returns a string with all phone number separator characters removed.
         * @param phoneNumber A phone number to clean (e.g. "+1 (212) 479-7990")
         * @return A string without the separators (e.g. "12124797990")
         */
        public static String filterPhoneNumber(String phoneNumber) {
            if (phoneNumber == null) {
                return null;
            }

            int length = phoneNumber.length();
            StringBuilder builder = new StringBuilder(length);

            for (int i = 0; i < length; i++) {
                char character = phoneNumber.charAt(i);

                if (PHONE_NUMBER_SEPARATORS.indexOf(character) == -1) {
                    builder.append(character);
                }
            }
            return builder.toString();
        }

        public CharSequence toToken() {
            SpannableString s = new SpannableString(number);
            int len = s.length();

            if (len == 0) {
                return s;
            }

            if (person_id != -1) {
                s.setSpan(new Annotation("person_id",
                                         String.valueOf(person_id)),
                          0, len,
                          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (name != null) {
                s.setSpan(new Annotation("name", name), 0, len,
                          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (label != null) {
                s.setSpan(new Annotation("label", label.toString()), 0, len,
                          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (number != null) {
                s.setSpan(new Annotation("number", number), 0, len,
                          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            s.setSpan(new Annotation("bcc", String.valueOf(bcc)), 0, len,
                      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            return s;
        }
    }

    public static RecipientList from(String address, Context context) {
        RecipientList list = new RecipientList();

        if (!TextUtils.isEmpty(address)) {
            String[] phoneNumbers = address.split(MessageSender.RECIPIENTS_SEPARATOR);
            for (String number : phoneNumbers) {
                Recipient recipient = new Recipient();
                if (number.startsWith("%bcc%")) {
                    recipient.bcc = true;
                    number = number.substring(5);
                }
                /*
                 * TODO: Consider getting the CallerInfo object asynchronously
                 * to help with ui responsiveness, instead of running the query
                 * directly from the UI thread
                 */
                CallerInfo ci = CallerInfo.getCallerInfo(context, number);
                if (TextUtils.isEmpty(ci.name)) {
                    recipient.person_id = -1;
                    if (MessageUtils.isLocalNumber(ci.phoneNumber)) {
                        recipient.name = "Me";
                    } else {
                        recipient.name = ci.phoneNumber;
                    }
                } else {
                    recipient.person_id = ci.person_id;
                    recipient.name = ci.name;
                }
                recipient.label = ci.phoneLabel;
                recipient.number = (ci.phoneNumber == null) ? "" : ci.phoneNumber;

                list.add(recipient.filter());
            }
        }
        return list;
    }

    public String[] getToNumbers() {
        ArrayList<String> numbers = new ArrayList<String>();
        int count = mRecipients.size();
        for (int i = 0 ; i < count ; i++) {
            Recipient recipient = mRecipients.get(i);
            if (!recipient.bcc && !TextUtils.isEmpty(recipient.number)) {
                numbers.add(recipient.number);
            }
        }
        return numbers.toArray(new String[numbers.size()]);
    }

    public boolean containsBcc() {
        int count = mRecipients.size();
        for (int i = 0; i < count; i++) {
            if (mRecipients.get(i).bcc) {
                return true;
            }
        }
        return false;
    }

    public boolean containsEmail() {
        int count = mRecipients.size();
        for (int i = 0; i < count; i++) {
            if (Mms.isEmailAddress(mRecipients.get(i).number)) {
                return true;
            }
        }
        return false;
    }

    public String[] getBccNumbers() {
        ArrayList<String> numbers = new ArrayList<String>();
        int count = mRecipients.size();
        for (int i = 0 ; i < count ; i++) {
            Recipient recipient = mRecipients.get(i);
            if (recipient.bcc && !TextUtils.isEmpty(recipient.number)) {
                numbers.add(recipient.number);
            }
        }
        return numbers.toArray(new String[numbers.size()]);
    }

    public String[] getNumbers() {
        int count = mRecipients.size();
        ArrayList<String> numbers = new ArrayList<String>(count);
        for (int i = 0 ; i < count ; i++) {
            numbers.add((mRecipients.get(i)).number);
        }
        return numbers.toArray(new String[numbers.size()]);
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        int count = mRecipients.size();
        for (int i = 0 ; i < count ; i++) {
            if (i != 0) {
                sb.append(MessageSender.RECIPIENTS_SEPARATOR);
            }

            Recipient recipient = mRecipients.get(i);
            if (recipient.bcc) {
                sb.append("%bcc%");
            }
            sb.append(recipient.number);
        }
        return sb.toString();
    }

    public boolean hasInvalidRecipient() {
        return !mInvalidRecipients.isEmpty();
    }

    public boolean hasValidRecipient() {
        return !mRecipients.isEmpty();
    }

    public String getInvalidRecipientString() {
        StringBuilder sb = new StringBuilder();
        int count = mInvalidRecipients.size();
        for (int i = 0 ; i < count ; i++) {
            if (i != 0) {
                sb.append(",");
            }

            Recipient recipient = mInvalidRecipients.get(i);
            if (recipient.bcc) {
                sb.append("%bcc%");
            }
            sb.append(recipient.number);
        }
        return sb.toString();
    }
}
