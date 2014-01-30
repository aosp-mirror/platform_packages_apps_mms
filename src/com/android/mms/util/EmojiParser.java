/*
 * Copyright (C) 2012 The CyanogenMod project
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
 * A class for annotating a CharSequence with spans to convert textual Softbank
 * and Unicode emojis to graphical ones. The full Unicode proposal is available
 * here:
 * {@link "http://www.unicode.org/~scherer/emoji4unicode/snapshot/full.html"}
 */
public class EmojiParser {
    // Singleton stuff
    private static EmojiParser sInstance;

    public static EmojiParser getInstance() {
        return sInstance;
    }

    public static void init(Context context) {
        sInstance = new EmojiParser(context);
    }

    private final Context mContext;
    private final Pattern mPattern;
    private final HashMap<String, Integer> mSmileyToRes;

    private EmojiParser(Context context) {
        mContext = context;
        mSmileyToRes = buildSmileyToRes();
        mPattern = buildPattern();
    }

    static class Emojis {
        private static final int[] sIconIds = {
                R.drawable.emoji_e415, R.drawable.emoji_e056, R.drawable.emoji_e057,
                R.drawable.emoji_e414, R.drawable.emoji_e405, R.drawable.emoji_e106,
                R.drawable.emoji_e418, R.drawable.emoji_e417, R.drawable.emoji_e40d,
                R.drawable.emoji_e40a, R.drawable.emoji_e404, R.drawable.emoji_e105,
                R.drawable.emoji_e409, R.drawable.emoji_e40e, R.drawable.emoji_e402,
                R.drawable.emoji_e108, R.drawable.emoji_e403, R.drawable.emoji_e058,
                R.drawable.emoji_e407, R.drawable.emoji_e401, R.drawable.emoji_e40f,
                R.drawable.emoji_e40b, R.drawable.emoji_e406, R.drawable.emoji_e413,
                R.drawable.emoji_e411, R.drawable.emoji_e412, R.drawable.emoji_e410,
                R.drawable.emoji_e107, R.drawable.emoji_e059, R.drawable.emoji_e416,
                R.drawable.emoji_e408, R.drawable.emoji_e40c, R.drawable.emoji_e11a,
                R.drawable.emoji_e10c, R.drawable.emoji_e32c, R.drawable.emoji_e32a,
                R.drawable.emoji_e32d, R.drawable.emoji_e328, R.drawable.emoji_e32b,
                R.drawable.emoji_e022, R.drawable.emoji_e023, R.drawable.emoji_e327,
                R.drawable.emoji_e329, R.drawable.emoji_e32e, R.drawable.emoji_e32f,
                R.drawable.emoji_e335, R.drawable.emoji_e334, R.drawable.emoji_e021,
                R.drawable.emoji_e337, R.drawable.emoji_e020, R.drawable.emoji_e336,
                R.drawable.emoji_e13c, R.drawable.emoji_e330, R.drawable.emoji_e331,
                R.drawable.emoji_e326, R.drawable.emoji_e03e, R.drawable.emoji_e11d,
                R.drawable.emoji_e05a, R.drawable.emoji_e00e, R.drawable.emoji_e421,
                R.drawable.emoji_e420, R.drawable.emoji_e00d, R.drawable.emoji_e010,
                R.drawable.emoji_e011, R.drawable.emoji_e41e, R.drawable.emoji_e012,
                R.drawable.emoji_e422, R.drawable.emoji_e22e, R.drawable.emoji_e22f,
                R.drawable.emoji_e231, R.drawable.emoji_e230, R.drawable.emoji_e427,
                R.drawable.emoji_e41d, R.drawable.emoji_e00f, R.drawable.emoji_e41f,
                R.drawable.emoji_e14c, R.drawable.emoji_e201, R.drawable.emoji_e115,
                R.drawable.emoji_e428, R.drawable.emoji_e51f, R.drawable.emoji_e429,
                R.drawable.emoji_e424, R.drawable.emoji_e423, R.drawable.emoji_e253,
                R.drawable.emoji_e426, R.drawable.emoji_e111, R.drawable.emoji_e425,
                R.drawable.emoji_e31e, R.drawable.emoji_e31f, R.drawable.emoji_e31d,
                R.drawable.emoji_e001, R.drawable.emoji_e002, R.drawable.emoji_e005,
                R.drawable.emoji_e004, R.drawable.emoji_e51a, R.drawable.emoji_e519,
                R.drawable.emoji_e518, R.drawable.emoji_e515, R.drawable.emoji_e516,
                R.drawable.emoji_e517, R.drawable.emoji_e51b, R.drawable.emoji_e152,
                R.drawable.emoji_e04e, R.drawable.emoji_e51c, R.drawable.emoji_e51e,
                R.drawable.emoji_e11c, R.drawable.emoji_e536, R.drawable.emoji_e003,
                R.drawable.emoji_e41c, R.drawable.emoji_e41b, R.drawable.emoji_e419,
                R.drawable.emoji_e41a, R.drawable.emoji_e04a, R.drawable.emoji_e04b,
                R.drawable.emoji_e049, R.drawable.emoji_e048, R.drawable.emoji_e04c,
                R.drawable.emoji_e13d, R.drawable.emoji_e443, R.drawable.emoji_e43e,
                R.drawable.emoji_e04f, R.drawable.emoji_e052, R.drawable.emoji_e053,
                R.drawable.emoji_e524, R.drawable.emoji_e52c, R.drawable.emoji_e52a,
                R.drawable.emoji_e531, R.drawable.emoji_e050, R.drawable.emoji_e527,
                R.drawable.emoji_e051, R.drawable.emoji_e10b, R.drawable.emoji_e52b,
                R.drawable.emoji_e52f, R.drawable.emoji_e528, R.drawable.emoji_e01a,
                R.drawable.emoji_e134, R.drawable.emoji_e530, R.drawable.emoji_e529,
                R.drawable.emoji_e526, R.drawable.emoji_e52d, R.drawable.emoji_e521,
                R.drawable.emoji_e523, R.drawable.emoji_e52e, R.drawable.emoji_e055,
                R.drawable.emoji_e525, R.drawable.emoji_e10a, R.drawable.emoji_e109,
                R.drawable.emoji_e522, R.drawable.emoji_e019, R.drawable.emoji_e054,
                R.drawable.emoji_e520, R.drawable.emoji_e306, R.drawable.emoji_e030,
                R.drawable.emoji_e304, R.drawable.emoji_e110, R.drawable.emoji_e032,
                R.drawable.emoji_e305, R.drawable.emoji_e303, R.drawable.emoji_e118,
                R.drawable.emoji_e447, R.drawable.emoji_e119, R.drawable.emoji_e307,
                R.drawable.emoji_e308, R.drawable.emoji_e444, R.drawable.emoji_e441,
                R.drawable.emoji_e436, R.drawable.emoji_e437, R.drawable.emoji_e438,
                R.drawable.emoji_e43a, R.drawable.emoji_e439, R.drawable.emoji_e43b,
                R.drawable.emoji_e117, R.drawable.emoji_e440, R.drawable.emoji_e442,
                R.drawable.emoji_e446, R.drawable.emoji_e445, R.drawable.emoji_e11b,
                R.drawable.emoji_e448, R.drawable.emoji_e033, R.drawable.emoji_e112,
                R.drawable.emoji_e325, R.drawable.emoji_e312, R.drawable.emoji_e310,
                R.drawable.emoji_e126, R.drawable.emoji_e127, R.drawable.emoji_e008,
                R.drawable.emoji_e03d, R.drawable.emoji_e00c, R.drawable.emoji_e12a,
                R.drawable.emoji_e00a, R.drawable.emoji_e00b, R.drawable.emoji_e009,
                R.drawable.emoji_e316, R.drawable.emoji_e129, R.drawable.emoji_e141,
                R.drawable.emoji_e142, R.drawable.emoji_e317, R.drawable.emoji_e128,
                R.drawable.emoji_e14b, R.drawable.emoji_e211, R.drawable.emoji_e114,
                R.drawable.emoji_e145, R.drawable.emoji_e144, R.drawable.emoji_e03f,
                R.drawable.emoji_e313, R.drawable.emoji_e116, R.drawable.emoji_e10f,
                R.drawable.emoji_e104, R.drawable.emoji_e103, R.drawable.emoji_e101,
                R.drawable.emoji_e102, R.drawable.emoji_e13f, R.drawable.emoji_e140,
                R.drawable.emoji_e11f, R.drawable.emoji_e12f, R.drawable.emoji_e031,
                R.drawable.emoji_e30e, R.drawable.emoji_e311, R.drawable.emoji_e113,
                R.drawable.emoji_e30f, R.drawable.emoji_e13b, R.drawable.emoji_e42b,
                R.drawable.emoji_e42a, R.drawable.emoji_e018, R.drawable.emoji_e016,
                R.drawable.emoji_e015, R.drawable.emoji_e014, R.drawable.emoji_e42c,
                R.drawable.emoji_e42d, R.drawable.emoji_e017, R.drawable.emoji_e013,
                R.drawable.emoji_e20e, R.drawable.emoji_e20c, R.drawable.emoji_e20f,
                R.drawable.emoji_e20d, R.drawable.emoji_e131, R.drawable.emoji_e12b,
                R.drawable.emoji_e130, R.drawable.emoji_e12d, R.drawable.emoji_e324,
                R.drawable.emoji_e301, R.drawable.emoji_e148, R.drawable.emoji_e502,
                R.drawable.emoji_e03c, R.drawable.emoji_e30a, R.drawable.emoji_e042,
                R.drawable.emoji_e040, R.drawable.emoji_e041, R.drawable.emoji_e12c,
                R.drawable.emoji_e007, R.drawable.emoji_e31a, R.drawable.emoji_e13e,
                R.drawable.emoji_e31b, R.drawable.emoji_e006, R.drawable.emoji_e302,
                R.drawable.emoji_e319, R.drawable.emoji_e321, R.drawable.emoji_e322,
                R.drawable.emoji_e314, R.drawable.emoji_e503, R.drawable.emoji_e10e,
                R.drawable.emoji_e318, R.drawable.emoji_e43c, R.drawable.emoji_e11e,
                R.drawable.emoji_e323, R.drawable.emoji_e31c, R.drawable.emoji_e034,
                R.drawable.emoji_e035, R.drawable.emoji_e045, R.drawable.emoji_e338,
                R.drawable.emoji_e047, R.drawable.emoji_e30c, R.drawable.emoji_e044,
                R.drawable.emoji_e30b, R.drawable.emoji_e043, R.drawable.emoji_e120,
                R.drawable.emoji_e33b, R.drawable.emoji_e33f, R.drawable.emoji_e341,
                R.drawable.emoji_e34c, R.drawable.emoji_e344, R.drawable.emoji_e342,
                R.drawable.emoji_e33d, R.drawable.emoji_e33e, R.drawable.emoji_e340,
                R.drawable.emoji_e34d, R.drawable.emoji_e339, R.drawable.emoji_e147,
                R.drawable.emoji_e343, R.drawable.emoji_e33c, R.drawable.emoji_e33a,
                R.drawable.emoji_e43f, R.drawable.emoji_e34b, R.drawable.emoji_e046,
                R.drawable.emoji_e345, R.drawable.emoji_e346, R.drawable.emoji_e348,
                R.drawable.emoji_e347, R.drawable.emoji_e34a, R.drawable.emoji_e349,
                R.drawable.emoji_e036, R.drawable.emoji_e157, R.drawable.emoji_e038,
                R.drawable.emoji_e153, R.drawable.emoji_e155, R.drawable.emoji_e14d,
                R.drawable.emoji_e156, R.drawable.emoji_e501, R.drawable.emoji_e158,
                R.drawable.emoji_e43d, R.drawable.emoji_e037, R.drawable.emoji_e504,
                R.drawable.emoji_e44a, R.drawable.emoji_e146, R.drawable.emoji_e50a,
                R.drawable.emoji_e505, R.drawable.emoji_e506, R.drawable.emoji_e122,
                R.drawable.emoji_e508, R.drawable.emoji_e509, R.drawable.emoji_e03b,
                R.drawable.emoji_e04d, R.drawable.emoji_e449, R.drawable.emoji_e44b,
                R.drawable.emoji_e51d, R.drawable.emoji_e44c, R.drawable.emoji_e124,
                R.drawable.emoji_e121, R.drawable.emoji_e433, R.drawable.emoji_e202,
                R.drawable.emoji_e135, R.drawable.emoji_e01c, R.drawable.emoji_e01d,
                R.drawable.emoji_e10d, R.drawable.emoji_e136, R.drawable.emoji_e42e,
                R.drawable.emoji_e01b, R.drawable.emoji_e15a, R.drawable.emoji_e159,
                R.drawable.emoji_e432, R.drawable.emoji_e430, R.drawable.emoji_e431,
                R.drawable.emoji_e42f, R.drawable.emoji_e01e, R.drawable.emoji_e039,
                R.drawable.emoji_e435, R.drawable.emoji_e01f, R.drawable.emoji_e125,
                R.drawable.emoji_e03a, R.drawable.emoji_e14e, R.drawable.emoji_e252,
                R.drawable.emoji_e137, R.drawable.emoji_e209, R.drawable.emoji_e154,
                R.drawable.emoji_e133, R.drawable.emoji_e150, R.drawable.emoji_e320,
                R.drawable.emoji_e123, R.drawable.emoji_e132, R.drawable.emoji_e143,
                R.drawable.emoji_e50b, R.drawable.emoji_e514, R.drawable.emoji_e513,
                R.drawable.emoji_e50c, R.drawable.emoji_e50d, R.drawable.emoji_e511,
                R.drawable.emoji_e50f, R.drawable.emoji_e512, R.drawable.emoji_e510,
                R.drawable.emoji_e50e, R.drawable.emoji_e21c, R.drawable.emoji_e21d,
                R.drawable.emoji_e21e, R.drawable.emoji_e21f, R.drawable.emoji_e220,
                R.drawable.emoji_e221, R.drawable.emoji_e222, R.drawable.emoji_e223,
                R.drawable.emoji_e224, R.drawable.emoji_e225, R.drawable.emoji_e210,
                R.drawable.emoji_e232, R.drawable.emoji_e233, R.drawable.emoji_e235,
                R.drawable.emoji_e234, R.drawable.emoji_e236, R.drawable.emoji_e237,
                R.drawable.emoji_e238, R.drawable.emoji_e239, R.drawable.emoji_e23b,
                R.drawable.emoji_e23a, R.drawable.emoji_e23d, R.drawable.emoji_e23c,
                R.drawable.emoji_e24d, R.drawable.emoji_e212, R.drawable.emoji_e24c,
                R.drawable.emoji_e213, R.drawable.emoji_e214, R.drawable.emoji_e507,
                R.drawable.emoji_e203, R.drawable.emoji_e20b, R.drawable.emoji_e22a,
                R.drawable.emoji_e22b, R.drawable.emoji_e226, R.drawable.emoji_e227,
                R.drawable.emoji_e22c, R.drawable.emoji_e22d, R.drawable.emoji_e215,
                R.drawable.emoji_e216, R.drawable.emoji_e217, R.drawable.emoji_e218,
                R.drawable.emoji_e228, R.drawable.emoji_e151, R.drawable.emoji_e138,
                R.drawable.emoji_e139, R.drawable.emoji_e13a, R.drawable.emoji_e208,
                R.drawable.emoji_e14f, R.drawable.emoji_e20a, R.drawable.emoji_e434,
                R.drawable.emoji_e309, R.drawable.emoji_e315, R.drawable.emoji_e30d,
                R.drawable.emoji_e207, R.drawable.emoji_e229, R.drawable.emoji_e206,
                R.drawable.emoji_e205, R.drawable.emoji_e204, R.drawable.emoji_e12e,
                R.drawable.emoji_e250, R.drawable.emoji_e251, R.drawable.emoji_e14a,
                R.drawable.emoji_e149, R.drawable.emoji_e23f, R.drawable.emoji_e240,
                R.drawable.emoji_e241, R.drawable.emoji_e242, R.drawable.emoji_e243,
                R.drawable.emoji_e244, R.drawable.emoji_e245, R.drawable.emoji_e246,
                R.drawable.emoji_e247, R.drawable.emoji_e248, R.drawable.emoji_e249,
                R.drawable.emoji_e24a, R.drawable.emoji_e24b, R.drawable.emoji_e23e,
                R.drawable.emoji_e532, R.drawable.emoji_e533, R.drawable.emoji_e534,
                R.drawable.emoji_e535, R.drawable.emoji_e21a, R.drawable.emoji_e219,
                R.drawable.emoji_e21b, R.drawable.emoji_e02f, R.drawable.emoji_e024,
                R.drawable.emoji_e025, R.drawable.emoji_e026, R.drawable.emoji_e027,
                R.drawable.emoji_e028, R.drawable.emoji_e029, R.drawable.emoji_e02a,
                R.drawable.emoji_e02b, R.drawable.emoji_e02c, R.drawable.emoji_e02d,
                R.drawable.emoji_e02e, R.drawable.emoji_e332, R.drawable.emoji_e333,
                R.drawable.emoji_e24e, R.drawable.emoji_e24f, R.drawable.emoji_e537
        };

