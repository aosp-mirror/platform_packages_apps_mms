package com.android.mms.data;

import java.util.ArrayList;
import java.util.List;

import android.text.TextUtils;

import com.android.mms.data.Contact.UpdateListener;

public class ContactList extends ArrayList<Contact>  {
    private static final long serialVersionUID = 1L;

    public static ContactList getByNumbers(Iterable<String> numbers, boolean canBlock) {
        ContactList list = new ContactList();
        for (String number : numbers) {
            list.add(Contact.get(number, canBlock));
        }
        return list;
    }

    public static ContactList getByNumbers(String semiSepNumbers, boolean canBlock) {
        ContactList list = new ContactList();
        for (String number : semiSepNumbers.split(";")) {
            list.add(Contact.get(number, canBlock));
        }
        return list;
    }

    public static ContactList getByIds(String spaceSepIds, boolean canBlock) {
        ContactList list = new ContactList();
        for (String number : RecipientIdCache.getNumbers(spaceSepIds)) {
            Contact contact = Contact.get(number, canBlock);
            list.add(contact);
        }
        return list;
    }

    public int getPresenceResId() {
        // We only show presence for single contacts.
        if (size() != 1)
            return 0;

        return get(0).getPresenceResId();
    }

    public void addListeners(UpdateListener l) {
        for (Contact c : this) {
            c.addListener(l);
        }
    }

    public void removeListeners(UpdateListener l) {
        for (Contact c : this) {
            c.removeListener(l);
        }
    }

    public String formatNames(String separator) {
        String[] names = new String[size()];
        int i = 0;
        for (Contact c : this) {
            names[i++] = c.getName();
        }
        return TextUtils.join(separator, names);
    }

    public String formatNamesAndNumbers(String separator) {
        String[] nans = new String[size()];
        int i = 0;
        for (Contact c : this) {
            nans[i++] = c.getNameAndNumber();
        }
        return TextUtils.join(separator, nans);
    }

    public String serialize() {
        return TextUtils.join(";", getNumbers());
    }

    public boolean containsEmail() {
        for (Contact c : this) {
            if (c.isEmail()) {
                return true;
            }
        }
        return false;
    }

    public String[] getNumbers() {
        List<String> numbers = new ArrayList<String>();
        for (Contact c : this) {
            numbers.add(c.getNumber());
        }
        return numbers.toArray(new String[numbers.size()]);
    }

    @Override
    public boolean equals(Object obj) {
        try {
            ContactList other = (ContactList)obj;
            // If they're different sizes, the contact
            // set is obviously different.
            if (size() != other.size()) {
                return false;
            }

            // Make sure all the individual contacts are the same.
            for (Contact c : this) {
                if (!other.contains(c)) {
                    return false;
                }
            }

            return true;
        } catch (ClassCastException e) {
            return false;
        }
    }
}
