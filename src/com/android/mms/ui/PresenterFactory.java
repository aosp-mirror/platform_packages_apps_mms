/*
 * Copyright (C) 2008 Esmertec AG.
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

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.android.mms.model.Model;

/**
 * The factory of concrete presenters.
 */
public class PresenterFactory {
    private static final String TAG = "PresenterFactory";
    private static final String PRESENTER_PACKAGE = "com.android.mms.ui.";

    public static Presenter getPresenter(String className, Context context,
            ViewInterface view, Model model) {
        try {
            if (className.indexOf(".") == -1) {
                className = PRESENTER_PACKAGE + className;
            }

            Class c = Class.forName(className);
            Constructor constructor = c.getConstructor(
                    Context.class, ViewInterface.class, Model.class);
            return (Presenter) constructor.newInstance(context, view, model);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Type not found: " + className, e);
        } catch (NoSuchMethodException e) {
            // Impossible to reach here.
            Log.e(TAG, "No such constructor.", e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Unexpected InvocationTargetException", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Unexpected IllegalAccessException", e);
        } catch (InstantiationException e) {
            Log.e(TAG, "Unexpected InstantiationException", e);
        }

        return null;
    }
}