        public static int getSmileyResource(int which) {
            return sIconIds[which];
        }
    }

    // NOTE: if you change anything about this array, you must make the
    // corresponding change in both the string array: default_smiley_texts in
    // res/values/arrays.xml
    // You also need to update the unified emoji table in mEmojiTexts
    public static int[] DEFAULT_EMOJI_RES_IDS;

    /**
     * @deprecated these private characters should not be used, and are only
     *             here to retain compatibility with previous implementation of
     *             emoji support
     */
    public static final String[] mSoftbankEmojiTexts = {
            "\ue415", "\ue056", "\ue057", "\ue414", "\ue405", "\ue106", "\ue418", "\ue417",
            "\ue40d", "\ue40a", "\ue404", "\ue105", "\ue409", "\ue40e", "\ue402", "\ue108",
            "\ue403", "\ue058", "\ue407", "\ue401", "\ue40f", "\ue40b", "\ue406", "\ue413",
            "\ue411", "\ue412", "\ue410", "\ue107", "\ue059", "\ue416", "\ue408", "\ue40c",
            "\ue11a", "\ue10c", "\ue32c", "\ue32a", "\ue32d", "\ue328", "\ue32b", "\ue022",
            "\ue023", "\ue327", "\ue329", "\ue32e", "\ue32f", "\ue335", "\ue334", "\ue021",
            "\ue337", "\ue020", "\ue336", "\ue13c", "\ue330", "\ue331", "\ue326", "\ue03e",
            "\ue11d", "\ue05a", "\ue00e", "\ue421", "\ue420", "\ue00d", "\ue010", "\ue011",
            "\ue41e", "\ue012", "\ue422", "\ue22e", "\ue22f", "\ue231", "\ue230", "\ue427",
            "\ue41d", "\ue00f", "\ue41f", "\ue14c", "\ue201", "\ue115", "\ue428", "\ue51f",
            "\ue429", "\ue424", "\ue423", "\ue253", "\ue426", "\ue111", "\ue425", "\ue31e",
            "\ue31f", "\ue31d", "\ue001", "\ue002", "\ue005", "\ue004", "\ue51a", "\ue519",
            "\ue518", "\ue515", "\ue516", "\ue517", "\ue51b", "\ue152", "\ue04e", "\ue51c",
            "\ue51e", "\ue11c", "\ue536", "\ue003", "\ue41c", "\ue41b", "\ue419", "\ue41a",
            "\ue04a", "\ue04b", "\ue049", "\ue048", "\ue04c", "\ue13d", "\ue443", "\ue43e",
            "\ue04f", "\ue052", "\ue053", "\ue524", "\ue52c", "\ue52a", "\ue531", "\ue050",
            "\ue527", "\ue051", "\ue10b", "\ue52b", "\ue52f", "\ue528", "\ue01a", "\ue134",
            "\ue530", "\ue529", "\ue526", "\ue52d", "\ue521", "\ue523", "\ue52e", "\ue055",
            "\ue525", "\ue10a", "\ue109", "\ue522", "\ue019", "\ue054", "\ue520", "\ue306",
            "\ue030", "\ue304", "\ue110", "\ue032", "\ue305", "\ue303", "\ue118", "\ue447",
            "\ue119", "\ue307", "\ue308", "\ue444", "\ue441", "\ue436", "\ue437", "\ue438",
            "\ue43a", "\ue439", "\ue43b", "\ue117", "\ue440", "\ue442", "\ue446", "\ue445",
            "\ue11b", "\ue448", "\ue033", "\ue112", "\ue325", "\ue312", "\ue310", "\ue126",
            "\ue127", "\ue008", "\ue03d", "\ue00c", "\ue12a", "\ue00a", "\ue00b", "\ue009",
            "\ue316", "\ue129", "\ue141", "\ue142", "\ue317", "\ue128", "\ue14b", "\ue211",
            "\ue114", "\ue145", "\ue144", "\ue03f", "\ue313", "\ue116", "\ue10f", "\ue104",
            "\ue103", "\ue101", "\ue102", "\ue13f", "\ue140", "\ue11f", "\ue12f", "\ue031",
            "\ue30e", "\ue311", "\ue113", "\ue30f", "\ue13b", "\ue42b", "\ue42a", "\ue018",
            "\ue016", "\ue015", "\ue014", "\ue42c", "\ue42d", "\ue017", "\ue013", "\ue20e",
            "\ue20c", "\ue20f", "\ue20d", "\ue131", "\ue12b", "\ue130", "\ue12d", "\ue324",
            "\ue301", "\ue148", "\ue502", "\ue03c", "\ue30a", "\ue042", "\ue040", "\ue041",
            "\ue12c", "\ue007", "\ue31a", "\ue13e", "\ue31b", "\ue006", "\ue302", "\ue319",
            "\ue321", "\ue322", "\ue314", "\ue503", "\ue10e", "\ue318", "\ue43c", "\ue11e",
            "\ue323", "\ue31c", "\ue034", "\ue035", "\ue045", "\ue338", "\ue047", "\ue30c",
            "\ue044", "\ue30b", "\ue043", "\ue120", "\ue33b", "\ue33f", "\ue341", "\ue34c",
            "\ue344", "\ue342", "\ue33d", "\ue33e", "\ue340", "\ue34d", "\ue339", "\ue147",
            "\ue343", "\ue33c", "\ue33a", "\ue43f", "\ue34b", "\ue046", "\ue345", "\ue346",
            "\ue348", "\ue347", "\ue34a", "\ue349", "\ue036", "\ue157", "\ue038", "\ue153",
            "\ue155", "\ue14d", "\ue156", "\ue501", "\ue158", "\ue43d", "\ue037", "\ue504",
            "\ue44a", "\ue146", "\ue50a", "\ue505", "\ue506", "\ue122", "\ue508", "\ue509",
            "\ue03b", "\ue04d", "\ue449", "\ue44b", "\ue51d", "\ue44c", "\ue124", "\ue121",
            "\ue433", "\ue202", "\ue135", "\ue01c", "\ue01d", "\ue10d", "\ue136", "\ue42e",
            "\ue01b", "\ue15a", "\ue159", "\ue432", "\ue430", "\ue431", "\ue42f", "\ue01e",
            "\ue039", "\ue435", "\ue01f", "\ue125", "\ue03a", "\ue14e", "\ue252", "\ue137",
            "\ue209", "\ue154", "\ue133", "\ue150", "\ue320", "\ue123", "\ue132", "\ue143",
            "\ue50b", "\ue514", "\ue513", "\ue50c", "\ue50d", "\ue511", "\ue50f", "\ue512",
            "\ue510", "\ue50e", "\u0031\u20e3", "\ue21d", "\ue21e", "\ue21f", "\ue220", "\ue221",
            "\ue222", "\ue223", "\ue224", "\ue225", "\u0023\u20e3", "\ue232", "\ue233", "\ue235",
            "\ue234", "\ue236", "\ue237", "\ue238", "\ue239", "\ue23b", "\ue23a", "\ue23d",
            "\ue23c", "\ue24d", "\ue212", "\ue24c", "\ue213", "\ue214", "\ue507", "\ue203",
            "\ue20b", "\ue22a", "\ue22b", "\ue226", "\ue227", "\ue22c", "\ue22d", "\ue215",
            "\ue216", "\ue217", "\ue218", "\ue228", "\ue151", "\ue138", "\ue139", "\ue13a",
            "\ue208", "\ue14f", "\ue20a", "\ue434", "\ue309", "\ue315", "\ue30d", "\ue207",
            "\ue229", "\ue206", "\ue205", "\ue204", "\ue12e", "\ue250", "\ue251", "\ue14a",
            "\ue149", "\ue23f", "\ue240", "\ue241", "\ue242", "\ue243", "\ue244", "\ue245",
            "\ue246", "\ue247", "\ue248", "\ue249", "\ue24a", "\ue24b", "\ue23e", "\ue532",
            "\ue533", "\ue534", "\ue535", "\ue21a", "\ue219", "\ue21b", "\ue02f", "\ue024",
            "\ue025", "\ue026", "\ue027", "\ue028", "\ue029", "\ue02a", "\ue02b", "\ue02c",
            "\ue02d", "\ue02e", "\ue332", "\ue333", "\ue24e", "\ue24f", "\ue537"
    };

