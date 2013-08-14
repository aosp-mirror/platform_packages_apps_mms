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

package com.android.mms.transaction;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import com.android.mms.model.Encapsulation;
import com.android.mms.ui.Dialog;

/**
 * CheckIsContact it's called each time a Sms is received
 */

public class CheckIsContact {
    public static final String MESSAGE = "message";
    /**
     * Puts text from the message received sms string and calls the
     * method which verifies if it is a contact
     */

    public void checkSmsReceived(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        if (!bundle.isEmpty()) {
            Object messages[] = (Object[]) bundle.get("pdus");
            SmsMessage smsMessage[] = new SmsMessage[messages.length];
            for (int n = 0; n < messages.length; n++) {
                smsMessage[n] = SmsMessage.createFromPdu((byte[]) messages[n]);
            }
            StringBuilder a = new StringBuilder();
            for (int n = 0; n < messages.length; n++) {
                a.append(smsMessage[n].getMessageBody());
            }

            String sms = a.toString();
            if (Encapsulation.verifyEncapsulation(sms)) {
                Intent i = new Intent(context, Dialog.class);
                i.putExtra(MESSAGE, Encapsulation.decapsulate(sms));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }

}
