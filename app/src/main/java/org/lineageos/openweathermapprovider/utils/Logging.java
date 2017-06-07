/*
 *  Copyright (C) 2016 The CyanogenMod Project
 *  Copyright (C) 2017 The LineageOS Project
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

package org.lineageos.openweathermapprovider.utils;

import android.util.Log;

public class Logging {
    private static final boolean DEBUG = false;
    private static final String TAG = "OpenWeatherMapProvider";

    public static final void logd(String log) {
        if (DEBUG) Log.d(TAG, log);
    }

    public static final void logw(String log) {
        if (DEBUG) Log.w(TAG, log);
    }

    public static final void loge(String log) {
        //This is an actual error, so it might be important, no check for debug flag
        Log.e(TAG, log);
    }
}
