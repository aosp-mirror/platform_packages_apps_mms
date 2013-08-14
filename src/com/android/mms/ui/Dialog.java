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

package com.android.mms.ui;

import java.util.ArrayList;
import java.util.StringTokenizer;

import com.android.mms.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.view.Window;

import com.android.mms.model.Encapsulation;
import com.android.mms.transaction.CheckIsContact;

/*
 * Ask user if he wants the contact to be saved or not
 */
public class Dialog extends Activity {

    private String mMessage;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog);

        Intent intent = getIntent();
        if (intent.getCharSequenceExtra(CheckIsContact.MESSAGE) != null)
            mMessage = intent.getCharSequenceExtra(CheckIsContact.MESSAGE).toString();

    }

    /**
     * Create a new contact with received details if user chooses Save button
     */
    public void saveContact(View view) {
        StringTokenizer st;

        st = new StringTokenizer(mMessage, Encapsulation.PARSE, false);
        ArrayList<String> info = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            String s;
            s = st.nextToken().toString();
            if (s.equals(Encapsulation.NOTHING))
                info.add("");
            else
                info.add(s);

        }

        Intent addContactIntent = new Intent(Intent.ACTION_INSERT);
        addContactIntent.setType(ContactsContract.Contacts.CONTENT_TYPE);
        addContactIntent.putExtra(ContactsContract.Intents.Insert.NAME, info.get(0));
        addContactIntent.putExtra(ContactsContract.Intents.Insert.PHONE, info.get(1));
        addContactIntent.putExtra(ContactsContract.Intents.Insert.EMAIL, info.get(2));
        addContactIntent.putExtra(ContactsContract.Intents.Insert.POSTAL, info.get(3));
        startActivity(addContactIntent);

        finish();

    }

    /**
     * Close the Dialog if user doesn't want to save
     */
    public void cancel(View view) {
        finish();
    }
}
