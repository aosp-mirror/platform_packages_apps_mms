package com.android.mms.model;

import java.lang.ref.SoftReference;
import java.util.List;

import org.w3c.dom.events.Event;

import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntry.EmailData;
import com.android.vcard.VCardEntry.GeoData;
import com.android.vcard.VCardEntry.ImData;
import com.android.vcard.VCardEntry.NicknameData;
import com.android.vcard.VCardEntry.NoteData;
import com.android.vcard.VCardEntry.OrganizationData;
import com.android.vcard.VCardEntry.PhoneData;
import com.android.vcard.VCardEntry.PostalData;
import com.android.vcard.VCardEntry.WebsiteData;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcel;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.mms.ContentRestrictionException;
import com.android.mms.R;
import com.android.mms.ui.MessageUtils;
import com.android.mms.UnsupportContentTypeException;

/**
 * 1 create Vcard Model
 * 2 retrieve all Vcard information
 * like phone Detail, Contact information,
 * Company name etc.
 */

public final class VcardModel extends MediaModel {
    private static final String TAG = "IcalModel";
    private static final boolean DEBUG = true;
    private static final boolean LOCAL_LOG = DEBUG;

    private static final String VCARD_EXTENSION = ".vcf";
    private VCardEntry mVCardEntry;

    /**
     * Constructor used when adding media from within the application (MO case)
     *
     * @param context
     *            - The context
     * @param creationMode
     *            - creation mode for this model
     * @param uri
     *            - Uri to construct this model from.
     * @param src
     *            - Source for this model.
     * @throws MmsException
     * @throws ContentRestrictionException
     * @throws UnsupportContentTypeException
     */
    public VcardModel(Context context, Uri uri) throws MmsException {
        super(context, ContentType.TEXT_LOW_VCARD, SmilHelper.ELEMENT_TAG_REF, uri);
        initializeFromUri(uri);
        mMediaData = getContactDetails();
    }

    protected String getNameForUri(Uri uri) {
        if (mVCardEntry == null){
            mVCardEntry = VcardUtil.parseVCard(mContext, uri);
        }
        //parseVCard can return null when any exception is encountered,
        //so need to have a null check for mVCardEntry object
        return ensurePrefix(mVCardEntry == null ? " " :
                mVCardEntry.getDisplayName());
    }

    protected String ensurePrefix(String source) {
        if (!source.endsWith(VCARD_EXTENSION)){
            source = source + VCARD_EXTENSION;
        }
        return source;
    }

    /**
     * Parses an Uri and, if it points to a vCard, retrieves the vcard name.
     * If the Uri does not point to a vCard, null is returned.
     * @param context
     * @param uri - The Uri to check
     * @return The vCard name or null
     */
    public static String parseNameFromUri(Context context, Uri uri) {
        return VcardUtil.getVcardName(context, uri);
    }

    public Bitmap getBitmap(int widthLimit, int heightLimit) {
        Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.ic_contact_picture);

        if (widthLimit == 0 || heightLimit == 0) {
            widthLimit = bitmap.getWidth();
            heightLimit = bitmap.getHeight();
        }
        Bitmap useThisBitmap = Bitmap.createScaledBitmap(bitmap, widthLimit, heightLimit, true);

