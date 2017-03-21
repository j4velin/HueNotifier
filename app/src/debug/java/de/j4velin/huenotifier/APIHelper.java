/*
 * Copyright 2017 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.j4velin.huenotifier;

import android.content.SharedPreferences;

import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

abstract class APIHelper {

    private APIHelper() {
    }

    static HueAPI getAPI(final SharedPreferences prefs) {
        Retrofit.Builder builder = new Retrofit.Builder();
        insertInterceptor(builder);
        return builder.baseUrl(
                "http://" + prefs.getString("bridge_ip", null) + "/api/" + prefs
                        .getString("username", null) + "/")
                .addConverterFactory(GsonConverterFactory
                        .create(new GsonBuilder().setLenient().create())).
                        build().create(HueAPI.class);
    }

    private static void insertInterceptor(Retrofit.Builder builder) {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(
                new HttpLoggingInterceptor.Logger() {
                    @Override
                    public void log(String message) {
                        Logger.log(message);
                    }
                });
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        builder.client(new OkHttpClient.Builder().addInterceptor(interceptor).build());
    }
}
