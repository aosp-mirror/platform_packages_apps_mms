/*
 * Copyright (C) 2008 Google Inc.
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

/**
 * This class exists specifically to allow us to require permissions checks on SMS_RECEIVED
 * broadcasts that are not applicable to other kinds of broadcast messages handled by the
 * SmsReceiver base class.
 */
public class PrivilegedSmsReceiver extends SmsReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Pass the message to the base class implementation, noting that it
        // was permission-checked on the way in.
        onReceiveWithPrivilege(context, intent, true);
    }
}
