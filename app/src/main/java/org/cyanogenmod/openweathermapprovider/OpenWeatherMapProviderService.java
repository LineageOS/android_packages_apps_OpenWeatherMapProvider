/*
 *  Copyright (C) 2016 The CyanogenMod Project
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

import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import org.cyanogenmod.openweathermapprovider.openweathermap.OpenWeatherMapService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cyanogenmod.weather.CMWeatherManager;
import cyanogenmod.weather.RequestInfo;
import cyanogenmod.weather.WeatherInfo;
import cyanogenmod.weather.WeatherLocation;
import cyanogenmod.weatherservice.ServiceRequest;
import cyanogenmod.weatherservice.ServiceRequestResult;
import cyanogenmod.weatherservice.WeatherProviderService;

import static org.cyanogenmod.openweathermapprovider.utils.Logging.logd;
import static org.cyanogenmod.openweathermapprovider.utils.Logging.logw;

public class OpenWeatherMapProviderService extends WeatherProviderService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String API_KEY = "api_key";
    private static final String API_KEY_VERIFIED_STATE = "api_key_verified_state";

    private static final int API_KEY_INVALID = 0;
    private static final int API_KEY_VERIFIED = 2;

    private OpenWeatherMapService mOpenWeatherMapService;

    private Map<ServiceRequest,WeatherUpdateRequestTask> mWeatherUpdateRequestMap = new HashMap<>();
    private Map<ServiceRequest,LookupCityNameRequestTask> mLookupCityRequestMap = new HashMap<>();
    //OpenWeatherMap recommends to wait 10 min between requests
    private final static long REQUEST_THRESHOLD = 1000L * 60L * 10L;
    private long mLastRequestTimestamp = -REQUEST_THRESHOLD;
    private WeatherLocation mLastWeatherLocation;
    private Location mLastLocation;
    //5km of threshold, the weather won't change that much in such short distance
    private static final float LOCATION_DISTANCE_METERS_THRESHOLD = 5f * 1000f;

    @Override
    public void onCreate() {
        mOpenWeatherMapService = new OpenWeatherMapService(this);
    }

    @Override
    public void onConnected() {
        final SharedPreferences preferences
                = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        final String mApiId = preferences.getString(API_KEY, null);
        mOpenWeatherMapService.setApiKey(mApiId);
    }

    @Override
    public void onDisconnected() {
        final SharedPreferences preferences
                = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onRequestSubmitted(ServiceRequest request) {
        RequestInfo requestInfo = request.getRequestInfo();
        int requestType = requestInfo.getRequestType();
        logd("Received request type " + requestType);

        if (((requestType == RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ &&
                isSameGeoLocation(requestInfo.getLocation(), mLastLocation))
                || (requestType == RequestInfo.TYPE_WEATHER_BY_WEATHER_LOCATION_REQ &&
                isSameWeatherLocation(requestInfo.getWeatherLocation(),
                        mLastWeatherLocation))) && wasRequestSubmittedTooSoon()) {
            request.reject(CMWeatherManager.RequestStatus.SUBMITTED_TOO_SOON);
            return;
        }

        switch (requestType) {
            case RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ:
            case RequestInfo.TYPE_WEATHER_BY_WEATHER_LOCATION_REQ:
                synchronized (mWeatherUpdateRequestMap) {
                    WeatherUpdateRequestTask updateTask
                            = new WeatherUpdateRequestTask(request);
                    mWeatherUpdateRequestMap.put(request, updateTask);
                    mLastRequestTimestamp = SystemClock.elapsedRealtime();
                    updateTask.execute();
                }
                break;
            case RequestInfo.TYPE_LOOKUP_CITY_NAME_REQ:
                synchronized (mLookupCityRequestMap) {
                    LookupCityNameRequestTask lookupTask = new LookupCityNameRequestTask(request);
                    mLookupCityRequestMap.put(request, lookupTask);
                    lookupTask.execute();
                }
                break;
        }
    }

    @Override
    protected void onRequestCancelled(ServiceRequest request) {
        switch (request.getRequestInfo().getRequestType()) {
            case RequestInfo.TYPE_WEATHER_BY_WEATHER_LOCATION_REQ:
            case RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ:
                synchronized (mWeatherUpdateRequestMap) {
                    WeatherUpdateRequestTask task = mWeatherUpdateRequestMap.remove(request);
                    if (task != null) {
                        task.cancel(true);
                    }
                    return;
                }
            case RequestInfo.TYPE_LOOKUP_CITY_NAME_REQ:
                synchronized (mLookupCityRequestMap) {
                    LookupCityNameRequestTask task = mLookupCityRequestMap.remove(request);
                    if (task != null) {
                        task.cancel(true);
                    }
                }
                return;
            default:
                logw("Received unknown request type "
                        + request.getRequestInfo().getRequestType());
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(API_KEY)) {
            logd("API key has changed");
            final String mApiKey = sharedPreferences.getString(key, null);
            mOpenWeatherMapService.setApiKey(mApiKey);
        }
    }
    private boolean isSameWeatherLocation(WeatherLocation newLocation,
            WeatherLocation oldLocation) {
        if (newLocation == null || oldLocation == null) return false;
        return (newLocation.getCityId().equals(oldLocation.getCityId())
                && newLocation.getCity().equals(oldLocation.getCity())
                && newLocation.getPostalCode().equals(oldLocation.getPostalCode())
                && newLocation.getCountry().equals(oldLocation.getCountry())
                && newLocation.getCountryId().equals(oldLocation.getCountryId()));
    }

    private boolean isSameGeoLocation(Location newLocation, Location oldLocation) {
        if (newLocation == null || oldLocation == null) return false;
        float distance = newLocation.distanceTo(oldLocation);
        logd("Distance between locations " + distance);
        return (distance < LOCATION_DISTANCE_METERS_THRESHOLD);
    }

    private boolean wasRequestSubmittedTooSoon() {
        final long now = SystemClock.elapsedRealtime();
        logd("Now " + now + " last request " + mLastRequestTimestamp);
        return (mLastRequestTimestamp + REQUEST_THRESHOLD > now);
    }

    private class WeatherUpdateRequestTask extends AsyncTask<Void, Void, WeatherInfo> {

        final private ServiceRequest mRequest;

        public WeatherUpdateRequestTask(ServiceRequest request) {
            mRequest = request;
        }

        @Override
        protected WeatherInfo doInBackground(Void... params) {
            RequestInfo requestInfo = mRequest.getRequestInfo();
            int requestType = requestInfo.getRequestType();
            if (requestType == RequestInfo.TYPE_WEATHER_BY_WEATHER_LOCATION_REQ) {
                try {
                    return mOpenWeatherMapService.queryWeather(requestInfo.getWeatherLocation());
                } catch (OpenWeatherMapService.InvalidApiKeyException e) {
                    setApiKeyVerified(API_KEY_INVALID);
                    return null;
                }
            } else if (requestType == RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ) {
                try {
                    return mOpenWeatherMapService.queryWeather(requestInfo.getLocation());
                } catch (OpenWeatherMapService.InvalidApiKeyException e) {
                    setApiKeyVerified(API_KEY_INVALID);
                    return null;
                }
            } else {
                // We don't know how to handle any other type of request
                logw("Received unknown request type "+ requestType);
                return null;
            }
        }

        @Override
        protected void onPostExecute(WeatherInfo weatherInfo) {
            if (weatherInfo == null) {
                logd("Received null weather info, failing request");
                mRequest.fail();
            } else {
                logd(weatherInfo.toString());
                ServiceRequestResult result = new ServiceRequestResult.Builder(weatherInfo).build();
                mRequest.complete(result);
                if (mRequest.getRequestInfo().getRequestType()
                        == RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ) {
                    mLastLocation = mRequest.getRequestInfo().getLocation();
                } else {
                    mLastWeatherLocation = mRequest.getRequestInfo().getWeatherLocation();
                }
                setApiKeyVerified(API_KEY_VERIFIED);
            }
            synchronized (mWeatherUpdateRequestMap) {
                mWeatherUpdateRequestMap.remove(mRequest);
            }
        }
    }

    private class LookupCityNameRequestTask extends AsyncTask<Void, Void, List<WeatherLocation>> {

        final private ServiceRequest mRequest;

        public LookupCityNameRequestTask(ServiceRequest request) {
            mRequest = request;
        }

        @Override
        protected List<WeatherLocation> doInBackground(Void... params) {
            RequestInfo requestInfo = mRequest.getRequestInfo();
            if (requestInfo.getRequestType() != RequestInfo.TYPE_LOOKUP_CITY_NAME_REQ) {
                logw("Received unsupported request type " + requestInfo.getRequestType());
                return null;
            }
            try {
                return mOpenWeatherMapService.lookupCity(mRequest.getRequestInfo().getCityName());
            } catch (OpenWeatherMapService.InvalidApiKeyException e) {
                setApiKeyVerified(API_KEY_INVALID);
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<WeatherLocation> locations) {
            if (locations != null) {
                for (WeatherLocation location : locations) {
                    logd(location.toString());
                }
                ServiceRequestResult request = new ServiceRequestResult.Builder(locations).build();
                mRequest.complete(request);
                setApiKeyVerified(API_KEY_VERIFIED);
            } else {
                mRequest.fail();
            }
            synchronized (mLookupCityRequestMap) {
                mLookupCityRequestMap.remove(mRequest);
            }
        }
    }

    private void setApiKeyVerified(int state) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putInt(API_KEY_VERIFIED_STATE, state).apply();
    }
}
