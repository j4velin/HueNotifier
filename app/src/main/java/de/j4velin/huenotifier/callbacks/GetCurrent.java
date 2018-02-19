package de.j4velin.huenotifier.callbacks;

import com.philips.lighting.hue.sdk.utilities.PHUtilities;

import de.j4velin.huenotifier.BuildConfig;
import de.j4velin.huenotifier.Light;
import de.j4velin.huenotifier.Logger;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Callback to retrieve the current state of a light
 */
public class GetCurrent extends AbstractCallback<Light> {
    private final boolean flashOnlyIfLightsOn;
    private final int color;

    public GetCurrent(int light, boolean flashOnlyIfLightsOn, int color, FlashService service) {
        super(light, service);
        this.flashOnlyIfLightsOn = flashOnlyIfLightsOn;
        this.color = color;
    }

    @Override
    public void onResponse(Call<Light> call, Response<Light> response) {
        if (BuildConfig.DEBUG)
            Logger.log("current state: " + response.body());
        if (response.isSuccessful()) {
            final Light.LightState originalState = response.body().state;
            if (!flashOnlyIfLightsOn || originalState.on) {
                Light.LightState alertState = new Light.LightState();
                alertState.on = true;
                alertState.xy =
                        PHUtilities.calculateXY(color, response.body().modelid);
                service.getApi().setLightState(light, alertState).enqueue(
                        new SetAlert(light, originalState, service));
            }
        } else if (BuildConfig.DEBUG) {
            Logger.log("error getting current state: " + response.message() + " " + response
                    .errorBody());
        }
    }
}