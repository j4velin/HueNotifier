package de.j4velin.huenotifier;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.philips.lighting.hue.sdk.utilities.PHUtilities;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ConnectionService extends IntentService {

    private HueAPI api;

    public ConnectionService() {
        super("ColorFlashService");
    }

    private final List<Integer> changing_lights = new LinkedList<>();

    @Override
    protected void onHandleIntent(final Intent intent) {
        SharedPreferences prefs = getSharedPreferences("HueNotifier", MODE_PRIVATE);
        if (!prefs.contains("bridge_ip") || !prefs.contains("username")) {
            if (BuildConfig.DEBUG)
                android.util.Log.e(MainActivity.TAG, "ConnectionService started but no bridge connection information found");
            stopSelf();
        } else {
            if (intent != null && intent.hasExtra("colors") && intent.hasExtra("lights")) {
                Gson gson = new GsonBuilder().setLenient().create();
                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl("http://" + prefs.getString("bridge_ip", null) + "/api/" + prefs.getString("username", null) + "/")
                        .addConverterFactory(GsonConverterFactory.create(gson))
                        .build();
                api = retrofit.create(HueAPI.class);
                int[] colors = intent.getIntArrayExtra("colors");
                int[] lights = intent.getIntArrayExtra("lights");
                int size = Math.min(colors.length, lights.length);
                for (int i = 0; i < size; i++) {
                    if (BuildConfig.DEBUG)
                        android.util.Log.d(MainActivity.TAG, "doColorFlash for " + lights[i]);
                    final int light = lights[i], color = colors[i];
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            doColorFlash(color, light);
                        }
                    }).start();
                }
            } else if (BuildConfig.DEBUG) {
                android.util.Log.e(MainActivity.TAG, "ConnectionService started but intent does not have necessary information");
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
                    android.util.Log.d(MainActivity.TAG, "current state: " + response.body());
                final Light.LightState currentState = response.body().state;
                Light.LightState alertState = new Light.LightState();
                alertState.on = true;
                alertState.xy =
                        PHUtilities.calculateXY(color, response.body().modelid);
                api.setLightState(light, alertState).enqueue(new Callback<List<JsonElement>>() {
                    @Override
                    public void onResponse(Call<List<JsonElement>> call, Response<List<JsonElement>> response) {
                        if (BuildConfig.DEBUG)
                            android.util.Log.d(MainActivity.TAG, "response2: " + Arrays.toString(response.body().toArray()));
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                api.setLightState(light, currentState).enqueue(new Callback<List<JsonElement>>() {
                                    @Override
                                    public void onResponse(Call<List<JsonElement>> call, Response<List<JsonElement>> response) {
                                        if (BuildConfig.DEBUG)
                                            android.util.Log.d(MainActivity.TAG, "response3: " + Arrays.toString(response.body().toArray()));
                                        done(light);
                                    }

                                    @Override
                                    public void onFailure(Call<List<JsonElement>> call, Throwable t) {
                                        if (BuildConfig.DEBUG)
                                            android.util.Log.e(MainActivity.TAG, "failure in: " + t.getMessage());
                                        t.printStackTrace();
                                        done(light);
                                    }
                                });
                            }
                        }, 1000);
                    }

                    @Override
                    public void onFailure(Call<List<JsonElement>> call, Throwable t) {
                        if (BuildConfig.DEBUG)
                            android.util.Log.e(MainActivity.TAG, "failure out: " + t.getMessage());
                        t.printStackTrace();
                        done(light);
                    }
                });
            }

            @Override
            public void onFailure(Call<Light> call, Throwable t) {
                if (BuildConfig.DEBUG)
                    android.util.Log.e(MainActivity.TAG, "failure: " + t.getMessage());
                t.printStackTrace();
            }
        });
    }

    private void done(Integer light) {
        if (BuildConfig.DEBUG)
            android.util.Log.d(MainActivity.TAG, "done " + light);
        synchronized (changing_lights) {
            changing_lights.remove(light);
            changing_lights.notifyAll();
        }
    }
}
