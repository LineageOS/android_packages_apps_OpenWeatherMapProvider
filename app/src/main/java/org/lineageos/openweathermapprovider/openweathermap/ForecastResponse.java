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

package org.lineageos.openweathermapprovider.openweathermap;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cyanogenmod.providers.WeatherContract;

public class ForecastResponse implements Serializable {

    @SerializedName("list")
    private List<DayForecast> forecastList;

    public ForecastResponse() {}

    static class DayForecast {

        private List<Weather> weather;
        private Temp temp;

        public DayForecast() {}
        static class Weather {
            @SerializedName("id")
            private int code = WeatherContract.WeatherColumns.WeatherCode.NOT_AVAILABLE;
            private String icon;

            public Weather() {}
        }

        static class Temp {
            double min = Double.NaN;
            double max = Double.NaN;

            public Temp() {}
        }

        public int getConditionCode() {
            if (weather == null || weather.size() == 0) {
                return WeatherContract.WeatherColumns.WeatherCode.NOT_AVAILABLE;
            } else {
                return weather.get(0).code;
            }
        }

        public String getWeatherIconId() {
            if (weather == null || weather.size() == 0) {
                return "";
            } else {
                return weather.get(0).icon;
            }
        }

        public double getMinTemp() {
            return temp.min;
        }

        public double getMaxTemp() {
            return temp.max;
        }
    }

    public List<DayForecast> getForecastList() {
        if (forecastList == null) {
            //return an empty list to prevent NPE
            return new ArrayList<>();
        } else {
            return forecastList;
        }
    }
}
