/*
 * Copyright (C) 2008 The Android Open Source Project
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


package com.android.mms.ui;

import com.android.mms.R;
import com.android.mms.ui.ConversationList;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;

/**
 * Various instrumentation tests for ConversationList.  
 * 
 */
public class ConversationListTests 
        extends ActivityInstrumentationTestCase2<ConversationList> {
    
    private Context mContext;

    public ConversationListTests() {
        super(ConversationList.class);
    }

    @Override
    protected void setUp() throws Exception {
    	super.setUp();
    	mContext = getInstrumentation().getTargetContext();
    }

    /**
     * Tests that various UI calls can be made safely even before the threads
     * have been loaded.  This catches various race conditions.
     */
    /* this test seems to get stuck!
    @MediumTest
    public void testUiRaceConditions() {
    	ConversationList a = getActivity();
        
        // menus
        getInstrumentation().invokeMenuActionSync(a, a.MENU_COMPOSE_NEW, 0);
        getInstrumentation().invokeMenuActionSync(a, a.MENU_SEARCH, 0);
        getInstrumentation().invokeMenuActionSync(a, a.MENU_PREFERENCES, 0);
        getInstrumentation().invokeMenuActionSync(a, a.MENU_DELETE_ALL, 0);
    }
    */        
}
