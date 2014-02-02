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

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mms.R;
import com.android.mms.data.Group;
import com.android.mms.data.PhoneNumber;
import com.android.mms.data.RecipientsListLoader;

import java.util.ArrayList;
import java.util.HashSet;

public class SelectRecipientsList extends ListActivity implements
        AdapterView.OnItemClickListener,
        LoaderManager.LoaderCallbacks<ArrayList<RecipientsListLoader.Result>> {
    private static final int MENU_DONE = 0;
    private static final int MENU_MOBILE = 1;
    private static final int MENU_GROUPS = 2;

    public static final String EXTRA_RECIPIENTS = "recipients";
    public static final String PREF_MOBILE_NUMBERS_ONLY = "pref_key_mobile_numbers_only";
    public static final String PREF_SHOW_GROUPS = "pref_key_show_groups";

    private SelectRecipientsListAdapter mListAdapter;
    private int mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
    private int mSavedFirstItemOffset;
    private HashSet<PhoneNumber> mCheckedPhoneNumbers;
    private boolean mMobileOnly = true;
    private boolean mShowGroups = true;
    private View mProgressSpinner;

    // Keys for extras and icicles
    private final static String LAST_LIST_POS = "last_list_pos";
    private final static String LAST_LIST_OFFSET = "last_list_offset";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.select_recipients_list_screen);

        mProgressSpinner = findViewById(R.id.progress_spinner);

        // List view
        ListView listView = getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        listView.setFastScrollEnabled(true);
        listView.setFastScrollAlwaysVisible(true);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setEmptyView(findViewById(R.id.empty));
        listView.setOnItemClickListener(this);

        // Get things ready
        mCheckedPhoneNumbers = new HashSet<PhoneNumber>();
        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState != null) {
            mSavedFirstVisiblePosition = savedInstanceState.getInt(LAST_LIST_POS,
                    AdapterView.INVALID_POSITION);
            mSavedFirstItemOffset = savedInstanceState.getInt(LAST_LIST_OFFSET, 0);
        } else {
            mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
            mSavedFirstItemOffset = 0;
        }

        ActionBar mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(LAST_LIST_POS, mSavedFirstVisiblePosition);
        outState.putInt(LAST_LIST_OFFSET, mSavedFirstItemOffset);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Remember where the list is scrolled to so we can restore the scroll position
        // when we come back to this activity and *after* we complete querying for the
        // contacts.
        ListView listView = getListView();
        mSavedFirstVisiblePosition = listView.getFirstVisiblePosition();
        View firstChild = listView.getChildAt(0);
        mSavedFirstItemOffset = (firstChild == null) ? 0 : firstChild.getTop();

        if (mListAdapter != null) {
            unbindListItems();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Load the required preference values
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mMobileOnly = sharedPreferences.getBoolean(PREF_MOBILE_NUMBERS_ONLY, true);
        mShowGroups = sharedPreferences.getBoolean(PREF_SHOW_GROUPS, true);

        menu.add(0, MENU_DONE, 0, R.string.menu_done)
             .setIcon(R.drawable.ic_menu_done_holo_light)
             .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT)
             .setVisible(false);

        menu.add(0, MENU_MOBILE, 0, R.string.menu_mobile)
             .setCheckable(true)
             .setChecked(mMobileOnly)
             .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        menu.add(0, MENU_GROUPS, 0, R.string.menu_groups)
             .setCheckable(true)
             .setChecked(mShowGroups)
             .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MENU_DONE).setVisible(mCheckedPhoneNumbers.size() > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        switch (item.getItemId()) {
            case MENU_DONE:
                ArrayList<String> numbers = new ArrayList<String>();
                for (PhoneNumber phoneNumber : mCheckedPhoneNumbers) {
                    if (phoneNumber.isChecked()) {
                        numbers.add(phoneNumber.getNumber());
                    }
                }

                // Pass the resulting set of numbers back
                Intent intent = new Intent();
                intent.putExtra(EXTRA_RECIPIENTS, numbers);
                setResult(RESULT_OK, intent);
                finish();
                return true;
            case MENU_MOBILE:
                // If it was checked before it should be unchecked now and vice versa
                mMobileOnly = !mMobileOnly;
                item.setChecked(mMobileOnly);
                prefs.edit().putBoolean(PREF_MOBILE_NUMBERS_ONLY, mMobileOnly).commit();

                // Restart the loader to reflect the change
                getLoaderManager().restartLoader(0, null, this);
                return true;
            case MENU_GROUPS:
                // If it was checked before it should be unchecked now and vice versa
                mShowGroups = !mShowGroups;
                item.setChecked(mShowGroups);
                prefs.edit().putBoolean(PREF_SHOW_GROUPS, mShowGroups).commit();

                // Restart the loader to reflect the change
                getLoaderManager().restartLoader(0, null, this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long arg) {
        RecipientsListLoader.Result item =
                (RecipientsListLoader.Result) adapter.getItemAtPosition(position);

        if (item.group != null) {
            checkGroup(item.group, !item.group.isChecked());
        } else {
            checkPhoneNumber(item.phoneNumber, !item.phoneNumber.isChecked());
            updateGroupCheckStateForNumber(item.phoneNumber, null);
        }

        invalidateOptionsMenu();
        mListAdapter.notifyDataSetChanged();
    }

    private void checkPhoneNumber(PhoneNumber phoneNumber, boolean check) {
        phoneNumber.setChecked(check);
        if (check) {
            mCheckedPhoneNumbers.add(phoneNumber);
        } else {
            mCheckedPhoneNumbers.remove(phoneNumber);
        }
    }

    private void updateGroupCheckStateForNumber(PhoneNumber phoneNumber, Group excludedGroup) {
        ArrayList<Group> phoneGroups = phoneNumber.getGroups();
        if (phoneGroups == null) {
            return;
        }

        if (phoneNumber.isChecked() && phoneNumber.isDefault()) {
            for (Group group : phoneGroups) {
                if (group == excludedGroup) {
                    continue;
                }
                boolean checked = true;
                for (PhoneNumber number : group.getPhoneNumbers()) {
                    if (number.isDefault() && !number.isChecked()) {
                        checked = false;
                        break;
                    }
                }
                group.setChecked(checked);
            }
        } else if (!phoneNumber.isChecked()) {
            for (Group group : phoneGroups) {
                if (group != excludedGroup) {
                    group.setChecked(false);
                }
            }
        }
    }

    private void checkGroup(Group group, boolean check) {
        group.setChecked(check);
        ArrayList<PhoneNumber> phoneNumbers = group.getPhoneNumbers();

        if (phoneNumbers != null) {
            for (PhoneNumber phoneNumber : phoneNumbers) {
                if (phoneNumber.isDefault()) {
                    checkPhoneNumber(phoneNumber, check);
                    updateGroupCheckStateForNumber(phoneNumber, group);
                }
            }
        }
    }

    private void unbindListItems() {
        final ListView listView = getListView();
        final int count = listView.getChildCount();
        for (int i = 0; i < count; i++) {
            mListAdapter.unbindView(listView.getChildAt(i));
        }
    }

    @Override
    public Loader<ArrayList<RecipientsListLoader.Result>> onCreateLoader(int id, Bundle args) {
        // Show the progress indicator
        mProgressSpinner.setVisibility(View.VISIBLE);
        return new RecipientsListLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<ArrayList<RecipientsListLoader.Result>> loader,
            ArrayList<RecipientsListLoader.Result> data) {
        // We have an old list, get rid of it before we start again
        if (mListAdapter != null) {
            mListAdapter.notifyDataSetInvalidated();
            unbindListItems();
        }

        // Hide the progress indicator
        mProgressSpinner.setVisibility(View.GONE);

        // Create and set the list adapter
        mListAdapter = new SelectRecipientsListAdapter(this, data);

        if (getIntent() != null) {
            String[] initialRecipients = getIntent().getStringArrayExtra(EXTRA_RECIPIENTS);
            if (initialRecipients != null) {
                for (String recipient : initialRecipients) {
                    for (RecipientsListLoader.Result result : data) {
                        if (result.phoneNumber != null && result.phoneNumber.equals(recipient)) {
                            checkPhoneNumber(result.phoneNumber, true);
                            updateGroupCheckStateForNumber(result.phoneNumber, null);
                            break;
                        }
                    }
                }
                invalidateOptionsMenu();
            }
            setIntent(null);
        }

        if (mListAdapter == null) {
            // We have no numbers to show, indicate it
            TextView emptyText = (TextView) getListView().getEmptyView();
            emptyText.setText(mMobileOnly ?
                    R.string.no_recipients_mobile_only : R.string.no_recipients);
        } else {
            setListAdapter(mListAdapter);
            getListView().setRecyclerListener(mListAdapter);
        }
    }

    @Override
    public void onLoaderReset(Loader<ArrayList<RecipientsListLoader.Result>> data) {
        mListAdapter.notifyDataSetInvalidated();
    }
}
