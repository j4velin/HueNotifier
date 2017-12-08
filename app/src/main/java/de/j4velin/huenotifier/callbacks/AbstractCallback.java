package de.j4velin.huenotifier.callbacks;

import de.j4velin.huenotifier.BuildConfig;
import de.j4velin.huenotifier.HueAPI;
import de.j4velin.huenotifier.Logger;
import retrofit2.Call;
import retrofit2.Callback;

/**
 * Base for all callback classes.
 * <p>
 * Call order:
 * GetCurrent -> SetAlert -> Revert -> RetryRevert
 */
public abstract class AbstractCallback<T> implements Callback<T> {
    final int light;
    final FlashService service;

    AbstractCallback(int light, FlashService service) {
        this.light = light;
        this.service = service;
    }

    @Override
    public void onFailure(Call<T> call, Throwable t) {
        if (BuildConfig.DEBUG) {
            Logger.log("failure in callback " + this.getClass().getSimpleName());
            Logger.log(t);
        }
        service.lightDone(light);
    }

    public interface FlashService {
        void lightDone(Integer light);

        HueAPI getApi();
    }
}
