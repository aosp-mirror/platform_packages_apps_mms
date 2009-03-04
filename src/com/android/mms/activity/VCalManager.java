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

import com.google.android.mms.util.SqliteWrapper;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Calendar;
import android.provider.Calendar.Calendars;
import android.syncml.pim.PropertyNode;
import android.syncml.pim.VDataBuilder;
import android.syncml.pim.VNode;
import android.syncml.pim.vcalendar.CalendarStruct;
import android.syncml.pim.vcalendar.VCalComposer;
import android.syncml.pim.vcalendar.VCalException;
import android.syncml.pim.vcalendar.VCalParser;
import android.text.format.Time;
import android.util.Log;

import java.util.HashMap;
import java.util.Set;

/**
 * Provides the methods to handle the content provider of calendars for MMS activity client.
 */
public class VCalManager {
    private static final String TAG = "VCalManager";
    private static final String TIME_FORMAT_STRING = "%Y-%m-%d %H:%M:%S";

    private ContentValues mVCalValues;
    private final Context mContext;
    private final ContentResolver mResolver;
    private Uri mUri;
    private HashMap<String, Long> mCalendars = null;

    /**
     * Constructor.
     * @param context the context of the activity.
     * @param data Calendar data.
     * @throws IllegalArgumentException
     */
    public VCalManager(Context context, String data) throws IllegalArgumentException {
        if (context == null) {
            throw new IllegalArgumentException();
        }

        mContext = context;
        mResolver = mContext.getContentResolver();
        mVCalValues = parseVCalendar(data);
    }

    /**
     * Constructor.
     * @param context the context of the activity.
     * @param uri the stored event URI.
     */
    public VCalManager(Context context, Uri uri) {
        mContext = context;
        mResolver = mContext.getContentResolver();
        mUri = uri;
    }

