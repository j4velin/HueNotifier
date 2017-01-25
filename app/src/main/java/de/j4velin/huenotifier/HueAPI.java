package de.j4velin.huenotifier;

import com.google.gson.JsonElement;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface HueAPI {

    @GET("lights/{id}")
    Call<Light> getLight(@Path("id") int id);

    @PUT("lights/{id}/state")
    Call<List<JsonElement>> setLightState(@Path("id") int id, @Body Light.LightState state);

}
