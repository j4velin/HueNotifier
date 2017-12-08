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
import android.preference.PreferenceManager;

import java.util.LinkedList;
import java.util.List;

import de.j4velin.huenotifier.callbacks.AbstractCallback;
import de.j4velin.huenotifier.callbacks.GetCurrent;

public class ColorFlashService extends IntentService implements AbstractCallback.FlashService {

    public final static int ALERT_STATE_DURATION = 1000; // in ms
    private final static List<Integer> changing_lights = new LinkedList<>();
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
                final boolean flashOnlyIfLightsOn = intent.hasExtra("flashOnlyIfLightsOn") ? intent
                        .getBooleanExtra("flashOnlyIfLightsOn", false) : PreferenceManager
                        .getDefaultSharedPreferences(
                                this).getBoolean("flashOnlyIfLightsOn", false);
                int size = Math.min(colors.length, lights.length);
                for (int i = 0; i < size; i++) {
                    if (BuildConfig.DEBUG)
                        Logger.log("doColorFlash for " + lights[i]);
                    final int light = lights[i], color = colors[i];
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            doColorFlash(color, light, flashOnlyIfLightsOn);
                        }
                    }).start();
                }
            } else if (BuildConfig.DEBUG) {
                Logger.log(
                        "ColorFlashService started but intent does not have necessary information");
            }
        }
    }

    private void doColorFlash(final int color, final int light, final boolean flashOnlyIfLightsOn) {
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
        api.getLight(light).enqueue(new GetCurrent(light, flashOnlyIfLightsOn, color, this));
    }

    @Override
    public void lightDone(Integer light) {
        if (BuildConfig.DEBUG)
            Logger.log("done " + light);
        synchronized (changing_lights) {
            changing_lights.remove(light);
            changing_lights.notifyAll();
        }
    }

    @Override
    public HueAPI getApi() {
        return api;
    }
}