    public String[] getCalendars() {
        if (mCalendars == null) {
            Cursor cursor = SqliteWrapper.query(mContext, mResolver, Calendars.CONTENT_URI,
                                new String[] {Calendars._ID, Calendars.DISPLAY_NAME},
                                null, null, null);

            if (null == cursor) {
                return null;
            }

            mCalendars = new HashMap<String, Long>();
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(Calendars._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(Calendars.DISPLAY_NAME));
                    mCalendars.put(name, id);
                }
            } finally {
                cursor.close();
            }
        }

        Set<String> keySet = mCalendars.keySet();
        int size = keySet.size();
        if (size <= 0) {
            return null;
        }

        String[] calendarArray = new String[size];
        keySet.toArray(calendarArray);
        return calendarArray;
    }

    /**
     * Load the formatted data of the stored calendar event.
     * @return the formatted data string.
     */
    public String getData() {
        if (mUri == null) {
            Log.e(TAG, "Bad content URI.");
            return null;
        }
        Cursor c = SqliteWrapper.query(mContext, mResolver, mUri,
                        null, null, null, null);

        String uid = mUri.toString();
        if (c == null) {
            Log.e(TAG, "Cannot query the content from " + mUri);
            return null;
        }
        try {
            c.moveToFirst();
            CalendarStruct calStruct = new CalendarStruct();

            calStruct.timezone = c.getString(c.getColumnIndexOrThrow(Calendar.Events.EVENT_TIMEZONE));

            //event list:
            CalendarStruct.EventStruct evtStruct = new CalendarStruct.EventStruct();

            evtStruct.uid = uid;
            evtStruct.description = c.getString(c.getColumnIndexOrThrow(
                    Calendar.Events.DESCRIPTION));
            evtStruct.dtend = convertLongToRFC2445DateTime(c.getLong(c.getColumnIndexOrThrow(
                    Calendar.Events.DTEND)));
            evtStruct.dtstart = convertLongToRFC2445DateTime(c.getLong(c.getColumnIndexOrThrow(
                    Calendar.Events.DTSTART)));
            evtStruct.duration = c.getString(c.getColumnIndexOrThrow(
                    Calendar.Events.DURATION));
            evtStruct.event_location = c.getString(c.getColumnIndexOrThrow(
                    Calendar.Events.EVENT_LOCATION));
            evtStruct.has_alarm = c.getString(c.getColumnIndexOrThrow(
                    Calendar.Events.HAS_ALARM));
            evtStruct.last_date = convertLongToRFC2445DateTime(c.getLong(c.getColumnIndexOrThrow(
                    Calendar.Events.LAST_DATE)));
            evtStruct.rrule = c.getString(c.getColumnIndexOrThrow(
                    Calendar.Events.RRULE));
            evtStruct.status = c.getString(c.getColumnIndexOrThrow(
                    Calendar.Events.STATUS));
            evtStruct.title = c.getString(c.getColumnIndexOrThrow(
                    Calendar.Events.TITLE));

            if (!isNull(evtStruct.has_alarm)) {
                getReminders(evtStruct, Uri.parse(uid).getLastPathSegment());
            }

            calStruct.addEventList(evtStruct);

            try{    //build vcalendar:
                VCalComposer composer = new VCalComposer();

                return composer.createVCal(calStruct, VCalComposer.VERSION_VCAL10_INT);
            } catch (Exception e) {
                return null;
            }
        } finally {
            c.close();
        }
    }

    /**
     * Get the name of the calendar event with ".vcs" suffix.
     * @return the name of the calendar event.
     */
    public String getName() {
        if (mUri == null) {
            Log.e(TAG, "Bad content URI.");
            return null;
        }

        Cursor c = SqliteWrapper.query(mContext, mResolver, mUri,
                        null, null, null, null);
        if (c == null) {
            Log.e(TAG, "Cannot query the content from " + mUri);
            return null;
        }
        try {
            c.moveToFirst();
            return c.getString(c.getColumnIndexOrThrow(Calendar.Events.TITLE)) + ".vcs";
        } finally {
            c.close();
        }
    }

    /**
     * Save the calendar data to local database.
     *
     * @param calendarName the calendar into which the vCal is saved.
     *      By calling method getCalendars(), user can get all calendar
     *      names as an string array.
     * @return true if success, otherwise return false.
     */
    public Uri save(String calendarName) {
        Long id = mCalendars.get(calendarName);
        if (null == id) {
            return null;
        }

        mVCalValues.put(Calendar.Events.CALENDAR_ID, id);
        return SqliteWrapper.insert(mContext, mResolver,
                    Calendar.Events.CONTENT_URI, mVCalValues);
    }

    /**
     * Get the title of the calendar event.
     * @return the title.
     */
    public String getTitle() {
        return (String) mVCalValues.get(Calendar.Events.TITLE);
    }

    /**
     * Get the event location of the calendar event.
     * @return the event location.
     */
    public String getEventLocation() {
        return (String) mVCalValues.get(Calendar.Events.EVENT_LOCATION);
    }

    /**
     * Get the description of the calendar event.
     * @return the description.
     */
    public String getDescription() {
        return (String) mVCalValues.get(Calendar.Events.DESCRIPTION);
    }

    /**
     * Get the start time of the calendar event.
     * @return the start time formatted as "%Y-%m-%d %H:%M:%S".
     */
    public String getDateTimeStart() {
        Long start = (Long) mVCalValues.get(Calendar.Events.DTSTART);
        if (start == null) {
            return null;
        }
        Time time = new Time();
        time.set(start);
        return time.format(TIME_FORMAT_STRING);
    }

    /**
     * Get the end time of the calendar event.
     * @return the end time formatted as "%Y-%m-%d %H:%M:%S".
     */
    public String getDateTimeEnd() {
        Long end = (Long) mVCalValues.get(Calendar.Events.DTEND);
        if (end == null) {
            return null;
        }
        Time time = new Time();
        time.set(end);
        return time.format(TIME_FORMAT_STRING);
    }

    /**
     * Get the duration of the calendar event.
     * @return the duration which is NOT formatted as "%Y-%m-%d %H:%M:%S".
     */
    public String getDuration() {
        return (String) mVCalValues.get(Calendar.Events.DURATION);
    }

    /**
     * Get the rule of the calendar event.
     * @return the rule.
     */
    public String getRepeatRule() {
        return (String) mVCalValues.get(Calendar.Events.RRULE);
    }

    /**
     * Get the completed time of the calendar event.
     * @return the completed time formatted as "%Y-%m-%d %H:%M:%S".
     */
    public String getCompleted() {
        Long complete = (Long) mVCalValues.get(Calendar.Events.LAST_DATE);
        if (complete == null) {
            return null;
        }
        Time time = new Time();
        time.set(complete);
        return time.format(TIME_FORMAT_STRING);
    }

    private ContentValues parseVCalendar(String data) {
        VCalParser parser = new VCalParser();
        VDataBuilder builder = new VDataBuilder();

        if (null == data) {
            return null;
        }

        try {
            parser.parse(data, builder);
        } catch (VCalException e) {
            Log.e(TAG, "VCalException: ", e);
            return null;
        }

        String curCalendarId = "";
        for (VNode vnode : builder.vNodeList) {
            // VCALENDAR field MUST present before VENENT and VTODO
            if (vnode.VName.equalsIgnoreCase("VCALENDAR")) {
                // If no Calendar, just set -1 as CalendarId, because user
                // should view the content even if no calendar created.
                curCalendarId = String.valueOf(getFirstIDFromCalendar());
            } else if (vnode.VName.equalsIgnoreCase("VEVENT") ||
                        vnode.VName.equalsIgnoreCase("VTODO")) {
                return setEventMap(vnode, curCalendarId);
            }
        }

        return null;
    }

    private static ContentValues setEventMap(VNode vnode, String calId) {
        ContentValues values = new ContentValues();

        values.put(Calendar.Events.CALENDAR_ID, calId);
        for (PropertyNode prop : vnode.propList) {
            if (prop.propValue != null) {
                Time time = new Time();
                if (prop.propName.equalsIgnoreCase("DESCRIPTION")) {
                    values.put(Calendar.Events.DESCRIPTION, prop.propValue);
                } else if (prop.propName.equalsIgnoreCase("DTEND")) {
                    time.parse(prop.propValue);
                    values.put(Calendar.Events.DTEND, time.toMillis(false /* use isDst */));
                } else if (prop.propName.equalsIgnoreCase("DTSTART")) {
                    time.parse(prop.propValue);
                    values.put(Calendar.Events.DTSTART, time.toMillis(false /* use isDst */));
                } else if (prop.propName.equalsIgnoreCase("SUMMARY")) {
                    values.put(Calendar.Events.TITLE, prop.propValue);
                } else if (prop.propName.equalsIgnoreCase("LOCATION")) {
                    values.put(Calendar.Events.EVENT_LOCATION, prop.propValue);
                } else if (prop.propName.equalsIgnoreCase("DUE")) {
                    values.put(Calendar.Events.DURATION, prop.propValue);
                } else if (prop.propName.equalsIgnoreCase("RRULE")) {
                    values.put(Calendar.Events.RRULE, prop.propValue);
                } else if (prop.propName.equalsIgnoreCase("COMPLETED")) {
                    time.parse(prop.propValue);
                    values.put(Calendar.Events.LAST_DATE, time.toMillis(false /* use isDst */));
                }
            }
        }
        return values;
    }

    private static boolean isNull(String str) {
        if ((str == null) || str.trim().equals("")) {
            return true;
        }
        return false;
    }

    private static String convertLongToRFC2445DateTime(long mills) {
        Time time = new Time();

        time.set(mills);
        return time.format("%Y%m%dT%H%M%SZ");
    }

    private void getReminders(CalendarStruct.EventStruct evtStruct, String localid) {
        Cursor c = SqliteWrapper.query(mContext, mResolver, Calendar.Reminders.CONTENT_URI,
                        null, "event_id=" + localid, null, null);
        String data = "";
        while ((c != null) && c.moveToNext()) {
            data = c.getString(c.getColumnIndexOrThrow(Calendar.Reminders.METHOD));
            evtStruct.addReminderList(data);
        }
        if (c != null) {
            c.close();
        }
    }

    private int getFirstIDFromCalendar() {
        Cursor c = SqliteWrapper.query(mContext, mResolver, Calendar.Calendars.CONTENT_URI,
                        null, null, null, null);

        if (c != null) {
            while (c.moveToNext()) {
                int id = c.getInt(c.getColumnIndexOrThrow("_id"));
                c.close();
                return id;
            }
            c.close();
        }
        return -1;
    }
}