    public static final String[] mEmojiTexts = {
            // Beginning of section 1 (Smileys)
            "\uD83D\uDE04", // Softbank: E415 - Unified: 1F604
            "\uD83D\uDE0A", // Softbank: E056 - Unified: 1F60A
            "\uD83D\uDE03", // Softbank: E057 - Unified: 1F603
            "\u263A", // Softbank: E414 - Unified: 263A
            "\uD83D\uDE09", // Softbank: E405 - Unified: 1F609
            "\uD83D\uDE0D", // Softbank: E106 - Unified: 1F60D
            "\uD83D\uDE18", // Softbank: E418 - Unified: 1F618
            "\uD83D\uDE1A", // Softbank: E417 - Unified: 1F61A
            "\uD83D\uDE33", // Softbank: E40D - Unified: 1F633
            "\uD83D\uDE0C", // Softbank: E40A - Unified: 1F60C
            "\uD83D\uDE01", // Softbank: E404 - Unified: 1F601
            "\uD83D\uDE1C", // Softbank: E105 - Unified: 1F61C
            "\uD83D\uDE1D", // Softbank: E409 - Unified: 1F61D
            "\uD83D\uDE12", // Softbank: E40E - Unified: 1F612
            "\uD83D\uDE0F", // Softbank: E402 - Unified: 1F60F
            "\uD83D\uDE13", // Softbank: E108 - Unified: 1F613
            "\uD83D\uDE14", // Softbank: E403 - Unified: 1F614
            "\uD83D\uDE1E", // Softbank: E058 - Unified: 1F61E
            "\uD83D\uDE16", // Softbank: E407 - Unified: 1F616
            "\uD83D\uDE25", // Softbank: E401 - Unified: 1F625
            "\uD83D\uDE30", // Softbank: E40F - Unified: 1F630
            "\uD83D\uDE28", // Softbank: E40B - Unified: 1F628
            "\uD83D\uDE23", // Softbank: E406 - Unified: 1F623
            "\uD83D\uDE22", // Softbank: E413 - Unified: 1F622
            "\uD83D\uDE2D", // Softbank: E411 - Unified: 1F62D
            "\uD83D\uDE02", // Softbank: E412 - Unified: 1F602
            "\uD83D\uDE32", // Softbank: E410 - Unified: 1F632
            "\uD83D\uDE31", // Softbank: E107 - Unified: 1F631
            "\uD83D\uDE20", // Softbank: E059 - Unified: 1F620
            "\uD83D\uDE21", // Softbank: E416 - Unified: 1F621
            "\uD83D\uDE2A", // Softbank: E408 - Unified: 1F62A
            "\uD83D\uDE37", // Softbank: E40C - Unified: 1F637
            "\uD83D\uDC7F", // Softbank: E11A - Unified: 1F47F
            "\uD83D\uDC7D", // Softbank: E10C - Unified: 1F47D
            "\uD83D\uDC9B", // Softbank: E32C - Unified: 1F49B
            "\uD83D\uDC99", // Softbank: E32A - Unified: 1F499
            "\uD83D\uDC9C", // Softbank: E32D - Unified: 1F49C
            "\uD83D\uDC97", // Softbank: E328 - Unified: 1F497
            "\uD83D\uDC9A", // Softbank: E32B - Unified: 1F49A
            "\u2764", // Softbank: E022 - Unified: 2764
            "\uD83D\uDC94", // Softbank: E023 - Unified: 1F494
            "\uD83D\uDC93", // Softbank: E327 - Unified: 1F493
            "\uD83D\uDC98", // Softbank: E329 - Unified: 1F498
            "\u2728", // Softbank: E32E - Unified: 2728
            "\u2B50", // Softbank: E32F - Unified: 2B50
            "\uD83C\uDF1F", // Softbank: E335 - Unified: 1F31F
            "\uD83D\uDCA2", // Softbank: E334 - Unified: 1F4A2
            "\u2757", // Softbank: E021 - Unified: 2757
            "\u2755", // Softbank: E337 - Unified: 2755
            "\u2753", // Softbank: E020 - Unified: 2753
            "\u2754", // Softbank: E336 - Unified: 2754
            "\uD83D\uDCA4", // Softbank: E13C - Unified: 1F4A4
            "\uD83D\uDCA8", // Softbank: E330 - Unified: 1F4A8
            "\uD83D\uDE05", // Softbank: E331 - Unified: 1F605
            "\uD83C\uDFB6", // Softbank: E326 - Unified: 1F3B6
            "\uD83C\uDFB5", // Softbank: E03E - Unified: 1F3B5
            "\uD83D\uDD25", // Softbank: E11D - Unified: 1F525
            "\uD83D\uDCA9", // Softbank: E05A - Unified: 1F4A9
            "\uD83D\uDC4D", // Softbank: E00E - Unified: 1F44D
            "\uD83D\uDC4E", // Softbank: E421 - Unified: 1F44E
            "\uD83D\uDC4C", // Softbank: E420 - Unified: 1F44C
            "\uD83D\uDC4A", // Softbank: E00D - Unified: 1F44A
            "\u270A", // Softbank: E010 - Unified: 270A
            "\u270C", // Softbank: E011 - Unified: 270C
            "\uD83D\uDC4B", // Softbank: E41E - Unified: 1F44B
            "\u270B", // Softbank: E012 - Unified: 1F64B
            "\uD83D\uDC50", // Softbank: E422 - Unified: 1F450
            "\uD83D\uDC46", // Softbank: E22E - Unified: 1F446
            "\uD83D\uDC47", // Softbank: E22F - Unified: 1F447
            "\uD83D\uDC49", // Softbank: E231 - Unified: 1F449
            "\uD83D\uDC48", // Softbank: E230 - Unified: 1F448
            "\uD83D\uDE4C", // Softbank: E427 - Unified: 1F64C
            "\uD83D\uDE4F", // Softbank: E41D - Unified: 1F64F
            "\u261D", // Softbank: E00F - Unified: 261D
            "\uD83D\uDC4F", // Softbank: E41F - Unified: 1F44F
            "\uD83D\uDCAA", // Softbank: E14C - Unified: 1F4AA
            "\uD83D\uDEB6", // Softbank: E201 - Unified: 1F6B6
            "\uD83C\uDFC3", // Softbank: E115 - Unified: 1F3C3
            "\uD83D\uDC6B", // Softbank: E428 - Unified: 1F46B
            "\uD83D\uDC83", // Softbank: E51F - Unified: 1F483
            "\uD83D\uDC6F", // Softbank: E429 - Unified: 1F46F
            "\uD83D\uDE46", // Softbank: E424 - Unified: 1F646
            "\uD83D\uDE45", // Softbank: E423 - Unified: 1F645
            "\uD83D\uDC81", // Softbank: E253 - Unified: 1F481
            "\uD83D\uDE47", // Softbank: E426 - Unified: 1F647
            "\uD83D\uDC8F", // Softbank: E111 - Unified: 1F48F
            "\uD83D\uDC91", // Softbank: E425 - Unified: 1F491
            "\uD83D\uDC86", // Softbank: E31E - Unified: 1F486
            "\uD83D\uDC87", // Softbank: E31F - Unified: 1F487
            "\uD83D\uDC85", // Softbank: E31D - Unified: 1F485
            "\uD83D\uDC66", // Softbank: E001 - Unified: 1F466
            "\uD83D\uDC67", // Softbank: E002 - Unified: 1F467
            "\uD83D\uDC69", // Softbank: E005 - Unified: 1F469
            "\uD83D\uDC68", // Softbank: E004 - Unified: 1F468
            "\uD83D\uDC76", // Softbank: E51A - Unified: 1F476
            "\uD83D\uDC75", // Softbank: E519 - Unified: 1F475
            "\uD83D\uDC74", // Softbank: E518 - Unified: 1F474
            "\uD83D\uDC71", // Softbank: E515 - Unified: 1F471
            "\uD83D\uDC72", // Softbank: E516 - Unified: 1F472
            "\uD83D\uDC73", // Softbank: E517 - Unified: 1F473
            "\uD83D\uDC77", // Softbank: E51B - Unified: 1F477
            "\uD83D\uDC6E", // Softbank: E152 - Unified: 1F46E
            "\uD83D\uDC7C", // Softbank: E04E - Unified: 1F47C
            "\uD83D\uDC78", // Softbank: E51C - Unified: 1F478
            "\uD83D\uDC82", // Softbank: E51E - Unified: 1F482
            "\uD83D\uDC80", // Softbank: E11C - Unified: 1F480
            "\uD83D\uDC3E", // Softbank: E536 - Unified: 1F43E
            "\uD83D\uDC8B", // Softbank: E003 - Unified: 1F48B
            "\uD83D\uDC44", // Softbank: E41C - Unified: 1F444
            "\uD83D\uDC42", // Softbank: E41B - Unified: 1F442
            "\uD83D\uDC40", // Softbank: E419 - Unified: 1F440
            "\uD83D\uDC43", // Softbank: E41A - Unified: 1F443
            // Beginning of section 2 (Nature)
            "\u2600", // Softbank: E04A - Unified: 2600
            "\u2614", // Softbank: E04B - Unified: 2614
            "\u2601", // Softbank: E049 - Unified: 2601
            "\u26C4", // Softbank: E048 - Unified: 26C4
            "\uD83C\uDF19", // Softbank: E04C - Unified: 1F319
            "\u26A1", // Softbank: E13D - Unified: 26A1
            "\uD83C\uDF00", // Softbank: E443 - Unified: 1F300
            "\uD83C\uDF0A", // Softbank: E43E - Unified: 1F30A
            "\uD83D\uDC31", // Softbank: E04F - Unified: 1F431
            "\uD83D\uDC36", // Softbank: E052 - Unified: 1F436
            "\uD83D\uDC2D", // Softbank: E053 - Unified: 1F42D
            "\uD83D\uDC39", // Softbank: E524 - Unified: 1F439
            "\uD83D\uDC30", // Softbank: E52C - Unified: 1F430
            "\uD83D\uDC3A", // Softbank: E52A - Unified: 1F43A
            "\uD83D\uDC38", // Softbank: E531 - Unified: 1F438
            "\uD83D\uDC2F", // Softbank: E050 - Unified: 1F42F
            "\uD83D\uDC28", // Softbank: E527 - Unified: 1F428
            "\uD83D\uDC3B", // Softbank: E051 - Unified: 1F43B
            "\uD83D\uDC37", // Softbank: E10B - Unified: 1F437
            "\uD83D\uDC2E", // Softbank: E52B - Unified: 1F42E
            "\uD83D\uDC17", // Softbank: E52F - Unified: 1F417
            "\uD83D\uDC12", // Softbank: E528 - Unified: 1F412
            "\uD83D\uDC34", // Softbank: E01A - Unified: 1F434
            "\uD83D\uDC0E", // Softbank: E134 - Unified: 1F40E
            "\uD83D\uDC2B", // Softbank: E530 - Unified: 1F42B
            "\uD83D\uDC11", // Softbank: E529 - Unified: 1F411
            "\uD83D\uDC18", // Softbank: E526 - Unified: 1F418
            "\uD83D\uDC0D", // Softbank: E52D - Unified: 1F40D
            "\uD83D\uDC26", // Softbank: E521 - Unified: 1F426
            "\uD83D\uDC24", // Softbank: E523 - Unified: 1F424
            "\uD83D\uDC14", // Softbank: E52E - Unified: 1F414
            "\uD83D\uDC27", // Softbank: E055 - Unified: 1F427
            "\uD83D\uDC1B", // Softbank: E525 - Unified: 1F41B
            "\uD83D\uDC19", // Softbank: E10A - Unified: 1F419
            "\uD83D\uDC35", // Softbank: E109 - Unified: 1F435
            "\uD83D\uDC20", // Softbank: E522 - Unified: 1F420
            "\uD83D\uDC1F", // Softbank: E019 - Unified: 1F41F
            "\uD83D\uDC33", // Softbank: E054 - Unified: 1F433
            "\uD83D\uDC2C", // Softbank: E520 - Unified: 1F42C
            "\uD83D\uDC90", // Softbank: E306 - Unified: 1F490
            "\uD83C\uDF38", // Softbank: E030 - Unified: 1F338
            "\uD83C\uDF37", // Softbank: E304 - Unified: 1F337
            "\uD83C\uDF40", // Softbank: E110 - Unified: 1F340
            "\uD83C\uDF39", // Softbank: E032 - Unified: 1F339
            "\uD83C\uDF3B", // Softbank: E305 - Unified: 1F33B
            "\uD83C\uDF3A", // Softbank: E303 - Unified: 1F33A
            "\uD83C\uDF41", // Softbank: E118 - Unified: 1F341
            "\uD83C\uDF43", // Softbank: E447 - Unified: 1F343
            "\uD83C\uDF42", // Softbank: E119 - Unified: 1F342
            "\uD83C\uDF34", // Softbank: E307 - Unified: 1F334
            "\uD83C\uDF35", // Softbank: E308 - Unified: 1F335
            "\uD83C\uDF3E", // Softbank: E444 - Unified: 1F33E
            "\uD83D\uDC1A", // Softbank: E441 - Unified: 1F41A
            // Beginning of section 3 (Misc)
            "\uD83C\uDF8D", // Softbank: E436 - Unified: 1F38D
            "\uD83D\uDC9D", // Softbank: E437 - Unified: 1F49D
            "\uD83C\uDF8E", // Softbank: E438 - Unified: 1F38E
            "\uD83C\uDF92", // Softbank: E43A - Unified: 1F392
            "\uD83C\uDF93", // Softbank: E439 - Unified: 1F393
            "\uD83C\uDF8F", // Softbank: E43B - Unified: 1F38F
            "\uD83C\uDF86", // Softbank: E117 - Unified: 1F386
            "\uD83C\uDF87", // Softbank: E440 - Unified: 1F387
            "\uD83C\uDF90", // Softbank: E442 - Unified: 1F390
            "\uD83C\uDF91", // Softbank: E446 - Unified: 1F391
            "\uD83C\uDF83", // Softbank: E445 - Unified: 1F383
            "\uD83D\uDC7B", // Softbank: E11B - Unified: 1F47B
            "\uD83C\uDF85", // Softbank: E448 - Unified: 1F385
            "\uD83C\uDF84", // Softbank: E033 - Unified: 1F384
            "\uD83C\uDF81", // Softbank: E112 - Unified: 1F381
            "\uD83D\uDD14", // Softbank: E325 - Unified: 1F514
            "\uD83C\uDF89", // Softbank: E312 - Unified: 1F389
            "\uD83C\uDF88", // Softbank: E310 - Unified: 1F388
            "\uD83D\uDCBF", // Softbank: E126 - Unified: 1F4BF
            "\uD83D\uDCC0", // Softbank: E127 - Unified: 1F4C0
            "\uD83D\uDCF7", // Softbank: E008 - Unified: 1F4F7
            "\uD83C\uDFA5", // Softbank: E03D - Unified: 1F4F9
            "\uD83D\uDCBB", // Softbank: E00C - Unified: 1F4BB
            "\uD83D\uDCFA", // Softbank: E12A - Unified: 1F4FA
            "\uD83D\uDCF1", // Softbank: E00A - Unified: 1F4F1
            "\uD83D\uDCE0", // Softbank: E00B - Unified: 1F4E0
            "\u260E", // Softbank: E009 - Unified: 260E
            "\uD83D\uDCBD", // Softbank: E316 - Unified: 1F4BD
            "\uD83D\uDCFC", // Softbank: E129 - Unified: 1F4FC
            "\uD83D\uDD0A", // Softbank: E141 - Unified: 1F50A
            "\uD83D\uDCE2", // Softbank: E142 - Unified: 1F4E2
            "\uD83D\uDCE3", // Softbank: E317 - Unified: 1F4E3
            "\uD83D\uDCFB", // Softbank: E128 - Unified: 1F4FB
            "\uD83D\uDCE1", // Softbank: E14B - Unified: 1F4E1
            "\u27BF", // Softbank: E211 - Unified: 27BF
            "\uD83D\uDD0D", // Softbank: E114 - Unified: 1F50D
            "\uD83D\uDD13", // Softbank: E145 - Unified: 1F513
            "\uD83D\uDD12", // Softbank: E144 - Unified: 1F512
            "\uD83D\uDD11", // Softbank: E03F - Unified: 1F511
            "\u2702", // Softbank: E313 - Unified: 2702
            "\uD83D\uDD28", // Softbank: E116 - Unified: 1F528
            "\uD83D\uDCA1", // Softbank: E10F - Unified: 1F4A1
            "\uD83D\uDCF2", // Softbank: E104 - Unified: 1F4F2
            "\uD83D\uDCE9", // Softbank: E103 - Unified: 1F4E9
            "\uD83D\uDCEB", // Softbank: E101 - Unified: 1F4EB
            "\uD83D\uDCEE", // Softbank: E102 - Unified: 1F4EE
            "\uD83D\uDEC0", // Softbank: E13F - Unified: 1F6C0
            "\uD83D\uDEBD", // Softbank: E140 - Unified: 1F6BD
            "\uD83D\uDCBA", // Softbank: E11F - Unified: 1F4BA
            "\uD83D\uDCB0", // Softbank: E12F - Unified: 1F4B0
            "\uD83D\uDD31", // Softbank: E031 - Unified: 1F531
            "\uD83D\uDEAC", // Softbank: E30E - Unified: 1F6AC
            "\uD83D\uDCA3", // Softbank: E311 - Unified: 1F4A3
            "\uD83D\uDD2B", // Softbank: E113 - Unified: 1F52B
            "\uD83D\uDC8A", // Softbank: E30F - Unified: 1F48A
            "\uD83D\uDC89", // Softbank: E13B - Unified: 1F489
            "\uD83C\uDFC8", // Softbank: E42B - Unified: 1F3C8
            "\uD83C\uDFC0", // Softbank: E42A - Unified: 1F3C0
            "\u26BD", // Softbank: E018 - Unified: 26BD
            "\u26BE", // Softbank: E016 - Unified: 26BE
            "\uD83C\uDFBE", // Softbank: E015 - Unified: 1F3BE
            "\u26F3", // Softbank: E014 - Unified: 26F3
            "\uD83C\uDFB1", // Softbank: E42C - Unified: 1F3B1
            "\uD83C\uDFCA", // Softbank: E42D - Unified: 1F3CA
            "\uD83C\uDFC4", // Softbank: E017 - Unified: 1F3C4
            "\uD83C\uDFBF", // Softbank: E013 - Unified: 1F3BF
            "\u2660", // Softbank: E20E - Unified: 2660
            "\u2665", // Softbank: E20C - Unified: 2665
            "\u2663", // Softbank: E20F - Unified: 2663
            "\u2666", // Softbank: E20D - Unified: 2666
            "\uD83C\uDFC6", // Softbank: E131 - Unified: 1F3C6
            "\uD83D\uDC7E", // Softbank: E12B - Unified: 1F47E
            "\uD83C\uDFAF", // Softbank: E130 - Unified: 1F3AF
            "\uD83C\uDC04", // Softbank: E12D - Unified: 1F004
            "\uD83C\uDFAC", // Softbank: E324 - Unified: 1F3AC
            "\uD83D\uDCDD", // Softbank: E301 - Unified: 1F4DD
            "\uD83D\uDCD3", // Softbank: E148 - Unified: 1F4D3
            "\uD83C\uDFA8", // Softbank: E502 - Unified: 1F3A8
            "\uD83C\uDFA4", // Softbank: E03C - Unified: 1F3A4
            "\uD83C\uDFA7", // Softbank: E30A - Unified: 1F3A7
            "\uD83C\uDFBA", // Softbank: E042 - Unified: 1F3BA
            "\uD83C\uDFB7", // Softbank: E040 - Unified: 1F3B7
            "\uD83C\uDFB8", // Softbank: E041 - Unified: 1F3B8
            "\u303D", // Softbank: E12C - Unified: 303D
            "\uD83D\uDC5E", // Softbank: E007 - Unified: 1F45E
            "\uD83D\uDC61", // Softbank: E31A - Unified: 1F461
            "\uD83D\uDC60", // Softbank: E13E - Unified: 1F460
            "\uD83D\uDC62", // Softbank: E31B - Unified: 1F462
            "\uD83D\uDC55", // Softbank: E006 - Unified: 1F455
            "\uD83D\uDC54", // Softbank: E302 - Unified: 1F454
            "\uD83D\uDC57", // Softbank: E319 - Unified: 1F457
            "\uD83D\uDC58", // Softbank: E321 - Unified: 1F458
            "\uD83D\uDC59", // Softbank: E322 - Unified: 1F459
            "\uD83C\uDF80", // Softbank: E314 - Unified: 1F380
            "\uD83C\uDFA9", // Softbank: E503 - Unified: 1F3A9
            "\uD83D\uDC51", // Softbank: E10E - Unified: 1F451
            "\uD83D\uDC52", // Softbank: E318 - Unified: 1F452
            "\uD83C\uDF02", // Softbank: E43C - Unified: 1F302
            "\uD83D\uDCBC", // Softbank: E11E - Unified: 1F4BC
            "\uD83D\uDC5C", // Softbank: E323 - Unified: 1F45C
            "\uD83D\uDC84", // Softbank: E31C - Unified: 1F484
            "\uD83D\uDC8D", // Softbank: E034 - Unified: 1F48D
            "\uD83D\uDC8E", // Softbank: E035 - Unified: 1F48E
            "\u2615", // Softbank: E045 - Unified: 2615
            "\uD83C\uDF75", // Softbank: E338 - Unified: 1F375
            "\uD83C\uDF7A", // Softbank: E047 - Unified: 1F37A
            "\uD83C\uDF7B", // Softbank: E30C - Unified: 1F37B
            "\uD83C\uDF78", // Softbank: E044 - Unified: 1F378
            "\uD83C\uDF76", // Softbank: E30B - Unified: 1F376
            "\uD83C\uDF74", // Softbank: E043 - Unified: 1F374
            "\uD83C\uDF54", // Softbank: E120 - Unified: 1F354
            "\uD83C\uDF5F", // Softbank: E33B - Unified: 1F35F
            "\uD83C\uDF5D", // Softbank: E33F - Unified: 1F35D
            "\uD83C\uDF5B", // Softbank: E341 - Unified: 1F35B
            "\uD83C\uDF71", // Softbank: E34C - Unified: 1F371
            "\uD83C\uDF63", // Softbank: E344 - Unified: 1F363
            "\uD83C\uDF59", // Softbank: E342 - Unified: 1F359
            "\uD83C\uDF58", // Softbank: E33D - Unified: 1F358
            "\uD83C\uDF5A", // Softbank: E33E - Unified: 1F35A
            "\uD83C\uDF5C", // Softbank: E340 - Unified: 1F35C
            "\uD83C\uDF72", // Softbank: E34D - Unified: 1F372
            "\uD83C\uDF5E", // Softbank: E339 - Unified: 1F35E
            "\uD83C\uDF73", // Softbank: E147 - Unified: 1F373
            "\uD83C\uDF62", // Softbank: E343 - Unified: 1F362
            "\uD83C\uDF61", // Softbank: E33C - Unified: 1F361
            "\uD83C\uDF66", // Softbank: E33A - Unified: 1F366
            "\uD83C\uDF67", // Softbank: E43F - Unified: 1F367
            "\uD83C\uDF82", // Softbank: E34B - Unified: 1F382
            "\uD83C\uDF70", // Softbank: E046 - Unified: 1F370
            "\uD83C\uDF4E", // Softbank: E345 - Unified: 1F34E
            "\uD83C\uDF4A", // Softbank: E346 - Unified: 1F34A
            "\uD83C\uDF49", // Softbank: E348 - Unified: 1F349
            "\uD83C\uDF53", // Softbank: E347 - Unified: 1F353
            "\uD83C\uDF46", // Softbank: E34A - Unified: 1F346
            "\uD83C\uDF45", // Softbank: E349 - Unified: 1F345
            // Beginning of section 4 (Buildings)
            "\uD83C\uDFE0", // Softbank: E036 - Unified: 1F3E0
            "\uD83C\uDFEB", // Softbank: E157 - Unified: 1F3EB
            "\uD83C\uDFE2", // Softbank: E038 - Unified: 1F3E2
            "\uD83C\uDFE3", // Softbank: E153 - Unified: 1F3E3
            "\uD83C\uDFE5", // Softbank: E155 - Unified: 1F3E5
            "\uD83C\uDFE6", // Softbank: E14D - Unified: 1F3E6
            "\uD83C\uDFEA", // Softbank: E156 - Unified: 1F3EA
            "\uD83C\uDFE9", // Softbank: E501 - Unified: 1F3E9
            "\uD83C\uDFE8", // Softbank: E158 - Unified: 1F3E8
            "\uD83D\uDC92", // Softbank: E43D - Unified: 1F492
            "\u26EA", // Softbank: E037 - Unified: 26EA
            "\uD83C\uDFEC", // Softbank: E504 - Unified: 1F3EC
            "\uD83C\uDF07", // Softbank: E44A - Unified: 1F307
            "\uD83C\uDF06", // Softbank: E146 - Unified: 1F306
            "\uD83C\uDFE7", // Softbank: E50A - Unified: 1F3E7
            "\uD83C\uDFEF", // Softbank: E505 - Unified: 1F3EF
            "\uD83C\uDFF0", // Softbank: E506 - Unified: 1F3F0
            "\u26FA", // Softbank: E122 - Unified: 26FA
            "\uD83C\uDFED", // Softbank: E508 - Unified: 1F3ED
            "\uD83D\uDDFC", // Softbank: E509 - Unified: 1F5FC
            "\uD83D\uDDFB", // Softbank: E03B - Unified: 1F5FB
            "\uD83C\uDF04", // Softbank: E04D - Unified: 1F304
            "\uD83C\uDF05", // Softbank: E449 - Unified: 1F305
            "\uD83C\uDF03", // Softbank: E44B - Unified: 1F303
            "\uD83D\uDDFD", // Softbank: E51D - Unified: 1F5FD
            "\uD83C\uDF08", // Softbank: E44C - Unified: 1F308
            "\uD83C\uDFA1", // Softbank: E124 - Unified: 1F3A1
            "\u26F2", // Softbank: E121 - Unified: 26F2
            "\uD83C\uDFA2", // Softbank: E433 - Unified: 1F3A2
            "\uD83D\uDEA2", // Softbank: E202 - Unified: 1F6A2
            "\uD83D\uDEA4", // Softbank: E135 - Unified: 1F6A4
            "\u26F5", // Softbank: E01C - Unified: 26F5
            "\u2708", // Softbank: E01D - Unified: 2708
            "\uD83D\uDE80", // Softbank: E10D - Unified: 1F680
            "\uD83D\uDEB2", // Softbank: E136 - Unified: 1F6B2
            "\uD83D\uDE99", // Softbank: E42E - Unified: 1F699
            "\uD83D\uDE97", // Softbank: E01B - Unified: 1F697
            "\uD83D\uDE95", // Softbank: E15A - Unified: 1F695
            "\uD83D\uDE8C", // Softbank: E159 - Unified: 1F68C
            "\uD83D\uDE93", // Softbank: E432 - Unified: 1F693
            "\uD83D\uDE92", // Softbank: E430 - Unified: 1F692
            "\uD83D\uDE91", // Softbank: E431 - Unified: 1F691
            "\uD83D\uDE9A", // Softbank: E42F - Unified: 1F69A
            "\uD83D\uDE83", // Softbank: E01E - Unified: 1F683
            "\uD83D\uDE89", // Softbank: E039 - Unified: 1F689
            "\uD83D\uDE84", // Softbank: E435 - Unified: 1F684
            "\uD83D\uDE85", // Softbank: E01F - Unified: 1F685
            "\uD83C\uDFAB", // Softbank: E125 - Unified: 1F3AB
            "\u26FD", // Softbank: E03A - Unified: 26FD
            "\uD83D\uDEA5", // Softbank: E14E - Unified: 1F6A5
            "\u26A0", // Softbank: E252 - Unified: 26A0
            "\uD83D\uDEA7", // Softbank: E137 - Unified: 1F6A7
            "\uD83D\uDD30", // Softbank: E209 - Unified: 1F530
            "\uD83C\uDFE7", // Softbank: E154 - Unified: 1F3E7
            "\uD83C\uDFB0", // Softbank: E133 - Unified: 1F3B0
            "\uD83D\uDE8F", // Softbank: E150 - Unified: 1F68F
            "\uD83D\uDC88", // Softbank: E320 - Unified: 1F488
            "\u2668", // Softbank: E123 - Unified: 2668
            "\uD83C\uDFC1", // Softbank: E132 - Unified: 1F3C1
            "\uD83C\uDF8C", // Softbank: E143 - Unified: 1F38C
            "\uD83C\uDDEF\uD83C\uDDF5", // Softbank: E50B - Unified: 1F1EF 1F1F5
            "\uD83C\uDDF0\uD83C\uDDF7", // Softbank: E514 - Unified: 1F1F0 1F1F7
            "\uD83C\uDDE8\uD83C\uDDF3", // Softbank: E513 - Unified: 1F1E8 1F1F3
            "\uD83C\uDDFA\uD83C\uDDF8", // Softbank: E50C - Unified: 1F1FA 1F1F8
            "\uD83C\uDDEB\uD83C\uDDF7", // Softbank: E50D - Unified: 1F1EB 1F1F7
            "\uD83C\uDDEA\uD83C\uDDF8", // Softbank: E511 - Unified: 1F1EA 1F1F8
            "\uD83C\uDDEE\uD83C\uDDF9", // Softbank: E50F - Unified: 1F1EE 1F1F9
            "\uD83C\uDDF7\uD83C\uDDFA", // Softbank: E512 - Unified: 1F1F7 1F1FA
            "\uD83C\uDDEC\uD83C\uDDE7", // Softbank: E510 - Unified: 1F1EC 1F1E7
            "\uD83C\uDDE9\uD83C\uDDEA", // Softbank: E50E - Unified: 1F1E9 1F1EA
            // Beginning of section 5 (Symbols)
            "\u0031\u20E3", // Softbank: E21C - Unified: 0031 20E3
            "\u0032\u20E3", // Softbank: E21D - Unified: 0032 20E3
            "\u0033\u20E3", // Softbank: E21E - Unified: 0033 20E3
            "\u0034\u20E3", // Softbank: E21F - Unified: 0034 20E3
            "\u0035\u20E3", // Softbank: E220 - Unified: 0035 20E3
            "\u0036\u20E3", // Softbank: E221 - Unified: 0036 20E3
            "\u0037\u20E3", // Softbank: E222 - Unified: 0037 20E3
            "\u0038\u20E3", // Softbank: E223 - Unified: 0038 20E3
            "\u0039\u20E3", // Softbank: E224 - Unified: 0039 20E3
            "\u0030\u20E3", // Softbank: E225 - Unified: 0030 20E3
            "\u0023\u20E3", // Softbank: E210 - Unified: 0023 20E3
            "\u2B06", // Softbank: E232 - Unified: 2B06
            "\u2B07", // Softbank: E233 - Unified: 2B07
            "\u2B05", // Softbank: E235 - Unified: 2B05
            "\u27A1", // Softbank: E234 - Unified: 27A1
            "\u2197", // Softbank: E236 - Unified: 2197
            "\u2196", // Softbank: E237 - Unified: 2196
            "\u2198", // Softbank: E238 - Unified: 2198
            "\u2199", // Softbank: E239 - Unified: 2199
            "\u25C0", // Softbank: E23B - Unified: 25C0
            "\u25B6", // Softbank: E23A - Unified: 25B6
            "\u23EA", // Softbank: E23D - Unified: 23EA
            "\u23E9", // Softbank: E23C - Unified: 23E9
            "\uD83C\uDD97", // Softbank: E24D - Unified: 1F197
            "\uD83C\uDD95", // Softbank: E212 - Unified: 1F195
            "\uD83D\uDD1D", // Softbank: E24C - Unified: 1F51D
            "\uD83C\uDD99", // Softbank: E213 - Unified: 1F199
            "\uD83C\uDD92", // Softbank: E214 - Unified: 1F192
            "\uD83C\uDFA6", // Softbank: E507 - Unified: 1F3A6
            "\uD83C\uDE01", // Softbank: E203 - Unified: 1F201
            "\uD83D\uDCF6", // Softbank: E20B - Unified: 1F4F6
            "\uD83C\uDE35", // Softbank: E22A - Unified: 1F235
            "\uD83C\uDE33", // Softbank: E22B - Unified: 1F233
            "\uD83C\uDE50", // Softbank: E226 - Unified: 1F250
            "\uD83C\uDE39", // Softbank: E227 - Unified: 1F239
            "\uD83C\uDE2F", // Softbank: E22C - Unified: 1F22F
            "\uD83C\uDE3A", // Softbank: E22D - Unified: 1F23A
            "\uD83C\uDE36", // Softbank: E215 - Unified: 1F236
            "\uD83C\uDE1A", // Softbank: E216 - Unified: 1F21A
            "\uD83C\uDE37", // Softbank: E217 - Unified: 1F237
            "\uD83C\uDE38", // Softbank: E218 - Unified: 1F238
            "\uD83C\uDE02", // Softbank: E228 - Unified: 1F202
            "\uD83D\uDEBB", // Softbank: E151 - Unified: 1F6BB
            "\uD83D\uDEB9", // Softbank: E138 - Unified: 1F6B9
            "\uD83D\uDEBA", // Softbank: E139 - Unified: 1F6BA
            "\uD83D\uDEBC", // Softbank: E13A - Unified: 1F6BC
            "\uD83D\uDEAD", // Softbank: E208 - Unified: 1F6AD
            "\uD83C\uDD7F", // Softbank: E14F - Unified: 1F17F
            "\u267F", // Softbank: E20A - Unified: 267F
            "\uD83D\uDE87", // Softbank: E434 - Unified: 1F687
            "\uD83D\uDEBE", // Softbank: E309 - Unified: 1F6BE
            "\u3299", // Softbank: E315 - Unified: 3299
            "\u3297", // Softbank: E30D - Unified: 3297
            "\uD83D\uDD1E", // Softbank: E207 - Unified: 1F51E
            "\uD83C\uDD94", // Softbank: E229 - Unified: 1F194
            "\u2733", // Softbank: E206 - Unified: 2733
            "\u2734", // Softbank: E205 - Unified: 2734
            "\uD83D\uDC9F", // Softbank: E204 - Unified: 1F49F
            "\uD83C\uDD9A", // Softbank: E12E - Unified: 1F19A
            "\uD83D\uDCF3", // Softbank: E250 - Unified: 1F4F3
            "\uD83D\uDCF4", // Softbank: E251 - Unified: 1F4F4
            "\uD83D\uDCB9", // Softbank: E14A - Unified: 1F4B9
            "\uD83D\uDCB1", // Softbank: E149 - Unified: 1F4B1
            "\u2648", // Softbank: E23F - Unified: 2648
            "\u2649", // Softbank: E240 - Unified: 2649
            "\u264A", // Softbank: E241 - Unified: 264A
            "\u264B", // Softbank: E242 - Unified: 264B
            "\u264C", // Softbank: E243 - Unified: 264C
            "\u264D", // Softbank: E244 - Unified: 264D
            "\u264E", // Softbank: E245 - Unified: 264E
            "\u264F", // Softbank: E246 - Unified: 264F
            "\u2650", // Softbank: E247 - Unified: 2650
            "\u2651", // Softbank: E248 - Unified: 2651
            "\u2652", // Softbank: E249 - Unified: 2652
            "\u2653", // Softbank: E24A - Unified: 2653
            "\u26CE", // Softbank: E24B - Unified: 26CE
            "\uD83D\uDD2F", // Softbank: E23E - Unified: 1F52F
            "\uD83C\uDD70", // Softbank: E532 - Unified: 1F170
            "\uD83C\uDD71", // Softbank: E533 - Unified: 1F171
            "\uD83C\uDD8E", // Softbank: E534 - Unified: 1F18E
            "\uD83C\uDD7E", // Softbank: E535 - Unified: 1F17E
            "\uD83D\uDD32", // Softbank: E21A - Unified: 1F532
            "\uD83D\uDD34", // Softbank: E219 - Unified: 1F534
            "\uD83D\uDD33", // Softbank: E21B - Unified: 1F533
            "\uD83D\uDD5B", // Softbank: E02F - Unified: 1F55B
            "\uD83D\uDD50", // Softbank: E024 - Unified: 1F550
            "\uD83D\uDD51", // Softbank: E025 - Unified: 1F551
            "\uD83D\uDD52", // Softbank: E026 - Unified: 1F552
            "\uD83D\uDD53", // Softbank: E027 - Unified: 1F553
            "\uD83D\uDD54", // Softbank: E028 - Unified: 1F554
            "\uD83D\uDD55", // Softbank: E029 - Unified: 1F555
            "\uD83D\uDD56", // Softbank: E02A - Unified: 1F556
            "\uD83D\uDD57", // Softbank: E02B - Unified: 1F557
            "\uD83D\uDD58", // Softbank: E02C - Unified: 1F558
            "\uD83D\uDD59", // Softbank: E02D - Unified: 1F559
            "\uD83D\uDD5A", // Softbank: E02E - Unified: 1F55A
            "\u2B55", // Softbank: E332 - Unified: 2B55
            "\u274C", // Softbank: E333 - Unified: 274C
            "\u00A9", // Softbank: E24E - Unified: 00A9
            "\u00AE", // Softbank: E24F - Unified: 00AE
            "\u2122" // Softbank: E537 - Unified: 2122
    };

