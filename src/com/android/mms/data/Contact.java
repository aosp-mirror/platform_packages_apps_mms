package com.android.mms.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Presence;
import android.provider.Telephony.Mms;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.mms.ui.MessageUtils;
import com.android.mms.util.ContactInfoCache;
import com.android.mms.util.TaskStack;
import com.android.mms.LogTag;

public class Contact {
    private static final String TAG = "Contact";
    private static final boolean V = false;

    private static final TaskStack sTaskStack = new TaskStack();

//    private static final ContentObserver sContactsObserver = new ContentObserver(new Handler()) {
//        @Override
//        public void onChange(boolean selfUpdate) {
//            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
//                log("contact changed, invalidate cache");
//            }
//            invalidateCache();
//        }
//    };

    private static final ContentObserver sPresenceObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfUpdate) {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                log("presence changed, invalidate cache");
            }
            invalidateCache();
        }
    };

    private final HashSet<UpdateListener> mListeners = new HashSet<UpdateListener>();

    private String mNumber;
    private String mName;
    private String mNameAndNumber;   // for display, e.g. Fred Flintstone <670-782-1123>
    private boolean mNumberIsModified; // true if the number is modified

    private long mRecipientId;       // used to find the Recipient cache entry
    private String mLabel;
    private long mPersonId;
    private int mPresenceResId;      // TODO: make this a state instead of a res ID
    private String mPresenceText;
    private BitmapDrawable mAvatar;
    private boolean mIsStale;

    @Override
    public synchronized String toString() {
        return String.format("{ number=%s, name=%s, nameAndNumber=%s, label=%s, person_id=%d }",
                mNumber, mName, mNameAndNumber, mLabel, mPersonId);
    }

    public interface UpdateListener {
        public void onUpdate(Contact updated);
    }

    private Contact(String number) {
        mName = "";
        setNumber(number);
        mNumberIsModified = false;
        mLabel = "";
        mPersonId = 0;
        mPresenceResId = 0;
        mIsStale = true;
    }

    private static void logWithTrace(String msg, Object... format) {
        Thread current = Thread.currentThread();
        StackTraceElement[] stack = current.getStackTrace();

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(current.getId());
        sb.append("] ");
        sb.append(String.format(msg, format));

        sb.append(" <- ");
        int stop = stack.length > 7 ? 7 : stack.length;
        for (int i = 3; i < stop; i++) {
            String methodName = stack[i].getMethodName();
            sb.append(methodName);
            if ((i+1) != stop) {
                sb.append(" <- ");
            }
        }

        Log.d(TAG, sb.toString());
    }

    public static Contact get(String number, boolean canBlock) {
        if (V) logWithTrace("get(%s, %s)", number, canBlock);

        if (TextUtils.isEmpty(number)) {
            throw new IllegalArgumentException("Contact.get called with null or empty number");
        }

        Contact contact = Cache.get(number);
        if (contact == null) {
            contact = new Contact(number);
            Cache.put(contact);
        }
        if (contact.mIsStale) {
            asyncUpdateContact(contact, canBlock);
        }
        return contact;
    }

    public static void invalidateCache() {
        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("invalidateCache");
        }

        // force invalidate the contact info cache, so we will query for fresh info again.
        // This is so we can get fresh presence info again on the screen, since the presence
        // info changes pretty quickly, and we can't get change notifications when presence is
        // updated in the ContactsProvider.
        ContactInfoCache.getInstance().invalidateCache();

        // While invalidating our local Cache doesn't remove the contacts, it will mark them
        // stale so the next time we're asked for a particular contact, we'll return that
        // stale contact and at the same time, fire off an asyncUpdateContact to update
        // that contact's info in the background. UI elements using the contact typically
        // call addListener() so they immediately get notified when the contact has been
        // updated with the latest info. They redraw themselves when we call the
        // listener's onUpdate().
        Cache.invalidate();
    }

    private static String emptyIfNull(String s) {
        return (s != null ? s : "");
    }

    private static boolean contactChanged(Contact orig, ContactInfoCache.CacheEntry newEntry) {
        // The phone number should never change, so don't bother checking.
        // TODO: Maybe update it if it has gotten longer, i.e. 650-234-5678 -> +16502345678?

        String oldName = emptyIfNull(orig.mName);
        String newName = emptyIfNull(newEntry.name);
        if (!oldName.equals(newName)) {
            if (V) Log.d(TAG, String.format("name changed: %s -> %s", oldName, newName));
            return true;
        }

        String oldLabel = emptyIfNull(orig.mLabel);
        String newLabel = emptyIfNull(newEntry.phoneLabel);
        if (!oldLabel.equals(newLabel)) {
            if (V) Log.d(TAG, String.format("label changed: %s -> %s", oldLabel, newLabel));
            return true;
        }

        if (orig.mPersonId != newEntry.person_id) {
            if (V) Log.d(TAG, "person id changed");
            return true;
        }

        if (orig.mPresenceResId != newEntry.presenceResId) {
            if (V) Log.d(TAG, "presence changed");
            return true;
        }

        return false;
    }

    /**
     * Handles the special case where the local ("Me") number is being looked up.
     * Updates the contact with the "me" name and returns true if it is the
     * local number, no-ops and returns false if it is not.
     */
    private static boolean handleLocalNumber(Contact c) {
        if (MessageUtils.isLocalNumber(c.mNumber)) {
            c.mName = Cache.getContext().getString(com.android.internal.R.string.me);
            c.updateNameAndNumber();
            return true;
        }
        return false;
    }

    private static void asyncUpdateContact(final Contact c, boolean canBlock) {
        if (c == null) {
            return;
        }

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("asyncUpdateContact for " + c.toString());
        }

        Runnable r = new Runnable() {
            public void run() {
                updateContact(c);
            }
        };

        if (canBlock) {
            r.run();
        } else {
            sTaskStack.push(r);
        }
    }

    private static void updateContact(final Contact c) {
        if (c == null) {
            return;
        }
        c.mIsStale = false;

        // Check to see if this is the local ("me") number.
        if (handleLocalNumber(c)) {
            return;
        }

        ContactInfoCache cache = ContactInfoCache.getInstance();
        ContactInfoCache.CacheEntry entry = cache.getContactInfo(c.mNumber);
        synchronized (Cache.getInstance()) {
            if (contactChanged(c, entry)) {
                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    log("updateContact: contact changed for " + entry.name);
                }

                //c.mNumber = entry.phoneNumber;
                c.mName = entry.name;
                c.updateNameAndNumber();
                c.mLabel = entry.phoneLabel;
                c.mPersonId = entry.person_id;
                c.mPresenceResId = entry.presenceResId;
                c.mPresenceText = entry.presenceText;
                c.mAvatar = entry.mAvatar;
                for (UpdateListener l : c.mListeners) {
                    if (V) Log.d(TAG, "updating " + l);
                    l.onUpdate(c);
                }
            }
        }
    }

    public static String formatNameAndNumber(String name, String number) {
        // Format like this: Mike Cleron <(650) 555-1234>
        //                   Erick Tseng <(650) 555-1212>
        //                   Tutankhamun <tutank1341@gmail.com>
        //                   (408) 555-1289
        String formattedNumber = number;
        if (!Mms.isEmailAddress(number)) {
            formattedNumber = PhoneNumberUtils.formatNumber(number);
        }

        if (!TextUtils.isEmpty(name) && !name.equals(number)) {
            return name + " <" + formattedNumber + ">";
        } else {
            return formattedNumber;
        }
    }

    public synchronized String getNumber() {
        return mNumber;
    }

    public synchronized void setNumber(String number) {
        mNumber = number;
        updateNameAndNumber();
        mNumberIsModified = true;
    }

    public boolean isNumberModified() {
        return mNumberIsModified;
    }

    public void setIsNumberModified(boolean flag) {
        mNumberIsModified = flag;
    }

    public synchronized String getName() {
        if (TextUtils.isEmpty(mName)) {
            return mNumber;
        } else {
            return mName;
        }
    }

    public synchronized String getNameAndNumber() {
        return mNameAndNumber;
    }

    private void updateNameAndNumber() {
        mNameAndNumber = formatNameAndNumber(mName, mNumber);
    }

    public synchronized long getRecipientId() {
        return mRecipientId;
    }

    public synchronized void setRecipientId(long id) {
        mRecipientId = id;
    }

    public synchronized String getLabel() {
        return mLabel;
    }

    public synchronized Uri getUri() {
        return ContentUris.withAppendedId(Contacts.CONTENT_URI, mPersonId);
    }

    public long getPersonId() {
        return mPersonId;
    }

    public synchronized int getPresenceResId() {
        return mPresenceResId;
    }

    public synchronized boolean existsInDatabase() {
        return (mPersonId > 0);
    }

    public synchronized void addListener(UpdateListener l) {
        boolean added = mListeners.add(l);
        if (V && added) dumpListeners();
    }

    public synchronized void removeListener(UpdateListener l) {
        boolean removed = mListeners.remove(l);
        if (V && removed) dumpListeners();
    }

    public synchronized void dumpListeners() {
        int i=0;
        Log.i(TAG, "[Contact] dumpListeners(" + mNumber + ") size=" + mListeners.size());
        for (UpdateListener listener : mListeners) {
            Log.i(TAG, "["+ (i++) + "]" + listener);
        }
    }

    public synchronized boolean isEmail() {
        return Mms.isEmailAddress(mNumber);
    }

    public String getPresenceText() {
        return mPresenceText;
    }

    public Drawable getAvatar(Drawable defaultValue) {
        return mAvatar != null ? mAvatar : defaultValue;
    }

    public static void init(final Context context) {
        Cache.init(context);
        RecipientIdCache.init(context);

        // it maybe too aggressive to listen for *any* contact changes, and rebuild MMS contact
        // cache each time that occurs. Unless we can get targeted updates for the contacts we
        // care about(which probably won't happen for a long time), we probably should just
        // invalidate cache peoridically, or surgically.
        /*
        context.getContentResolver().registerContentObserver(
                Contacts.CONTENT_URI, true, sContactsObserver);
        */
    }

    public static void dump() {
        Cache.dump();
    }

    public static void startPresenceObserver() {
        Cache.getContext().getContentResolver().registerContentObserver(
                Presence.CONTENT_URI, true, sPresenceObserver);
    }

    public static void stopPresenceObserver() {
        Cache.getContext().getContentResolver().unregisterContentObserver(sPresenceObserver);
    }

    private static class Cache {
        private static Cache sInstance;
        static Cache getInstance() { return sInstance; }
        private final List<Contact> mCache;
        private final Context mContext;
        private Cache(Context context) {
            mCache = new ArrayList<Contact>();
            mContext = context;
        }

        static void init(Context context) {
            sInstance = new Cache(context);
        }

        static Context getContext() {
            return sInstance.mContext;
        }

        static void dump() {
            synchronized (sInstance) {
                Log.d(TAG, "**** Contact cache dump ****");
                for (Contact c : sInstance.mCache) {
                    Log.d(TAG, c.toString());
                }
            }
        }

        private static Contact getEmail(String number) {
            synchronized (sInstance) {
                for (Contact c : sInstance.mCache) {
                    if (number.equalsIgnoreCase(c.mNumber)) {
                        return c;
                    }
                }
                return null;
            }
        }

        static Contact get(String number) {
            if (Mms.isEmailAddress(number))
                return getEmail(number);

            synchronized (sInstance) {
                for (Contact c : sInstance.mCache) {

                    // if the numbers are an exact match (i.e. Google SMS), or if the phone
                    // number comparison returns a match, return the contact.
                    if (number.equals(c.mNumber) || PhoneNumberUtils.compare(number, c.mNumber)) {
                        return c;
                    }
                }
                return null;
            }
        }

        static void put(Contact c) {
            synchronized (sInstance) {
                // We update cache entries in place so people with long-
                // held references get updated.
                if (get(c.mNumber) != null) {
                    throw new IllegalStateException("cache already contains " + c);
                }
                sInstance.mCache.add(c);
            }
        }

        static String[] getNumbers() {
            synchronized (sInstance) {
                String[] numbers = new String[sInstance.mCache.size()];
                int i = 0;
                for (Contact c : sInstance.mCache) {
                    numbers[i++] = c.getNumber();
                }
                return numbers;
            }
        }

        static List<Contact> getContacts() {
            synchronized (sInstance) {
                return new ArrayList<Contact>(sInstance.mCache);
            }
        }

        static void invalidate() {
            // Don't remove the contacts. Just mark them stale so we'll update their
            // info, particularly their presence.
            synchronized (sInstance) {
                for (Contact c : sInstance.mCache) {
                    c.mIsStale = true;
                }
            }
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
