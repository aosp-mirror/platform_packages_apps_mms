package com.android.mms.model;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.android.vcard.VCardConfig;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryConstructor;
import com.android.vcard.VCardEntryHandler;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;

public class VcardUtil {

    private static final String TAG = "VcardUtil";

    private static final String VERSION_INDEX_2 = "2.1";
    private static final String VERSION_INDEX_3 = "3.0";
    public static final String VCARD_READ_ONLY = "vcard_read_Only";
    public static final String VCARD_ATT_NAME = "EXTRA_ATTACHMENT_SHARE_NAME";
    public static final String SAVE_TO_CONTACTS = "save_to_contacts";
    public static final String VCARD_PATH = "/sdcard/.blur/vcard/";
    public static final String VCARD_MIME_TYPE = "text/x-vcard";

    public static final int VERSION_VCARD21_INT = 1;
    public static final int VERSION_VCARD30_INT = 2;
    public static final int VERSION_VCARD_ERROR = -1;

    /**
     * Returns vcard name as ContactName.vcf
     */
    public static String getVcardName(Context context, Uri uri) {

        VCardEntry contactstruct = parseVCard(context, uri);
        if (contactstruct == null) {
            return null;
        }
        return contactstruct.getDisplayName() + ".vcf";
    }

    public static class EntryImplementer implements VCardEntryHandler {

        private VCardEntry mContactStruct;

        public void onEntryCreated(final VCardEntry contactStruct) {
            mContactStruct = contactStruct;
        }

        public void onStart() {
        }

        public void onEnd() {
        }
    }

    public static int judgeVersion(Context context, Uri uri) {
        InputStream input = null;
        try {
            //TODO : need to improve this function
            if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)){
                input = context.getContentResolver().openInputStream(uri);
            } else {
                input = new FileInputStream(uri.getPath());
            }

            byte[] data = new byte[input.available()];
            input.read(data);
            input.close();

            if (data == null) {
                return VERSION_VCARD_ERROR;
            }

            String vcardStr = new String(data);
            int verIndex = vcardStr.indexOf("\nVERSION:");

            if (verIndex == -1) {
                return VERSION_VCARD_ERROR;
            }

            String verStr = vcardStr.substring(verIndex, vcardStr.indexOf("\n", verIndex + 1));

            if (verStr.indexOf(VERSION_INDEX_2) > 0) {
                return VERSION_VCARD21_INT;
            } else if (verStr.indexOf(VERSION_INDEX_3) > 0) {
                return VERSION_VCARD30_INT;
            } else {
                return VERSION_VCARD_ERROR;
            }

        } catch (IOException e) {
            if (input != null)
                try {
                    input.close();
                } catch (IOException e1) {
                }
            Log.w(TAG, "Exception while computing version", e);
            return VERSION_VCARD_ERROR;
        }
    }

    public static VCardEntry parseVCard(Context context, Uri uri) {
        InputStream input = null;

        try {
            Log.v(TAG, "parseVCard, uri:" + uri);

            int version = judgeVersion(context, uri);
            VCardParser mVCardParser;
            VCardEntryConstructor builder;

            if (version == VERSION_VCARD30_INT) {
                mVCardParser = new VCardParser_V30();
                builder = new VCardEntryConstructor(VCardConfig.VCARD_TYPE_V30_GENERIC);
            } else if (version == VERSION_VCARD21_INT) {
                mVCardParser = new VCardParser_V21();
                builder = new VCardEntryConstructor(VCardConfig.VCARD_TYPE_V21_GENERIC);
            } else {
                Log.w(TAG, " Error while identifying version:" + version);
                return null;
            }

            EntryImplementer eImplementer = new EntryImplementer();
            builder.addEntryHandler(eImplementer);

            if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                input = context.getContentResolver().openInputStream(uri);
            } else {
                input = new FileInputStream(uri.getPath());
            }

            mVCardParser.parse(input, builder);

            Log.w(TAG, "value of Contact Struct" + eImplementer.mContactStruct);
            return eImplementer.mContactStruct;
        } catch (IOException e) {
            Log.w(TAG, "exception in parseVCard - IO", e);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "exception in parseVCard - generic", e);
            return null;
        } finally {
            closeQuietly(input);
        }
    }

    /**
     * Closes the given InputStream without throwing any exceptions.
     * @param is The InputStream to close.
     */
    private static void closeQuietly(InputStream is) {
        try {
            if (is != null) {
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