        return useThisBitmap;
    }

    protected void initializeFromUri(Uri uri) throws MmsException {
        if (LOCAL_LOG) {
            Log.v(TAG, "initializeFromUri, uri=" + uri + ", mContentType=" + mContentType +
                            ", mSrc=" + mSrc);
        }

        String path = getNameForUri(uri);

        if (path == null) {
            return;
        }
        mSrc = getNameFromPath(path, mContentType);

         if (TextUtils.isEmpty(mSrc)) {
            throw new MmsException("Media source name is unknown.");
        }

    }

    private String getNameFromPath(String path, String mContentType) {

        String name = path.substring(path.lastIndexOf('/') + 1);


        name = fixFileExtension(name, mContentType);

        // Some MMSCs appear to have problems with filenames
        // containing a space. So just replace them with
        // underscores in the name, which is typically not
        // visible to the user anyway.
        name = name.replace(' ', '_');

        // Avoid multiple dots, as some phones can't handle it.
        if (name.indexOf('.') != name.lastIndexOf('.')) {
            name = name.substring(0, name.lastIndexOf('.') + 1).replace('.', '_') + '.'
                    + name.substring(name.lastIndexOf('.') + 1);
        }

        return name;
    }

    private final String fixFileExtension(String fileName, String type) {
        if (fileName == null || type == null) {
            Log.w(TAG, "fixFileExtension - fileName=" + fileName);
            return fileName;
        }

        int index = fileName.indexOf(".");
        if (index == -1) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            // If the file extension is empty, then leave it to the SdCardWriter
            // to determine the appropriate file extension.
            if (!TextUtils.isEmpty(extension)) {
                if (LOCAL_LOG) {
                    Log.v(TAG, "fixFileExtension - fileName=" + fileName + "." + extension);
                }

                return (fileName + "." + extension);
            }
        }

        return fileName;
    }

    public void handleEvent(Event evt) {
    }


    public String getContactDetails() {
        final String carriageReturn = "\r\n";

        if (mVCardEntry == null) {
            return null;
        }

        StringBuffer res = new StringBuffer();
        // Contact Name
        String displayName = mVCardEntry.getDisplayName();
        if (displayName != null) {
            res.append(displayName);
            res.append(carriageReturn);
        }

        // Contact Organization Detials
        List<OrganizationData> orgData = mVCardEntry.getOrganizationList();
        if (orgData != null) {
            for (int i = 0; i < orgData.size(); i++) {
                OrganizationData data = orgData.get(i);
                String company = data.getOrganizationName();
                String title = data.getTitle();
                String result = null;
                if (!TextUtils.isEmpty(company) && !TextUtils.isEmpty(title)) {
                    result = mContext.getString(
                            R.string.organization_company_and_title, company,
                            title);
                } else if (!TextUtils.isEmpty(company)) {
                    result = company;
                } else if (!TextUtils.isEmpty(title)) {
                    result = title;
                }
                if (!TextUtils.isEmpty(result)) {
                    res.append(result);
                    res.append(carriageReturn);
                }
            }
        }

        // Contact Phone Details
        List<PhoneData> phoneData = mVCardEntry.getPhoneList();
        if (phoneData != null) {
            for (int i = 0; i < phoneData.size(); i++) {
                PhoneData data = phoneData.get(i);
                if (!data.isEmpty()) {
                    res.append(mContext.getString(
                            R.string.phone_type_number,
                            mContext.getResources().getString(
                            Phone.getTypeLabelResource(data.getType())),
                            data.getNumber()));
                    res.append(carriageReturn);
                }
            }
        }

        // Contact Email Details
        List<EmailData> emailData = mVCardEntry.getEmailList();
        if (emailData != null) {
            for (int i = 0; i < emailData.size(); i++) {
                EmailData data = emailData.get(i);
                if (!data.isEmpty()) {
                    res.append(mContext.getString(
                            R.string.email_type_number,
                            mContext.getResources().getString(
                            Email.getTypeLabelResource(data.getType())),
                            data.getAddress()));
                    res.append(carriageReturn);
                }
            }
        }

        // Get IM Details
        List<ImData> imData = mVCardEntry.getImList();
        if (imData != null) {
            for (int i = 0; i < imData.size(); i++) {
                ImData data = imData.get(i);
                if (!data.isEmpty()) {
                    res.append(mContext.getString(
                            R.string.im_type_number,
                            mContext.getResources().getString(
                            Im.getProtocolLabelResource(data.getProtocol())),
                            data.getAddress()));
                    res.append(carriageReturn);
                }
            }
        }

        // Contact NickName
        List<NicknameData> nickNameData = mVCardEntry.getNickNameList();
        if (nickNameData != null) {
            for (int i = 0; i < nickNameData.size(); i++) {
                NicknameData data = nickNameData.get(i);
                if (!data.isEmpty()) {
                    if (i == 0) {
                        res.append(mContext.getString(R.string.nickname_type));
                    }
                    res.append(data.getNickname());
                    res.append(carriageReturn);
                }
            }
        }

        // Contact Website Details
        List<WebsiteData> websiteData = mVCardEntry.getWebsiteList();
        if (websiteData != null) {
            for (int i = 0; i < websiteData.size(); i++) {
                WebsiteData data = websiteData.get(i);
                if (!data.isEmpty()) {
                    if (i == 0) {
                        res.append(mContext.getString(R.string.website_type));
                    }
                    res.append(data.getWebsite());
                    res.append(carriageReturn);
                }
            }
        }

        // Get Postal Data
        List<PostalData> postalData = mVCardEntry.getPostalList();
        if (postalData != null) {
            for (int i = 0; i < postalData.size(); i++) {
                PostalData data = postalData.get(i);
                if (!TextUtils.isEmpty(data.getStreet())
                        || !TextUtils.isEmpty(data.getLocalty())
                        || !TextUtils.isEmpty(data.getRegion())) {
                    StringBuffer address = new StringBuffer();
                    if (!TextUtils.isEmpty(data.getStreet())) {
                        address.append(data.getStreet());
                        address.append(" ");
                    }
                    if (!TextUtils.isEmpty(data.getLocalty())) {
                        address.append(data.getLocalty());
                        address.append(",");
                        address.append(" ");
                    }
                    if (!TextUtils.isEmpty(data.getRegion())) {
                        address.append(data.getRegion());
                        address.append(" ");
                    }
                    if (!TextUtils.isEmpty(data.getPostalCode())) {
                        address.append(data.getPostalCode());
                        address.append(" ");
                    }
                    if (!TextUtils.isEmpty(data.getCountry()))
                        address.append(data.getCountry());

                    res.append(mContext.getString(
                            R.string.address_type_number,
                            mContext.getResources().getString(
                                    StructuredPostal.getTypeLabelResource(data
                                            .getType())), address.toString()));
                    res.append(carriageReturn);
                }
            }
        }

        // Contact BirthDay
        String birthday = mVCardEntry.getBirthday();
        if (!TextUtils.isEmpty(birthday)) {
            res.append(mContext.getString(R.string.event_type));
            res.append(birthday);
            res.append(carriageReturn);
        }

        // Contact Notes Details
        List<NoteData> notesData = mVCardEntry.getNotes();
        if (notesData != null) {
            for (int i = 0; i < notesData.size(); i++) {
                NoteData data = notesData.get(i);
                if (!data.isEmpty()) {
                    if (i == 0) {
                        res.append(mContext.getString(R.string.note_type));
                    }
                    res.append(data.getNote());
                    res.append(carriageReturn);
                }
            }
        }

        return res.toString();
    }

    public boolean hasLocationData() {
        GeoData geoData = null;
        if (mVCardEntry != null) {
            geoData = mVCardEntry.getGeoData();
        }
        if (geoData!=null && !geoData.isEmpty()) {
            return true;
        }
        return false;
    }
}
