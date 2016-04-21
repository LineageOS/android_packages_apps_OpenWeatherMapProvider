/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.cyanogenmod.openweathermapprovider;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private final static String API_KEY = "api_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new ServicePrefsFragment())
                .commit();
    }

    public static class ServicePrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstance) {
            super.onCreate(savedInstance);
            addPreferencesFromResource(R.xml.preferences);

            //Format some strings with arguments
            EditTextPreference apiKey = (EditTextPreference)findPreference("api_key");
            apiKey.setSummary(getString(R.string.prefscreen_api_key_summary,
                    getString(R.string.app_name)));

            Preference copyright = findPreference("copyright");
            copyright.setSummary(getString(R.string.prefscreen_copyright_summary,
                    getString(R.string.openweathermap_inc_name)));
        }

        @Override
        public void onResume() {
            super.onResume();
            Context context = getActivity();
            if (context != null) {
                String apiKey = getPreferenceManager().getSharedPreferences()
                        .getString(API_KEY, null);
                if (apiKey == null || apiKey.equals("")) {
                    Toast.makeText(context, getString(R.string.api_key_not_set_message,
                            getString(R.string.app_name)), Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
