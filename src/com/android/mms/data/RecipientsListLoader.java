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

package com.android.mms.data;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.android.mms.ui.SelectRecipientsList;

public class RecipientsListLoader extends AsyncTaskLoader<ArrayList<RecipientsListLoader.Result>> {
    private ArrayList<Result> mResults;

    public static class Result {
        public PhoneNumber phoneNumber;
        public Group group;
    }

    public RecipientsListLoader(Context context) {
        super(context);
    }

    @Override
    public ArrayList<Result> loadInBackground() {
        final Context context = getContext();
        ArrayList<PhoneNumber> phoneNumbers = PhoneNumber.getPhoneNumbers(context);
        if (phoneNumbers == null) {
            return new ArrayList<Result>();
        }

        // Get things ready
        ArrayList<Result> results = new ArrayList<Result>();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showGroups = prefs.getBoolean(SelectRecipientsList.PREF_SHOW_GROUPS, true);

        if (showGroups) {
            ArrayList<Group> groups = Group.getGroups(context);
            ArrayList<GroupMembership> groupMemberships = GroupMembership.getGroupMemberships(context);
            Map<Long, ArrayList<Long>> groupIdWithContactsId = new HashMap<Long, ArrayList<Long>>();

            // Store GID with all its CIDs
            if (groups != null && groupMemberships != null) {
                for (GroupMembership membership : groupMemberships) {
                    Long gid = membership.getGroupId();
                    Long uid = membership.getContactId();

                    if (!groupIdWithContactsId.containsKey(gid)) {
                        groupIdWithContactsId.put(gid, new ArrayList<Long>());
                    }

                    if (!groupIdWithContactsId.get(gid).contains(uid)) {
                        groupIdWithContactsId.get(gid).add(uid);
                    }
                }

                // For each PhoneNumber, find its GID, and add it to correct Group
                for (PhoneNumber phoneNumber : phoneNumbers) {
                    long cid = phoneNumber.getContactId();

                    for (Map.Entry<Long, ArrayList<Long>> entry : groupIdWithContactsId.entrySet()) {
                        if (!entry.getValue().contains(cid)) {
                            continue;
                        }
                        for (Group group : groups) {
                            if (group.getId() == entry.getKey()) {
                                group.addPhoneNumber(phoneNumber);
                                phoneNumber.addGroup(group);
                            }
                        }
                    }
                }

                // Add the groups to the list first
                for (Group group : groups) {
                    // Due to filtering there may be groups that have contacts, but no
                    // phone numbers. Filter those.
                    if (!group.getPhoneNumbers().isEmpty()) {
                        Result result = new Result();
                        result.group = group;
                        results.add(result);
                    }
                }
            }
        }

        // Add phone numbers to the list
        for (PhoneNumber phoneNumber : phoneNumbers) {
            Result result = new Result();
            result.phoneNumber = phoneNumber;
            results.add(result);
        }

        // We are done
        return results;
    }

    // Called when there is new data to deliver to the client. The
    // super class will take care of delivering it; the implementation
    // here just adds a little more logic.
    @Override
    public void deliverResult(ArrayList<Result> results) {
        mResults = results;

        if (isStarted()) {
            // If the Loader is started, immediately deliver its results.
            super.deliverResult(results);
        }
    }

    @Override
    protected void onStartLoading() {
        if (mResults != null) {
            // If we currently have a result available, deliver it immediately.
            deliverResult(mResults);
        }

        if (takeContentChanged() || mResults == null) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        // At this point we can release the resources associated if needed.
        mResults = null;
    }
}