    /**
     * Builds the hashtable we use for mapping the string version of a smiley
     * (e.g. ":-)") to a resource ID for the icon version.
     */
    private HashMap<String, Integer> buildSmileyToRes() {

        if (mEmojiTexts.length != mSoftbankEmojiTexts.length) {
            // Throw an exception if someone updated mEmojiTexts
            // and failed to update arrays.xml (Softbank)
            throw new IllegalStateException("Smiley resource ID/text mismatch - "
                    + mEmojiTexts.length
                    + " != " + mSoftbankEmojiTexts.length);
        }

        // Initialize the resource ids
        DEFAULT_EMOJI_RES_IDS = new int[mSoftbankEmojiTexts.length];
        for (int i = 0; i < mSoftbankEmojiTexts.length; i++) {
            DEFAULT_EMOJI_RES_IDS[i] = Emojis.getSmileyResource(i);
        }

        HashMap<String, Integer> smileyToRes = new HashMap<String, Integer>(
                mSoftbankEmojiTexts.length);
        for (int i = 0; i < mSoftbankEmojiTexts.length; i++) {
            smileyToRes.put(mSoftbankEmojiTexts[i], DEFAULT_EMOJI_RES_IDS[i]);
            smileyToRes.put(mEmojiTexts[i], DEFAULT_EMOJI_RES_IDS[i]);
        }

        return smileyToRes;
    }

