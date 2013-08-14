/**
 * Copyright (C) 2013 Intel Corporation, All Rights Reserved
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

package com.android.mms.model;

/**
 * Class which implements the methods to encapsulate or decapsulate contact
 * details sent by SmsContact and verifies if a sms was encapsulated by this
 * application
 */
public class Encapsulation {
    private static final String ONE = "\u0001";
    private static final String POINT = "\u0002";
    private static final String MESSAGE_HEADER = ONE+ONE;
    private static final String MESSAGE_FOOTER = ONE + POINT + ONE;
    public static final String PARSE = "\u009C";
    public static final String NOTHING = "!?";

    public static String encapsulate(String message) {

        return MESSAGE_HEADER + message + MESSAGE_FOOTER;

    }

    public static String decapsulate(String message) {
        if (verifyEncapsulation(message)) {
            int n;
            n = message.length();
            return message.substring(MESSAGE_HEADER.length(), n - MESSAGE_FOOTER.length());
        }
        return null;
    }

    public static boolean verifyEncapsulation(String message) {

        int n;
        n = message.length();
        if (n < MESSAGE_HEADER.length() + MESSAGE_FOOTER.length())
            return false;
        String messageHeader = message.substring(0, MESSAGE_HEADER.length());
        String messageFooter = message.substring(n - MESSAGE_FOOTER.length());
        if (!messageHeader.equals(MESSAGE_HEADER) || !messageFooter.equals(MESSAGE_FOOTER))
            return false;

        return true;

    }
}
