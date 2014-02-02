package com.android.mms.transaction;

import java.io.IOException;

import com.google.android.mms.ContentType;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.RetrieveConf;

import android.util.Log;

public class ConvUtils {
    private static final String TAG = "ConvUtils";
    private static final boolean LOCAL_LOGV = false;

    private static int toUnicodeSoftBankGlyph(String glyphStr) throws java.io.UnsupportedEncodingException {
        byte[] byteArray = glyphStr.getBytes("Windows-31J");
        if (byteArray.length < 2) return -1;
        int firstByte = byteArray[0] & 0xFF;
        int secondByte = byteArray[1] & 0xFF;
        int base;
        if (firstByte == 0xF7) {
            if (secondByte < 0xA0) {
                base = 0xE100;
            } else {
                base = 0xE200;
            }
        } else if (firstByte == 0xF9) {
            if (secondByte < 0xA0) {
                base = 0xE000;
            } else {
                base = 0xE300;
            }
        } else if (firstByte == 0xFB) {
            if (secondByte < 0xA0) {
                base = 0xE400;
            } else {
                base = 0xE500;
            }
        } else {
            return -1;
        }

        int uniNum;
        if (secondByte < 0x80) {
            uniNum = base + (secondByte - 0x40);
        } else if (secondByte > 0xA0) {
            uniNum = base + (secondByte - 0xA0);
        } else {
            uniNum = base + (secondByte - 0x41);
        }
        return uniNum;
    }

    public static void convPduBody(GenericPdu pdu) throws java.io.UnsupportedEncodingException {

        if (!(pdu instanceof RetrieveConf)) return;

        PduBody body = ((RetrieveConf) pdu).getBody();
        if (body != null) {
            int partsNum = body.getPartsNum();
            for (int i = 0; i < partsNum; i++) {
                PduPart part = body.getPart(i);
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Content-Type: " + new String(part.getContentType()) + "; Charset: " + part.getCharset());
                }
                if (part.getCharset() == CharacterSets.SHIFT_JIS ||
                        (part.getCharset() == 0 && ContentType.TEXT_HTML.equals(new String(part.getContentType())))) {
                    String text;
                    StringBuilder sb = new StringBuilder();
                    text = new String(part.getData(),
                        CharacterSets.getMimeName(CharacterSets.SHIFT_JIS));
                    for (String s : text.split("")) {
                        int cp = toUnicodeSoftBankGlyph(s);
                        if (cp > 0) sb.appendCodePoint(cp);
                        else sb.append(s);
                    }
                    part.setData(sb.toString().getBytes());
                    part.setCharset(CharacterSets.UTF_8);
                } else if (part.getCharset() == 39) {
                    String text = new String(part.getData(), "iso-2022-jp");
                    part.setData(text.getBytes());
                    part.setCharset(CharacterSets.UTF_8);
                } else if (ContentType.TEXT_HTML.equals(new String(part.getContentType())) &&
                        part.getCharset() != CharacterSets.UTF_8) {
                    String text = new String(part.getData(),
                        CharacterSets.getMimeName(part.getCharset()));
                    part.setData(text.getBytes());
                    part.setCharset(CharacterSets.UTF_8);
                }
            }
        }
    }
}
