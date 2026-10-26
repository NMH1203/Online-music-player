package vn.edu.usth.online_music_player.data.account;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;
import vn.edu.usth.online_music_player.data.account.AccountModels.*;

public interface AccountApi {
    @POST("auth/register") Call<Session> register(@Body Login body);
    @POST("auth/login") Call<Session> login(@Body Login body);
    @POST("auth/refresh") Call<Session> refresh(@Body Refresh body);
    @POST("auth/logout") Call<Void> logout(@Body Refresh body);
    @GET("me/profile") Call<Profile> profile();
    @PATCH("me/profile") Call<Profile> edit(@Body Edit body);
    @GET("me/entitlements") Call<Entitlement> entitlement();
    @POST("demo/payments") Call<Payment> pay(@Body PaymentRequest body);
    @POST("demo/reset-plan") Call<Entitlement> reset();
    @GET("me/history") Call<List<History>> history();
}
