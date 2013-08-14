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

import com.android.mms.R;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class OptionsList extends Activity implements OnItemClickListener {

    private ListView mContactDetails;
    private ArrayOptions mAdapter;
    private ArrayList<ListItemComponents> mContactValues;
    private Button mButton;
    private Bundle mExtras;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_option_list);

        /**
         * Insert ListView items in a list
         */
        mContactDetails = (ListView) findViewById(R.id.options_list);
        mContactValues = new ArrayList<ListItemComponents>();
        ListItemComponents field = new ListItemComponents();
        field.setName(getString(R.string.name_key));
        mContactValues.add(field);
        field = new ListItemComponents();
        field.setName(getString(R.string.phone_key));
        mContactValues.add(field);
        field = new ListItemComponents();
        field.setName(getString(R.string.email_key));
        mContactValues.add(field);
        field = new ListItemComponents();
        field.setName(getString(R.string.address_key));
        mContactValues.add(field);

        /**
         * initialize layout components
         */
        mAdapter = new ArrayOptions(this, mContactValues);
        mContactDetails.setAdapter(mAdapter);
        mContactDetails.setOnItemClickListener(this);

        mButton = (Button) findViewById(R.id.save_button);
        mButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Intent data = new Intent();
                for (int i = 0; i < mAdapter.getCount(); i++) {
                    data.putExtra(mAdapter.getItem(i).getName(), mAdapter.getItem(i).isChecked());
                }
                setResult(RESULT_OK, data);
                finish();
            }

        });
        /**
         * Check available contact detail options
         */
        mExtras = getIntent().getExtras();
        if (mExtras != null) {
            for (int i = 0; i < mAdapter.getCount(); i++) {
                if (mExtras.containsKey(mAdapter.getItem(i).getName())) {
                    mAdapter.getItem(i).setChecked(
                            mExtras.getBoolean(mAdapter.getItem(i).getName()));
                } else {
                    mAdapter.getItem(i).setChecked(false);
                }
            }
        } else {
            for (int i = 0; i < mAdapter.getCount(); i++) {
                mAdapter.getItem(i).setChecked(false);
            }
        }

    }

    /**
     * Set check item on click textview
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ListItemComponents myObj = (ListItemComponents) parent.getItemAtPosition(position);
        if (mExtras.getBoolean(myObj.getName())) {
            myObj.setChecked(!myObj.isChecked());
        } else {
            Toast.makeText(this, String.format(getString(R.string.not_found), myObj.getName()),
                    Toast.LENGTH_SHORT).show();
        }
        mContactDetails.invalidateViews();
    }

}
