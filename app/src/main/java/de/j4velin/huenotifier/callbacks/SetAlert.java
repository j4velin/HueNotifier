package de.j4velin.huenotifier.callbacks;

import android.os.Handler;

import com.google.gson.JsonElement;

import java.util.Arrays;
import java.util.List;

import de.j4velin.huenotifier.BuildConfig;
import de.j4velin.huenotifier.ColorFlashService;
import de.j4velin.huenotifier.Light;
import de.j4velin.huenotifier.Logger;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Callback to retrieve the result of setting the alert state
 */
class SetAlert extends AbstractCallback<List<JsonElement>> {
    private final Light.LightState originalState;

    SetAlert(int light, Light.LightState originalState, FlashService service) {
        super(light, service);
        this.originalState = originalState;
    }

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
                service.getApi().setLightState(light, originalState)
                        .enqueue(new Revert(light, originalState, service));
            }
        }, ColorFlashService.ALERT_STATE_DURATION);
    }
}