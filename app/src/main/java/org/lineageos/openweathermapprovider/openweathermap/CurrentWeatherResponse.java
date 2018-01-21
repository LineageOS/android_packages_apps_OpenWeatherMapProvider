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
import java.util.List;

import lineageos.providers.WeatherContract;

public class CurrentWeatherResponse implements Serializable {
    @SerializedName("cod")
    private int code;
    @SerializedName("name")
    private String cityName;
    private List<Weather> weather;
    private Wind wind;
    private Main main;

    public CurrentWeatherResponse() {}

    static class Weather {
        public Weather() {}
        private int id;
        String icon;
    }

    static class Wind {
        public Wind() {}
        private double speed = Double.NaN;
        private double deg = Double.NaN;
    }

    static class Main {
        public Main() {}
        private double temp = Double.NaN;
        @SerializedName("temp_min")
        private double minTemp = Double.NaN;
        @SerializedName("temp_max")
        private double maxTemp = Double.NaN;
        private double humidity = Double.NaN;

    }

    public String getCityName() {
        return cityName;
    }

    public int getInternalCode() {
        return code;
    }

    public double getTemperature() {
        return main.temp;
    }

    public double getHumidity() {
        return main.humidity;
    }

    public double getTodaysMaxTemp() {
        return main.maxTemp;
    }

    public double getTodaysMinTemp() {
        return main.minTemp;
    }

    public double getWindDirection() {
        return wind.deg;
    }

    public double getWindSpeed() {
        return wind.speed;
    }

    public int getConditionCode() {
        if (weather == null || weather.size() == 0) {
            return WeatherContract.WeatherColumns.WeatherCode.NOT_AVAILABLE;
        } else {
            return weather.get(0).id;
        }
    }

    public String getWeatherIconId() {
        if (weather == null || weather.size() == 0) {
            return "";
        } else {
            return weather.get(0).icon;
        }
    }
}
