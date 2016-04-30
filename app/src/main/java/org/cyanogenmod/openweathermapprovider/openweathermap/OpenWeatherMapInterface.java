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

package org.cyanogenmod.openweathermapprovider.openweathermap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenWeatherMapInterface {
    @GET("/data/2.5/weather")
    Call<CurrentWeatherResponse> queryCurrentWeather(@Query("id") String cityId,
        @Query("mode") String mode, @Query("units") String units, @Query("lang") String lang,
            @Query("appid") String appid);

    @GET("/data/2.5/weather")
    Call<CurrentWeatherResponse> queryCurrentWeather(@Query("lat") double lat,
        @Query("lon") double lon, @Query("mode") String mode, @Query("units") String units,
            @Query("lang") String lang, @Query("appid") String appid);

    @GET("/data/2.5/forecast/daily")
    Call<ForecastResponse> queryForecast(@Query("id") String cityId, @Query("mode") String mode,
        @Query("units") String units, @Query("lang") String lang, @Query("cnt") int daysCount,
            @Query("appid") String appid);

    @GET("/data/2.5/forecast/daily")
    Call<ForecastResponse> queryForecast(@Query("lat") double lat, @Query("lon") double lon,
        @Query("mode") String mode, @Query("units") String units, @Query("lang") String lang,
            @Query("cnt") int daysCount, @Query("appid") String appid);

    @GET("/data/2.5/find")
    Call<LookupCityResponse> lookupCity(@Query("q") String cityName, @Query("mode") String mode,
        @Query("lang") String lang, @Query("type") String searchType, @Query("appid") String appid);
}
