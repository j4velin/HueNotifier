package de.j4velin.huenotifier.callbacks;

import com.google.gson.JsonElement;

import java.util.List;

import de.j4velin.huenotifier.BuildConfig;
import de.j4velin.huenotifier.Logger;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Callback to retrieve the result of retrying to reset the light state
 */
class RetryRevert extends AbstractCallback<List<JsonElement>> {
    RetryRevert(int light, FlashService service) {
        super(light, service);
    }

    @Override
    public void onResponse(
            Call<List<JsonElement>> call,
            Response<List<JsonElement>> response) {
        service.lightDone(light);
    }

    @Override
    public void onFailure(
            Call<List<JsonElement>> call,
            Throwable t) {
        if (BuildConfig.DEBUG)
            Logger.log(t);
        service.lightDone(light);
    }
}