    /**
     * Builds the regular expression we use to find emojis in
     * {@link #addEmojiSpans}.
     */
    private Pattern buildPattern() {
        // Set the StringBuilder capacity with the assumption that one emoji is
        // 5 char (1 surrogate pair, 1 separator, 1 utf-8 char and 1 separator)
        StringBuilder patternString = new StringBuilder(mEmojiTexts.length * 5);

        // Build a regex that looks like (\uDAAA\uDBBB|\uCCCC|...), but escaping
        // the emojis
        // properly so they will be interpreted literally by the regex matcher.
        patternString.append('(');
        for (int i = 0; i < mSoftbankEmojiTexts.length; i++) {
            patternString.append(Pattern.quote(mSoftbankEmojiTexts[i]));
            patternString.append('|');
            patternString.append(Pattern.quote(mEmojiTexts[i]));
            patternString.append('|');
        }

        // Replace the extra '|' with a ')'
        patternString.replace(patternString.length() - 1, patternString.length(), ")");

        return Pattern.compile(patternString.toString());
    }

    /**
     * Adds ImageSpans to a CharSequence that replace unicode emojis with a
     * graphical version.
     * 
     * @param text A String possibly containing emojis (using string for UTF-16)
     * @return A CharSequence annotated with ImageSpans covering any recognized
     *         emojis
     */
    public CharSequence addEmojiSpans(CharSequence text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        // Scan for both Softbank private Unicode emoji code points (every
        // Android SMS app + iOS < 5) and Unicode 6.1 emoji BMP and non-BMP
        // codes
        Matcher matcher = mPattern.matcher(text);
        while (matcher.find()) {
            int resId = mSmileyToRes.get(matcher.group());
            builder.setSpan(new ImageSpan(mContext, resId), matcher.start(), matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return builder;
    }
}
