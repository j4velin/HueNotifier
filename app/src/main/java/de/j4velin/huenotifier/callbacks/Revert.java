package de.j4velin.huenotifier.callbacks;

import com.google.gson.JsonElement;

import java.util.Arrays;
import java.util.List;

import de.j4velin.huenotifier.BuildConfig;
import de.j4velin.huenotifier.Light;
import de.j4velin.huenotifier.Logger;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Callback to retrieve the result of resetting the light state to the original state
 */
class Revert extends AbstractCallback<List<JsonElement>> {
    private final Light.LightState originalState;

    Revert(int light, Light.LightState originalState, FlashService service) {
        super(light, service);
        this.originalState = originalState;
    }

    @Override
    public void onResponse(Call<List<JsonElement>> call,
                           Response<List<JsonElement>> response) {
        if (BuildConfig.DEBUG)
            Logger.log(
                    "revert state response: " + Arrays
                            .toString(response.body()
                                    .toArray()));
        service.lightDone(light);
    }

    @Override
    public void onFailure(Call<List<JsonElement>> call,
                          Throwable t) {
        if (BuildConfig.DEBUG) {
            Logger.log("unable to restore original state:");
            Logger.log(t);
        }
        // retry
        service.getApi().setLightState(light, originalState)
                .enqueue(new RetryRevert(light, service));
    }
}