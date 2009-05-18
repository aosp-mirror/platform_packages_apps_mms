/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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
package com.android.mms.activity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.Contacts;
import android.syncml.pim.PropertyNode;
import android.syncml.pim.VDataBuilder;
import android.syncml.pim.VNode;
import android.syncml.pim.vcard.ContactStruct;
import android.syncml.pim.vcard.VCardComposer;
import android.syncml.pim.vcard.VCardException;
import android.syncml.pim.vcard.VCardParser;
import android.util.Log;

import com.google.android.mms.util.SqliteWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class VCardManager {
    static private final String TAG = "VCardManager";

    private ArrayList<ContentValues> mContactMethodList;
    private ArrayList<ContentValues> mPhoneList;
    private ArrayList<ContentValues> mOrganizationList;
    private ContentValues mPeople;

    private final String mData;

    private final ContentResolver mResolver;
    private final Context mContext;

    public VCardManager(Context context, String data) {
        mContext = context;
        mResolver = context.getContentResolver();
        mData = data;
        parse(mData);
    }

    public VCardManager(Context context, Uri uri) {
        mContext = context;
        mResolver = context.getContentResolver();
        mData = loadData(uri);
        parse(mData);
    }

    public String getData() {
        return mData;
    }

    /**
     * Get property value.
     *
     * @return the value
     */
    public String getName() {
        return (String) mPeople.get(Contacts.People.NAME);
    }

    /**
     * Save content to content provider.
     */
    public Uri save() {
        try {
            Uri uri = SqliteWrapper.insert(mContext, mResolver, Contacts.People.CONTENT_URI,
                            mPeople);
            ContentValues[] phoneArray = new ContentValues[mPhoneList.size()];
            mResolver.bulkInsert(Uri.withAppendedPath(uri, "phones"), mPhoneList
                    .toArray(phoneArray));
            ContentValues[] organizationArray = new ContentValues[mOrganizationList.size()];
            mResolver.bulkInsert(Uri.withAppendedPath(uri, "organizations"), mOrganizationList
                    .toArray(organizationArray));
            ContentValues[] contactMethodArray = new ContentValues[mContactMethodList.size()];
            mResolver.bulkInsert(Uri.withAppendedPath(uri, "contact_methods"), mContactMethodList
                    .toArray(contactMethodArray));
            return uri;
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(mContext, e);
        }
        return null;
    }

    private void parse(String data) {
        VCardParser mParser = new VCardParser();
        VDataBuilder builder = new VDataBuilder();

        mContactMethodList = new ArrayList<ContentValues>();
        mPhoneList = new ArrayList<ContentValues>();
        mOrganizationList = new ArrayList<ContentValues>();
        mPeople = new ContentValues();

        try {
            mParser.parse(data, builder);
        } catch (VCardException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        for (VNode vnode : builder.vNodeList) {
            setContactsValue(vnode);
        }
    }

    private void setContactsValue(VNode vnode) {
        String title = null;
        String company = null;
        int phoneContentType = -1;
        ContentValues phoneContent = new ContentValues();
        HashSet<String> typeList = new HashSet<String>();

        for (PropertyNode prop : vnode.propList) {
            if (prop.propName.equalsIgnoreCase("TITLE") && (prop.propValue != null)) {
                title = prop.propValue;
            }
            if (prop.propName.equalsIgnoreCase("ORG") && (prop.propValue != null)) {
                company = prop.propValue;
            }

            // People.
            if (prop.propName.equalsIgnoreCase("N") && (prop.propValue != null)) {
                mPeople.put(Contacts.People.NAME, prop.propValue);
            }

            // Phone
            if (prop.propName.equalsIgnoreCase("TEL")
                    && (prop.propValue != null)) {
                phoneContent.clear();
                typeList.clear();

                for (String typeStr : prop.paramMap_TYPE) {
                    typeList.add(typeStr.toUpperCase());
                }
                if (typeList.contains("FAX")) {
                    phoneContentType = -1;
                    if (typeList.contains("HOME")) {
                        phoneContentType = Contacts.Phones.TYPE_FAX_HOME;
                        typeList.remove("HOME");
                    } else if (typeList.contains("WORK")) {
                        phoneContentType = Contacts.Phones.TYPE_FAX_WORK;
                        typeList.remove("WORK");
                    }
                    if (phoneContentType != -1) {
                        phoneContent.put(Contacts.Phones.TYPE, phoneContentType);
                        phoneContent.put(Contacts.Phones.NUMBER, prop.propValue);
                        mPhoneList.add(phoneContent);
                        phoneContent.clear();
                        typeList.remove("FAX");
                    }
                }
                for (String typeStr : typeList) {
                    phoneContent.clear();
                    // The following just to match the type that predefined in
                    // contacts.db. If not match, we will save the phone number
                    // with one type in phone column
                    if (typeStr.equals("HOME")) {
                        phoneContentType = Contacts.Phones.TYPE_HOME;
                    } else if (typeStr.equals("WORK")) {
                        phoneContentType = Contacts.Phones.TYPE_WORK;
                    } else if (typeStr.equals("FAX")) {
                        phoneContentType = Contacts.Phones.TYPE_FAX_WORK;
                    } else if (typeStr.equals("PAGER")) {
                        phoneContentType = Contacts.Phones.TYPE_PAGER;
                    } else if (typeStr.equals("CELL")) {
                        phoneContentType = Contacts.Phones.TYPE_MOBILE;
                    } else if (typeStr.equals("X-OTHER")) {
                        phoneContentType = Contacts.Phones.TYPE_OTHER;
                    } else {
                        phoneContentType = Contacts.Phones.TYPE_CUSTOM;
                        phoneContent.put(Contacts.Phones.LABEL, typeStr);
                    } // end if-else
                    phoneContent.put(Contacts.Phones.TYPE, phoneContentType);
                    phoneContent.put(Contacts.Phones.NUMBER, prop.propValue);
                    mPhoneList.add(phoneContent);
                } // end for
            } // end if
            //Contact method.
            if (prop.propName.equalsIgnoreCase("EMAIL")
                    && (prop.propValue != null)) {
                ContentValues mapContactM = new ContentValues();
                int iType;

                for (String typeName : prop.paramMap_TYPE) {
                    mapContactM.clear();
                    mapContactM.put(Contacts.ContactMethods.DATA,
                            prop.propValue.replaceAll(";", " ").trim());
                    mapContactM.put(Contacts.ContactMethods.KIND,
                            Contacts.KIND_EMAIL);
                    iType = getEmailTypeByName(typeName);
                    mapContactM.put(Contacts.ContactMethods.TYPE, iType);
                    if (iType == Contacts.ContactMethods.TYPE_CUSTOM) {
                        mapContactM.put(Contacts.ContactMethods.LABEL, typeName);
                    }
                    mContactMethodList.add(mapContactM);
                }
            }

            if (prop.propName.equalsIgnoreCase("ADR") && (prop.propValue != null)) {
                ContentValues mapContactM = new ContentValues();

                mapContactM.put(Contacts.ContactMethods.DATA,
                        prop.propValue.replaceAll(";", " ").trim());
                mapContactM.put(Contacts.ContactMethods.KIND,
                        Contacts.KIND_POSTAL);

                String typeName = setToString(prop.paramMap_TYPE);
                int addressType = getAddressTypeByName(typeName);
                mapContactM.put(Contacts.ContactMethods.TYPE, addressType);
                if (addressType == Contacts.ContactMethods.TYPE_CUSTOM) {
                    mapContactM.put(Contacts.ContactMethods.LABEL, typeName);
                }

                mContactMethodList.add(mapContactM);
            }
        }

        // Organization
        if ((title != null) || (company != null)) {
            ContentValues organization = new ContentValues();
            organization.put(Contacts.Organizations.COMPANY, company);
            organization.put(Contacts.Organizations.TITLE, title);
            organization.put(Contacts.Organizations.TYPE,
                    Contacts.Organizations.TYPE_WORK);
            mOrganizationList.add(organization);
        }
    }

    private int getEmailTypeByName(String typeName) {
        if ((typeName.length() == 0) || typeName.equalsIgnoreCase("INTERNET")) {
            return Contacts.ContactMethods.TYPE_HOME;
        } else if (typeName.equalsIgnoreCase("HOME")) {
            return Contacts.ContactMethods.TYPE_HOME;
        } else if (typeName.equalsIgnoreCase("WORK")){
            return Contacts.ContactMethods.TYPE_WORK;
        } else if (typeName.equalsIgnoreCase("X-OTHER")){
            return Contacts.ContactMethods.TYPE_OTHER;
        } else {
            return Contacts.ContactMethods.TYPE_CUSTOM;
        }
    }

    private int getAddressTypeByName(String typeName) {
        if (typeName.length() == 0) {
            return Contacts.ContactMethods.TYPE_HOME;
        } else if (typeName.equalsIgnoreCase("HOME")) {
            return Contacts.ContactMethods.TYPE_HOME;
        } else if (typeName.equalsIgnoreCase("WORK")){
            return Contacts.ContactMethods.TYPE_WORK;
        } else if (typeName.equalsIgnoreCase("X-OTHER")){
            return Contacts.ContactMethods.TYPE_OTHER;
        } else {
            return Contacts.ContactMethods.TYPE_CUSTOM;
        }
    }


    private String setToString(Set<String> set) {
        StringBuilder typeListB = new StringBuilder("");
        for (String o : set) {
            typeListB.append(o).append(";");
        }

        String typeList = typeListB.toString();
        if (typeList.endsWith(";")) {
            return typeList.substring(0, typeList.length() - 1);
        } else {
            return typeList;
        }
    }

    private String loadData(Uri uri) {
        Cursor contactC = SqliteWrapper.query(mContext, mResolver, uri,
                                null, null, null, null);

        ContactStruct contactStruct = new ContactStruct();

        contactC.moveToFirst();

        // Get people info.
        contactStruct.name = contactC.getString(
                contactC.getColumnIndexOrThrow(Contacts.People.NAME));
        contactStruct.notes.add(contactC.getString(
                contactC.getColumnIndexOrThrow(Contacts.People.NOTES)));

        // Get phone list.
        String data, label;
        int kind, type;

        Cursor orgC = SqliteWrapper.query(mContext, mResolver,
                            Uri.withAppendedPath(uri, "organizations"),
                            null, "isprimary", null, null);

        contactStruct.company = null;
        contactStruct.title = null;
        if (orgC != null) {
            if (orgC.moveToNext()) {
                contactStruct.company = contactC.getString(
                        orgC.getColumnIndexOrThrow(Contacts.Organizations.COMPANY));
                contactStruct.title = contactC.getString(
                       orgC.getColumnIndexOrThrow(Contacts.Organizations.TITLE));
            }
            orgC.close();
        }

        Cursor phoneC = SqliteWrapper.query(mContext, mResolver,
                            Uri.withAppendedPath(uri, "phones"),
                            null, null, null, null);

        if (phoneC != null) {
            while (phoneC.moveToNext()) {
                data = phoneC.getString(phoneC.getColumnIndexOrThrow(
                        Contacts.Phones.NUMBER));
                type = phoneC.getInt(phoneC.getColumnIndexOrThrow(
                        Contacts.Phones.TYPE));
                label = phoneC.getString(phoneC.getColumnIndexOrThrow(
                        Contacts.Phones.LABEL));
                //contactStruct.addPhone(data, type, label);
            }
            phoneC.close();
        }

        // Get contact-method list.
        Cursor contactMethodC = SqliteWrapper.query(mContext, mResolver,
                                    Uri.withAppendedPath(uri, "contact_methods"),
                                    null, null, null, null);

        if (contactMethodC != null) {
            while (contactMethodC.moveToNext()) {
                kind = contactMethodC.getInt(contactMethodC.getColumnIndexOrThrow(
                        Contacts.ContactMethods.KIND));
                data = contactMethodC.getString(contactMethodC.getColumnIndexOrThrow(
                        Contacts.ContactMethods.DATA));
                type = contactMethodC.getInt(contactMethodC.getColumnIndexOrThrow(
                        Contacts.ContactMethods.TYPE));
                label = contactMethodC.getString(contactMethodC.getColumnIndexOrThrow(
                        Contacts.ContactMethods.LABEL));
                contactStruct.addContactmethod(kind, type, data, label, false);
            }
            contactMethodC.close();
        }
        // Generate vCard data.
        try {
            VCardComposer composer = new VCardComposer();
            return composer.createVCard(contactStruct,
                    VCardParser.VERSION_VCARD21_INT);
        } catch (VCardException e) {
            return null;
        }
    }
}
