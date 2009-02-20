/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.mms.util;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;

import com.android.mms.R;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for annotating a CharSequence with spans to convert textual emoticons
 * to graphical ones.
 */
public class SmileyParser {
    // Singleton stuff
    private static SmileyParser sInstance;
    public static SmileyParser getInstance() { return sInstance; }
    public static void init(Context context) {
        sInstance = new SmileyParser(context);
    }
    
    private final Context mContext;
    private final String[] mSmileyTexts;
    private final Pattern mPattern;
    private final HashMap<String, Integer> mSmileyToRes;
 
    private SmileyParser(Context context) {
        mContext = context;
        mSmileyTexts = mContext.getResources().getStringArray(DEFAULT_SMILEY_TEXTS);
        mSmileyToRes = buildSmileyToRes();
        mPattern = buildPattern();
    }
    
    // NOTE: if you change anything about this array, you must make the corresponding change
    // to the string arrays: default_smiley_texts and default_smiley_names in res/values/arrays.xml
    public static final int[] DEFAULT_SMILEY_RES_IDS = {
        android.R.drawable.emo_im_happy,                //  0
        android.R.drawable.emo_im_sad,                  //  1
        android.R.drawable.emo_im_winking,              //  2
        android.R.drawable.emo_im_tongue_sticking_out,  //  3
        android.R.drawable.emo_im_surprised,            //  4
        android.R.drawable.emo_im_kissing,              //  5
        android.R.drawable.emo_im_yelling,              //  6
        android.R.drawable.emo_im_cool,                 //  7
        android.R.drawable.emo_im_money_mouth,          //  8
        android.R.drawable.emo_im_foot_in_mouth,        //  9
        android.R.drawable.emo_im_embarrassed,          //  10
        android.R.drawable.emo_im_angel,                //  11
        android.R.drawable.emo_im_undecided,            //  12
        android.R.drawable.emo_im_crying,               //  13
        android.R.drawable.emo_im_lips_are_sealed,      //  14
        android.R.drawable.emo_im_laughing,             //  15
        android.R.drawable.emo_im_wtf                   //  16
    };
    
    public static final int DEFAULT_SMILEY_TEXTS = R.array.default_smiley_texts;
    public static final int DEFAULT_SMILEY_NAMES = R.array.default_smiley_names;

    /**
     * Builds the hashtable we use for mapping the string version
     * of a smiley (e.g. ":-)") to a resource ID for the icon version.
     */
    private HashMap<String, Integer> buildSmileyToRes() {
        if (DEFAULT_SMILEY_RES_IDS.length != mSmileyTexts.length) {
            // Throw an exception if someone updated DEFAULT_SMILEY_RES_IDS
            // and failed to update arrays.xml
            throw new IllegalStateException("Smiley resource ID/text mismatch");
        }

        HashMap<String, Integer> smileyToRes =
                            new HashMap<String, Integer>(mSmileyTexts.length);
        for (int i = 0; i < mSmileyTexts.length; i++) {
            smileyToRes.put(mSmileyTexts[i], DEFAULT_SMILEY_RES_IDS[i]);
        }
        
        return smileyToRes;
    }
    
    /**
     * Builds the regular expression we use to find smileys in {@link #addSmileySpans}.
     */
    private Pattern buildPattern() {
        // Set the StringBuilder capacity with the assumption that the average
        // smiley is 3 characters long.
        StringBuilder patternString = new StringBuilder(mSmileyTexts.length * 3);

        // Build a regex that looks like (:-)|:-(|...), but escaping the smilies
        // properly so they will be interpreted literally by the regex matcher.
        patternString.append('(');
        for (String s : mSmileyTexts) {
            patternString.append(Pattern.quote(s));
            patternString.append('|');
        }
        // Replace the extra '|' with a ')'
        patternString.replace(patternString.length() - 1, patternString.length(), ")");
        
        return Pattern.compile(patternString.toString());
    }


    /**
     * Adds ImageSpans to a CharSequence that replace textual emoticons such
     * as :-) with a graphical version.
     * 
     * @param text A CharSequence possibly containing emoticons
     * @return A CharSequence annotated with ImageSpans covering any
     *         recognized emoticons.
     */
    public CharSequence addSmileySpans(CharSequence text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        Matcher matcher = mPattern.matcher(text);
        while (matcher.find()) {
            int resId = mSmileyToRes.get(matcher.group());
            builder.setSpan(new ImageSpan(mContext, resId),
                            matcher.start(), matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return builder;
    }
}


