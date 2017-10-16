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

package org.lineageos.openweathermapprovider.openweathermap;

import android.content.Context;
import android.location.Location;
import android.text.TextUtils;

import org.lineageos.openweathermapprovider.utils.Logging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cyanogenmod.providers.CMSettings;
import cyanogenmod.providers.WeatherContract;
import cyanogenmod.weather.WeatherInfo;
import cyanogenmod.weather.WeatherLocation;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenWeatherMapService {

    // OpenWeatherMap allows like or accurate, let's use like so we return more choices to the user
    private static final String SEARCH_CITY_TYPE = "like";

    private static final String METRIC_UNITS = "metric";
    private static final String IMPERIAL_UNITS = "imperial";

    private final Retrofit mRetrofit;
    private final OpenWeatherMapInterface mOpenWeatherMapInterface;
    private volatile String mApiKey;
    private Context mContext;

    public OpenWeatherMapService(Context context) {
        mContext = context;
        mRetrofit = buildRestAdapter();
        mOpenWeatherMapInterface = mRetrofit.create(OpenWeatherMapInterface.class);
    }

    public void setApiKey(String apiKey) {
        mApiKey = apiKey;
    }

    /**
     * This is a synchronous call and should never be called from the UI thread
     * @param weatherLocation
     * @throws InvalidApiKeyException If the application ID has not been set
     */
    public WeatherInfo queryWeather(WeatherLocation weatherLocation)
            throws InvalidApiKeyException {

        if (!maybeValidApiKey(mApiKey)) {
            throw new InvalidApiKeyException();
        }

        String language = getLanguageCode();
        final int tempUnit = getTempUnitFromSettings();
        final String units = mapTempUnit(tempUnit);
        Call<CurrentWeatherResponse> weatherResponseCall
                = mOpenWeatherMapInterface.queryCurrentWeather(weatherLocation.getCityId(),
                units, language, mApiKey);
        Response<CurrentWeatherResponse> currentWeatherResponse;
        try {
            Logging.logd(weatherResponseCall.request().toString());
            currentWeatherResponse = weatherResponseCall.execute();
        } catch (IOException e) {
            //An error occurred while talking to the server
            return null;
        }

        if (currentWeatherResponse.code() == 200) {
            //Query the forecast now. We can return a valid WeatherInfo object without the forecast
            //but the user is expecting both the current weather and the forecast
            Call<ForecastResponse> forecastResponseCall
                    = mOpenWeatherMapInterface.queryForecast(weatherLocation.getCityId(),
                    units, language, mApiKey);
            ForecastResponse forecastResponse = null;
            try {
                Logging.logd(forecastResponseCall.request().toString());
                Response<ForecastResponse> r = forecastResponseCall.execute();
                if (r.code() == 200) forecastResponse = r.body();
            } catch (IOException e) {
                //this is an error we can live with
                Logging.logd("IOException while requesting forecast " + e);
            }
            return processWeatherResponse(currentWeatherResponse.body(), forecastResponse,
                    tempUnit);
        } else {
            return null;
        }
    }

    /**
     * This is a synchronous call and should never be called from the UI thread
     * @param location A {@link WeatherInfo} weather info object if the call was successfully
     *                 processed by the end point, null otherwise
     * @throws InvalidApiKeyException If the application ID has not been set
     */
    public WeatherInfo queryWeather(Location location) throws InvalidApiKeyException {
        if (!maybeValidApiKey(mApiKey)) {
            throw new InvalidApiKeyException();
        }

        String language = getLanguageCode();
        final int tempUnit = getTempUnitFromSettings();
        final String units = mapTempUnit(tempUnit);
        Call<CurrentWeatherResponse> weatherResponseCall
                = mOpenWeatherMapInterface.queryCurrentWeather(location.getLatitude(),
                location.getLongitude(), units, language, mApiKey);
        Response<CurrentWeatherResponse> currentWeatherResponse;
        try {
            Logging.logd(weatherResponseCall.request().toString());
            currentWeatherResponse = weatherResponseCall.execute();
        } catch (IOException e) {
            //An error occurred while talking to the server
            Logging.logd("IOException while requesting weather " + e);
            return null;
        }

        if (currentWeatherResponse.code() == 200) {
            //Query the forecast now. We can return a valid WeatherInfo object without the forecast
            //but the user is expecting both the current weather and the forecast
            Call<ForecastResponse> forecastResponseCall
                    = mOpenWeatherMapInterface.queryForecast(location.getLatitude(),
                    location.getLongitude(), units, language, mApiKey);
            ForecastResponse forecastResponse = null;
            try {
                Logging.logd(forecastResponseCall.request().toString());
                Response<ForecastResponse> r = forecastResponseCall.execute();
                if (r.code() == 200) forecastResponse = r.body();
            } catch (IOException e) {
                //this is an error we can live with
                Logging.logd("IOException while requesting forecast " + e);
            }
            return processWeatherResponse(currentWeatherResponse.body(), forecastResponse,
                    tempUnit);
        } else {
            return null;
        }
    }

    private WeatherInfo processWeatherResponse(CurrentWeatherResponse currentWeatherResponse,
            ForecastResponse forecastResponse, int tempUnit) {

        if (currentWeatherResponse.getInternalCode() == 404) {
            //OpenWeatherMap might return 404 even if we supplied a valid lat/lon or the
            //city ID that we got by looking up a city...not our fault
            return null;
        }
        final String cityName = currentWeatherResponse.getCityName();
        final double temperature = currentWeatherResponse.getTemperature();

        //We need at least the city name and current temperature
        if (cityName == null || Double.isNaN(temperature)) return null;

        WeatherInfo.Builder builder = new WeatherInfo.Builder(cityName,
                sanitizeTemperature(temperature, true), tempUnit)
                        .setTimestamp(System.currentTimeMillis());

        builder.setWeatherCondition(mapConditionIconToCode(
                currentWeatherResponse.getWeatherIconId(),
                        currentWeatherResponse.getConditionCode()));

        final double humidity = currentWeatherResponse.getHumidity();
        if (!Double.isNaN(humidity)) {
            builder.setHumidity(humidity);
        }

        final double todaysHigh = currentWeatherResponse.getTodaysMaxTemp();
        if (!Double.isNaN(todaysHigh)) {
            builder.setTodaysHigh(todaysHigh);
        }

        final double todaysLow = currentWeatherResponse.getTodaysMinTemp();
        if (!Double.isNaN(todaysLow)) {
            builder.setTodaysLow(todaysLow);
        }

        final double windDir = currentWeatherResponse.getWindDirection();
        final double windSpeed = currentWeatherResponse.getWindSpeed();
        if (!Double.isNaN(windDir) && !Double.isNaN(windSpeed)) {
            builder.setWind(windSpeed, windDir, WeatherContract.WeatherColumns.WindSpeedUnit.KPH);
        }

        if (forecastResponse != null) {
            List<WeatherInfo.DayForecast> forecastList = new ArrayList<>();
            List<ForecastResponse.DayForecast> forecastResponses =
                    forecastResponse.getForecastList();
            double dayMinimum = Double.NaN;
            double dayMaximum = Double.NaN;
            int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            WeatherInfo.DayForecast.Builder forecastBuilder = null;
            int maxItems = forecastResponses.size();
            for (int i = 0; i < maxItems; i++) {
                ForecastResponse.DayForecast forecast = forecastResponses.get(i);
                Calendar forecastCalendar = Calendar.getInstance();
                forecastCalendar.setTimeInMillis(forecast.getTimestamp() * 1000);
                int hour = forecastCalendar.get(Calendar.HOUR_OF_DAY);
                int day = forecastCalendar.get(Calendar.DAY_OF_YEAR);

                // If the first forecast is for the next day already, add a forecast item with
                // today's values so the list is populated correctly.
                if (i == 0 && currentDay != day) {
                    forecastBuilder = new WeatherInfo.DayForecast.Builder(mapConditionIconToCode(
                            currentWeatherResponse.getWeatherIconId(),
                            currentWeatherResponse.getConditionCode()));
                    if (!Double.isNaN(todaysHigh)) {
                        forecastBuilder.setHigh(todaysHigh);
                    }
                    if (!Double.isNaN(todaysLow)) {
                        forecastBuilder.setLow(todaysLow);
                    }
                    forecastList.add(forecastBuilder.build());

                    // Remove 8 items (= 1 day) from the list so we are back at 5 days
                    maxItems -= 8;
                }

                // Results are 3 hours apart, so the first result which is before 3am indicates
                // a new day
                if (i == 0 || hour < 3) {
                    dayMinimum = Double.NaN;
                    dayMaximum = Double.NaN;
                    forecastBuilder = new WeatherInfo.DayForecast.Builder(mapConditionIconToCode(
                            forecast.getWeatherIconId(), forecast.getConditionCode()));
                }

                final double max = forecast.getMaxTemp();
                if (!Double.isNaN(max)) {
                    if (Double.isNaN(dayMaximum) || max > dayMaximum) {
                        forecastBuilder.setHigh(max);
                        dayMaximum = max;
                    }
                }

                final double min = forecast.getMinTemp();
                if (!Double.isNaN(min)) {
                    if (Double.isNaN(dayMinimum) || min < dayMinimum) {
                        forecastBuilder.setLow(min);
                        dayMinimum = min;
                    }
                }

                // If it's the last result of each day (less than 3 hours from the next day),
                // build the forecast
                if (hour >= 21) {
                    forecastList.add(forecastBuilder.build());
                }
            }
            builder.setForecast(forecastList);
        }
        return builder.build();
    }

    /**
     * This is a synchronous call and should never be called from the UI thread
     * @param cityName
     * @return Array of {@link WeatherLocation} weather locations. This method will always return a
     * list, but the list might be empty if no match was found
     * @throws InvalidApiKeyException If the application ID has not been set
     */
    public List<WeatherLocation> lookupCity(String cityName) throws InvalidApiKeyException {
        if (!maybeValidApiKey(mApiKey)) {
            throw new InvalidApiKeyException();
        }

        Call<LookupCityResponse> lookupCityCall = mOpenWeatherMapInterface.lookupCity(
                cityName, getLanguageCode(), SEARCH_CITY_TYPE, mApiKey);

        Response<LookupCityResponse> lookupResponse;
        try {
            Logging.logd(lookupCityCall.request().toString());
            lookupResponse = lookupCityCall.execute();
        } catch (IOException e) {
            Logging.logd("IOException while looking up city name " + e);
            //Return empty list to prevent NPE
            return new ArrayList<>();
        }

        if (lookupResponse != null && lookupResponse.code() == 200) {
            List<WeatherLocation> weatherLocations = new ArrayList<>();
            for (LookupCityResponse.CityInfo cityInfo: lookupResponse.body().getCityInfoList()) {
                WeatherLocation location
                        = new WeatherLocation.Builder(cityInfo.getCityId(),
                                cityInfo.getCityName()).setCountry(cityInfo.getCountry()).build();
                weatherLocations.add(location);
            }
            return weatherLocations;
        } else {
            //Return empty list to prevent NPE
            return new ArrayList<>();
        }
    }

    private Retrofit buildRestAdapter() {
        final OkHttpClient httpClient = new OkHttpClient().newBuilder().build();

        final String baseUrl = "http://api.openweathermap.org";
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    /**
     * Supported languages http://openweathermap.org/forecast5#multi
     */
    private static final Set<String> SUPPORTED_LANGUAGES = new HashSet<>();
    static {
        SUPPORTED_LANGUAGES.add("en"); //English
        SUPPORTED_LANGUAGES.add("ru"); //Russian
        SUPPORTED_LANGUAGES.add("it"); //Italian
        SUPPORTED_LANGUAGES.add("es"); //Spanish
        SUPPORTED_LANGUAGES.add("sp"); //Spanish
        SUPPORTED_LANGUAGES.add("uk"); //Ukrainian
        SUPPORTED_LANGUAGES.add("ua"); //Ukrainian
        SUPPORTED_LANGUAGES.add("de"); //German
        SUPPORTED_LANGUAGES.add("pt"); //Portuguese
        SUPPORTED_LANGUAGES.add("ro"); //Romanian
        SUPPORTED_LANGUAGES.add("pl"); //Polish
        SUPPORTED_LANGUAGES.add("fi"); //Finnish
        SUPPORTED_LANGUAGES.add("nl"); //Dutch
        SUPPORTED_LANGUAGES.add("fr"); //French
        SUPPORTED_LANGUAGES.add("bg"); //Bulgarian
        SUPPORTED_LANGUAGES.add("sv"); //Swedish
        SUPPORTED_LANGUAGES.add("se"); //Swedish
        SUPPORTED_LANGUAGES.add("zh_tw"); //Chinese Traditional
        SUPPORTED_LANGUAGES.add("zh_cn"); //Chinese Simplified
        SUPPORTED_LANGUAGES.add("tr"); //Turkish
        SUPPORTED_LANGUAGES.add("hr"); //Croatian
        SUPPORTED_LANGUAGES.add("ca"); //Catalan
    }


    private String getLanguageCode() {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String selector = locale.getLanguage();

        //Special cases
        if (TextUtils.equals(selector, "zh")) {
            selector += "_" + locale.getCountry();
        }

        if (SUPPORTED_LANGUAGES.contains(selector)) {
            return selector;
        } else {
            //Default to english
            return "en";
        }
    }

    // OpenWeatherMap sometimes returns temperatures in Kelvin even if we ask it
    // for deg C or deg F. Detect this and convert accordingly.
    private double sanitizeTemperature(double value, boolean metric) {
        // threshold chosen to work for both C and F. 170 deg F is hotter
        // than the hottest place on earth.
        if (value > 170d) {
            // K -> deg C
            value -= 273.15d;
            if (!metric) {
                // deg C -> deg F
                value = (value * 1.8d) + 32d;
            }
        }
        return value;
    }

    private static final HashMap<String, Integer> ICON_MAPPING = new HashMap<>();
    static {
        ICON_MAPPING.put("01d", WeatherContract.WeatherColumns.WeatherCode.SUNNY);
        ICON_MAPPING.put("01n", WeatherContract.WeatherColumns.WeatherCode.CLEAR_NIGHT);
        ICON_MAPPING.put("02d", WeatherContract.WeatherColumns.WeatherCode.PARTLY_CLOUDY_DAY);
        ICON_MAPPING.put("02n", WeatherContract.WeatherColumns.WeatherCode.PARTLY_CLOUDY_NIGHT);
        ICON_MAPPING.put("03d", WeatherContract.WeatherColumns.WeatherCode.CLOUDY);
        ICON_MAPPING.put("03n", WeatherContract.WeatherColumns.WeatherCode.CLOUDY);
        ICON_MAPPING.put("04d", WeatherContract.WeatherColumns.WeatherCode.MOSTLY_CLOUDY_DAY);
        ICON_MAPPING.put("04n", WeatherContract.WeatherColumns.WeatherCode.MOSTLY_CLOUDY_NIGHT);
        ICON_MAPPING.put("09d", WeatherContract.WeatherColumns.WeatherCode.SHOWERS);
        ICON_MAPPING.put("09n", WeatherContract.WeatherColumns.WeatherCode.SHOWERS);
        ICON_MAPPING.put("10d", WeatherContract.WeatherColumns.WeatherCode.SCATTERED_SHOWERS);
        ICON_MAPPING.put("10n", WeatherContract.WeatherColumns.WeatherCode.THUNDERSHOWER);
        ICON_MAPPING.put("11d", WeatherContract.WeatherColumns.WeatherCode.THUNDERSTORMS);
        ICON_MAPPING.put("11n", WeatherContract.WeatherColumns.WeatherCode.THUNDERSTORMS);
        ICON_MAPPING.put("13d", WeatherContract.WeatherColumns.WeatherCode.SNOW);
        ICON_MAPPING.put("13n", WeatherContract.WeatherColumns.WeatherCode.SNOW);
        ICON_MAPPING.put("50d", WeatherContract.WeatherColumns.WeatherCode.HAZE);
        ICON_MAPPING.put("50n", WeatherContract.WeatherColumns.WeatherCode.FOGGY);
    }

    private int mapConditionIconToCode(String icon, int conditionId) {

        // First, use condition ID for specific cases
        switch (conditionId) {
            // Thunderstorms
            case 202:   // thunderstorm with heavy rain
            case 232:   // thunderstorm with heavy drizzle
            case 211:   // thunderstorm
                return WeatherContract.WeatherColumns.WeatherCode.THUNDERSTORMS;
            case 212:   // heavy thunderstorm
                return WeatherContract.WeatherColumns.WeatherCode.HURRICANE;
            case 221:   // ragged thunderstorm
            case 231:   // thunderstorm with drizzle
            case 201:   // thunderstorm with rain
                return WeatherContract.WeatherColumns.WeatherCode.SCATTERED_THUNDERSTORMS;
            case 230:   // thunderstorm with light drizzle
            case 200:   // thunderstorm with light rain
            case 210:   // light thunderstorm
                return WeatherContract.WeatherColumns.WeatherCode.ISOLATED_THUNDERSTORMS;

            // Drizzle
            case 300:   // light intensity drizzle
            case 301:   // drizzle
            case 302:   // heavy intensity drizzle
            case 310:   // light intensity drizzle rain
            case 311:   // drizzle rain
            case 312:   // heavy intensity drizzle rain
            case 313:   // shower rain and drizzle
            case 314:   // heavy shower rain and drizzle
            case 321:   // shower drizzle
                return WeatherContract.WeatherColumns.WeatherCode.DRIZZLE;

            // Rain
            case 500:   // light rain
            case 501:   // moderate rain
            case 520:   // light intensity shower rain
            case 521:   // shower rain
            case 531:   // ragged shower rain
            case 502:   // heavy intensity rain
            case 503:   // very heavy rain
            case 504:   // extreme rain
            case 522:   // heavy intensity shower rain
                return WeatherContract.WeatherColumns.WeatherCode.SHOWERS;
            case 511:   // freezing rain
                return WeatherContract.WeatherColumns.WeatherCode.FREEZING_RAIN;

            // Snow
            case 600: case 620: // light snow
                return WeatherContract.WeatherColumns.WeatherCode.LIGHT_SNOW_SHOWERS;
            case 601: case 621: // snow
                return WeatherContract.WeatherColumns.WeatherCode.SNOW;
            case 602: case 622: // heavy snow
                return WeatherContract.WeatherColumns.WeatherCode.HEAVY_SNOW;
            case 611: case 612: // sleet
                return WeatherContract.WeatherColumns.WeatherCode.SLEET;
            case 615: case 616: // rain and snow
                return WeatherContract.WeatherColumns.WeatherCode.MIXED_RAIN_AND_SNOW;

            // Atmosphere
            case 741:   // fog
                return WeatherContract.WeatherColumns.WeatherCode.FOGGY;
            case 711:   // smoke
            case 762:   // volcanic ash
                return WeatherContract.WeatherColumns.WeatherCode.SMOKY;
            case 701:   // mist
            case 721:   // haze
                return WeatherContract.WeatherColumns.WeatherCode.HAZE;
            case 731:   // sand/dust whirls
            case 751:   // sand
            case 761:   // dust
                return WeatherContract.WeatherColumns.WeatherCode.DUST;
            case 771:   // squalls
                return WeatherContract.WeatherColumns.WeatherCode.BLUSTERY;
            case 781:   // tornado
                return WeatherContract.WeatherColumns.WeatherCode.TORNADO;

            // Extreme
            case 900:   // tornado
                return WeatherContract.WeatherColumns.WeatherCode.TORNADO;
            case 901:   // tropical storm
                return WeatherContract.WeatherColumns.WeatherCode.TROPICAL_STORM;
            case 902:   // hurricane
                return WeatherContract.WeatherColumns.WeatherCode.HURRICANE;
            case 903:   // cold
                return WeatherContract.WeatherColumns.WeatherCode.COLD;
            case 904:   // hot
                return WeatherContract.WeatherColumns.WeatherCode.HOT;
            case 905:   // windy
                return WeatherContract.WeatherColumns.WeatherCode.WINDY;
            case 906:   // hail
                return WeatherContract.WeatherColumns.WeatherCode.HAIL;
        }

        // Not yet handled - Use generic icon mapping
        Integer condition = ICON_MAPPING.get(icon);
        if (condition != null) {
            return condition;
        }

        return WeatherContract.WeatherColumns.WeatherCode.NOT_AVAILABLE;
    }


    public final static class InvalidApiKeyException extends Exception {

        public InvalidApiKeyException() {
            super("A valid API key is required to process the request");
        }
    }

    private boolean maybeValidApiKey(String key) {
        return key != null && !TextUtils.equals(key, "");
    }

    private int getTempUnitFromSettings() {
        try {
            final int tempUnit = CMSettings.Global.getInt(mContext.getContentResolver(),
                    CMSettings.Global.WEATHER_TEMPERATURE_UNIT);
            return tempUnit;
        } catch (CMSettings.CMSettingNotFoundException e) {
            //Default to metric
            return WeatherContract.WeatherColumns.TempUnit.CELSIUS;
        }
    }

    private String mapTempUnit(int tempUnit) {
        switch (tempUnit) {
            case WeatherContract.WeatherColumns.TempUnit.FAHRENHEIT:
                return IMPERIAL_UNITS;
            case WeatherContract.WeatherColumns.TempUnit.CELSIUS:
                return METRIC_UNITS;
            default:
                //In the unlikely case we receive an unknown temp unit, return empty string
                //to avoid sending an invalid argument in the request
                return "";
        }
    }
}
