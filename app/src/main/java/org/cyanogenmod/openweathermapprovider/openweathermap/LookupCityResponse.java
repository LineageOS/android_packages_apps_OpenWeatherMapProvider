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

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class LookupCityResponse implements Serializable {

    @SerializedName("list")
    private List<CityInfo> cities;
    static class CityInfo {
        private String id = "";
        private String name = "";
        private Sys sys;

        static class Sys {
            private String country = "";
        }

        public String getCityId() {
            return id;
        }

        public String getCityName() {
            return name;
        }

        public String getCountry() {
            if (sys != null) {
                return sys.country;
            } else {
                return "";
            }
        }
    }

    public List<CityInfo> getCityInfoList() {
        if (cities == null) {
            //Return empty list to prevent NPE
            return new ArrayList<>();
        } else {
            return cities;
        }
    }
}
