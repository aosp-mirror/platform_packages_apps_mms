/**
 * Copyright (C) 2013 Intel Corporation, All Rights Reserved
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

import java.io.BufferedReader;
import java.io.File;
import com.android.mms.model.Encapsulation;
import com.android.mms.R;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

public class ReceiveContent {
    private static StringBuffer toSend;
    private static StringBuffer nameBuffer;
    private static StringBuffer phoneBuffer;
    private static StringBuffer emailBuffer;
    private static StringBuffer addressBuffer;
    private static boolean isNameEncoded;
    private static boolean isPhoneEncoded;
    private static boolean isEmailEncoded;
    private static boolean isAddressEncoded;
    private static final String NEW_LINE = "\n";
    private static final String NOTHING = "";
    private static final String EQUAL = "=";
    private static final String ENCODING = "ENCODING=QUOTED-PRINTABLE";
    private static final String BEGIN = "BEGIN";
    private static final String VERSION = "VERSION";
    private static final String TAG = "ReceiveContent";
    private static final String NAME_TAG = "FN";
    private static final String PHONE_TAG = "TEL";
    private static final String EMAIL_TAG = "EMAIL";
    private static final String ADDRESS_TAG = "ADR";
    private static final String END = "END";

    /**
     * Method that receives checked options from OptionList activity
     */
    public static String createFormattedContact(Bundle b, Context context) {
        /**
         * Format data intent adding contact detail to StringBuilder
         */

        toSend = new StringBuffer();
        if (b.getBoolean(context.getString(R.string.name_key)) && nameBuffer != null) {
            StringBuffer s = extractContactDetail(nameBuffer.substring(NAME_TAG.length()), ';', ':');
            if (s != null) {
                if (isNameEncoded) {
                    s = decode(s);
                }
                toSend.append(s);
            }
        } else {
            toSend.append(Encapsulation.NOTHING);
        }
        toSend.append(Encapsulation.PARSE);
        if (b.getBoolean(context.getString(R.string.phone_key)) && phoneBuffer != null) {
            StringBuffer s = new StringBuffer(extractContactDetail(
                    phoneBuffer.substring(PHONE_TAG.length()), ';', ':'));
            if (s != null) {
                if (isPhoneEncoded) {
                    s = decode(s);
                }
                toSend.append(s);
            }
        } else {
            toSend.append(Encapsulation.NOTHING);
        }
        toSend.append(Encapsulation.PARSE);
        if (b.getBoolean(context.getString(R.string.email_key)) && emailBuffer != null) {
            StringBuffer s = new StringBuffer(extractContactDetail(
                    emailBuffer.substring(EMAIL_TAG.length()), ';', ':'));
            if (s != null) {
                if (isEmailEncoded) {
                    s = decode(s);
                }
                toSend.append(s);
            }
        } else {
            toSend.append(Encapsulation.NOTHING);
        }
        toSend.append(Encapsulation.PARSE);
        if (b.getBoolean(context.getString(R.string.address_key)) && addressBuffer != null) {
            StringBuffer s = new StringBuffer(extractContactDetail(
                    addressBuffer.substring(ADDRESS_TAG.length()), ';', ':'));
            if (s != null) {
                if (isAddressEncoded) {
                    s = decode(s);
                }
                toSend.append(s);
            }

        } else {
            toSend.append(Encapsulation.NOTHING);
        }
        toSend.append(Encapsulation.PARSE);
        String message = Encapsulation.encapsulate(toSend.toString());
        return message;
    }

    /**
     * Method changes vcard format from Uri to String
     * @param intent contains the contact in Uri format
     */
    public static Bundle handleContact(ContentResolver cr, Uri uri, Context context) {

        StringBuffer buffer = new StringBuffer();
        nameBuffer = null;
        phoneBuffer = null;
        emailBuffer = null;
        addressBuffer = null;
        isNameEncoded = false;
        isPhoneEncoded = false;
        isEmailEncoded = false;
        isAddressEncoded = false;

        try {
            Scanner s = new Scanner(cr.openInputStream(uri));
            while (s.hasNext()) {
                buffer.append(s.nextLine() + "\n");
            }
        } catch (IOException io) {
            Log.e(TAG, io.toString());
            return null;
        }
        String NEW_LINE = "\n";
        int begin = buffer.indexOf(BEGIN);
        if (begin != -1) {
            buffer.delete(begin, buffer.indexOf(NEW_LINE, begin));
        }
        begin = buffer.indexOf(VERSION);
        if (begin != -1) {
            buffer.delete(begin, buffer.indexOf(NEW_LINE, begin));
        }
        /**
         * Parse buffer and extract contact detail if exists
         */
        nameBuffer = getContactDetail(buffer, NAME_TAG);
        phoneBuffer = getContactDetail(buffer, PHONE_TAG);
        emailBuffer = getContactDetail(buffer, EMAIL_TAG);
        addressBuffer = getContactDetail(buffer, ADDRESS_TAG);

        Bundle bundle = new Bundle();

        /**
         * Send available options to OptionsList
         */
        if (phoneBuffer == null) {
            bundle.putBoolean(context.getString(R.string.phone_key), false);
        } else {
            bundle.putBoolean(context.getString(R.string.phone_key), true);
        }

        if (nameBuffer == null) {
            bundle.putBoolean(context.getString(R.string.name_key), false);
        } else {
            bundle.putBoolean(context.getString(R.string.name_key), true);
        }

        if (emailBuffer == null) {
            bundle.putBoolean(context.getString(R.string.email_key), false);
        } else {
            bundle.putBoolean(context.getString(R.string.email_key), true);
        }

        if (addressBuffer == null) {
            bundle.putBoolean(context.getString(R.string.address_key), false);
        } else {
            bundle.putBoolean(context.getString(R.string.address_key), true);
        }

        return bundle;

    }

    /**
     * Method extracts contact components from a contact detail (like first name
     * from full name)
     * @param buffer contains tags and contact detail
     * @param delim the separator used between contact components
     * @param extraDelim separator between tags and data
     * @return parsed component
     */
    private static StringBuffer extractContactDetail(String buffer, char delim, char extraDelim) {
        StringBuffer contactdetail = new StringBuffer();
        Scanner s = new Scanner(buffer);
        s.useDelimiter(delim + "");
        while (s.hasNext()) {
            String str = s.next();
            contactdetail.append(str.substring(str.indexOf(extraDelim) + 1) + " ");
        }
        return contactdetail.deleteCharAt(contactdetail.length() - 1);
    }

    /**
     * Method return a certain part of a contact component depending on the tag
     * separator
     * @param buffer contains all contact details
     * @param tag specifies which part of a contact detail is needed
     * @return null if component does not exist else component
     */
    private static StringBuffer getContactDetail(StringBuffer buffer, String tag) {

        int beginIndex = buffer.indexOf(tag);
        if (beginIndex != -1) {
            int endIndex = buffer.indexOf(NEW_LINE, beginIndex);
            int beginEncoding = buffer.substring(beginIndex, endIndex).indexOf(ENCODING);
            if (beginEncoding != -1) {
                buffer.delete(beginEncoding, beginEncoding + ENCODING.length());

                if (tag.equals(NAME_TAG)) {
                    endIndex = buffer.indexOf(PHONE_TAG) - 1;
                    if (endIndex == -2) {
                        endIndex = buffer.indexOf(EMAIL_TAG) - 1;
                        if (endIndex == -2) {
                            endIndex = buffer.indexOf(ADDRESS_TAG) - 1;
                            if (endIndex == -2) {
                                endIndex = buffer.indexOf(END);
                            }
                        }
                    }
                    isNameEncoded = true;
                }

                if (tag.equals(PHONE_TAG)) {
                    endIndex = buffer.indexOf(EMAIL_TAG) - 1;
                    if (endIndex == -2) {
                        endIndex = buffer.indexOf(ADDRESS_TAG) - 1;
                        if (endIndex == -2) {
                            endIndex = buffer.indexOf(END);
                        }
                    }
                    isPhoneEncoded = true;
                }

                if (tag.equals(EMAIL_TAG)) {
                    endIndex = buffer.indexOf(ADDRESS_TAG) - 1;
                    if (endIndex == -2) {
                        endIndex = buffer.indexOf(END);
                    }
                    isEmailEncoded = true;
                }

                if (tag.equals(ADDRESS_TAG)) {
                    endIndex = buffer.indexOf(END);
                    isAddressEncoded = true;
                }
            }
            return new StringBuffer(buffer.substring(beginIndex, endIndex));
        }
        return null;
    }

    private static StringBuffer decode(StringBuffer toDecode) {

        QuotedPrintableCodec qpc = new QuotedPrintableCodec();
        StringBuffer decoded = new StringBuffer();
        try {
            String temp = toDecode.toString();
            temp = temp.replace(NEW_LINE, NOTHING);
            temp = temp.replaceAll(EQUAL + EQUAL, EQUAL);
            if (temp.charAt(temp.length() - 1) == '=') {
                int length = temp.length();
                temp = temp.substring(0, length - 1);
            }

            decoded.append(qpc.decode(temp, "UTF-8"));
        } catch (DecoderException e) {
            Log.e(TAG, e.toString());
            return null;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
            return null;
        }
        return decoded;
    }
}
