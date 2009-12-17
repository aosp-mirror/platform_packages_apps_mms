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

package com.android.mms.telephony;

// This class contains a number of constants from the internal Phone class from
// com.android.internal.telephony. TODO: remove this file when we add these constants to
// the Telephony API.

public class Phone {
    static final public String STATE_KEY = "state";

    /**
     * APN types for data connections.  These are usage categories for an APN
     * entry.  One APN entry may support multiple APN types, eg, a single APN
     * may service regular internet traffic ("default") as well as MMS-specific
     * connections.<br/>
     * APN_TYPE_ALL is a special type to indicate that this APN entry can
     * service all data connections.
     */
    static final public String APN_TYPE_ALL = "*";
    /** APN type for MMS traffic */
    static final public String APN_TYPE_MMS = "mms";

    // "Features" accessible through the connectivity manager
    static final public String FEATURE_ENABLE_MMS = "enableMMS";

    /**
     * Return codes for <code>enableApnType()</code>
     */
    static final public int APN_ALREADY_ACTIVE     = 0;
    static final public int APN_REQUEST_STARTED    = 1;
}

