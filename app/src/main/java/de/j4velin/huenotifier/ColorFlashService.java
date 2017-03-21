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

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;

import com.google.gson.JsonElement;
import com.philips.lighting.hue.sdk.utilities.PHUtilities;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ColorFlashService extends IntentService {

    private final static int ALERT_STATE_DURATION = 1000; // in ms
    private final List<Integer> changing_lights = new LinkedList<>();
    private HueAPI api;

    public ColorFlashService() {
        super("ColorFlashService");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        SharedPreferences prefs = getSharedPreferences("HueNotifier", MODE_PRIVATE);
        if (!prefs.contains("bridge_ip") || !prefs.contains("username")) {
            if (BuildConfig.DEBUG)
                Logger.log(
                        "ColorFlashService started but no bridge connection information found");
            stopSelf();
        } else {
            if (intent != null && intent.hasExtra("colors") && intent.hasExtra("lights")) {
                api = APIHelper.getAPI(prefs);
                int[] colors = intent.getIntArrayExtra("colors");
                int[] lights = intent.getIntArrayExtra("lights");
                int size = Math.min(colors.length, lights.length);
                for (int i = 0; i < size; i++) {
                    if (BuildConfig.DEBUG)
                        Logger.log("doColorFlash for " + lights[i]);
                    final int light = lights[i], color = colors[i];
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            doColorFlash(color, light);
                        }
                    }).start();
                }
            } else if (BuildConfig.DEBUG) {
                Logger.log(
                        "ColorFlashService started but intent does not have necessary information");
            }
        }
    }

    private void doColorFlash(final int color, final int light) {
        synchronized (changing_lights) {
            while (changing_lights.contains(light)) {
                try {
                    changing_lights.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            changing_lights.add(light);
        }
        api.getLight(light).enqueue(new Callback<Light>() {
            @Override
            public void onResponse(Call<Light> call, Response<Light> response) {
                if (BuildConfig.DEBUG)
                    Logger.log("current state: " + response.body());
                final Light.LightState currentState = response.body().state;
                Light.LightState alertState = new Light.LightState();
                alertState.on = true;
                alertState.xy =
                        PHUtilities.calculateXY(color, response.body().modelid);
                api.setLightState(light, alertState).enqueue(new Callback<List<JsonElement>>() {
                    @Override
                    public void onResponse(Call<List<JsonElement>> call,
                                           Response<List<JsonElement>> response) {
                        if (BuildConfig.DEBUG)
                            Logger.log(
                                    "set alert state response: " + Arrays
                                            .toString(response.body().toArray()));
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                api.setLightState(light, currentState)
                                        .enqueue(new Callback<List<JsonElement>>() {
                                            @Override
                                            public void onResponse(Call<List<JsonElement>> call,
                                                                   Response<List<JsonElement>> response) {
                                                if (BuildConfig.DEBUG)
                                                    Logger.log(
                                                            "revert state response: " + Arrays
                                                                    .toString(response.body()
                                                                            .toArray()));
                                                done(light);
                                            }

                                            @Override
                                            public void onFailure(Call<List<JsonElement>> call,
                                                                  Throwable t) {
                                                if (BuildConfig.DEBUG) {
                                                    Logger.log("unable to restore original state:");
                                                    Logger.log(t);
                                                }
                                                // retry
                                                api.setLightState(light, currentState)
                                                        .enqueue(new Callback<List<JsonElement>>() {
                                                            @Override
                                                            public void onResponse(
                                                                    Call<List<JsonElement>> call,
                                                                    Response<List<JsonElement>> response) {
                                                                done(light);
                                                            }

                                                            @Override
                                                            public void onFailure(
                                                                    Call<List<JsonElement>> call,
                                                                    Throwable t) {
                                                                if (BuildConfig.DEBUG)
                                                                    Logger.log(t);
                                                                done(light);
                                                            }
                                                        });
                                            }
                                        });
                            }
                        }, ALERT_STATE_DURATION);
                    }

                    @Override
                    public void onFailure(Call<List<JsonElement>> call, Throwable t) {
                        if (BuildConfig.DEBUG) {
                            Logger.log("unable to change to alert state:");
                            Logger.log(t);
                        }
                        done(light);
                    }
                });
            }

            @Override
            public void onFailure(Call<Light> call, Throwable t) {
                if (BuildConfig.DEBUG) {
                    Logger.log("unable to get original state:");
                    Logger.log(t);
                }
                done(light);
            }
        });
    }

    private void done(Integer light) {
        if (BuildConfig.DEBUG)
            Logger.log("done " + light);
        synchronized (changing_lights) {
            changing_lights.remove(light);
            changing_lights.notifyAll();
        }
    }
}
