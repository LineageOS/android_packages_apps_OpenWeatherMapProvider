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

package org.cyanogenmod.openweathermapprovider;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private static final String API_KEY = "api_key";
    private static final String API_KEY_VERIFIED_STATE = "api_key_verified_state";

    private static final int API_KEY_PENDING_VERIFICATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new ServicePrefsFragment())
                .commit();
    }

    public static class ServicePrefsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

        private EditTextPreference mApiKeyPreference;

        @Override
        public void onCreate(Bundle savedInstance) {
            super.onCreate(savedInstance);
            addPreferencesFromResource(R.xml.preferences);

            mApiKeyPreference = (EditTextPreference) findPreference(API_KEY);
            SharedPreferences sharedPreferences
                    = PreferenceManager.getDefaultSharedPreferences(getActivity());
            int apiKeyVerificationState = sharedPreferences.getInt(API_KEY_VERIFIED_STATE, -1);
            try {
                //lookup the value state
                String[] stateEntries
                        = getResources().getStringArray(R.array.api_key_states_entries);
                String state = stateEntries[apiKeyVerificationState];
                mApiKeyPreference.setSummary(state);
            } catch (IndexOutOfBoundsException e) {
                mApiKeyPreference.setSummary(getString(R.string.prefscreen_api_key_summary,
                        getString(R.string.app_name)));
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            Context context = getActivity();
            if (context != null) {
                SharedPreferences sp = getPreferenceManager().getSharedPreferences();
                String apiKey = sp.getString(API_KEY, null);
                if (apiKey == null || apiKey.equals("")) {
                    Toast.makeText(context, getString(R.string.api_key_not_set_message,
                            getString(R.string.app_name)), Toast.LENGTH_LONG).show();
                }
                mApiKeyPreference.setOnPreferenceChangeListener(this);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            switch (preference.getKey()) {
                case API_KEY:
                    SharedPreferences sharedPreferences
                            = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    sharedPreferences.edit().putInt(API_KEY_VERIFIED_STATE,
                            API_KEY_PENDING_VERIFICATION).apply();
                    mApiKeyPreference.setSummary(getResources().getStringArray(
                            R.array.api_key_states_entries)[API_KEY_PENDING_VERIFICATION]);
                    Toast.makeText(getActivity(), R.string.api_key_changed_verification_warning,
                            Toast.LENGTH_LONG).show();
                    return true;
            }
            return false;
        }
    }
}
