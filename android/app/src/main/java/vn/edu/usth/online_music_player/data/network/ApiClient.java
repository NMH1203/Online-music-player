package vn.edu.usth.online_music_player.data.network;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.usth.online_music_player.BuildConfig;
import vn.edu.usth.online_music_player.data.session.AccessTokenStore;

public final class ApiClient {
    private static volatile Retrofit retrofit;

    private ApiClient() {}

    public static <T> T create(Class<T> serviceType) {
        return getRetrofit().create(serviceType);
    }

    private static Retrofit getRetrofit() {
        if (retrofit == null) {
            synchronized (ApiClient.class) {
                if (retrofit == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .addInterceptor(chain -> {
                                var manager = vn.edu.usth.online_music_player.data.session.SessionManager.get();
                                Request.Builder request = chain.request().newBuilder().header("Accept", "application/json");
                                synchronized (manager) {
                                    var supplied = chain.request().tag(vn.edu.usth.online_music_player.data.session.SessionManager.Stamp.class);
                                    var current = manager.stamp();
                                    if (supplied != null && supplied.generation != current.generation)
                                        throw new java.io.IOException("Account changed before request was sent");
                                    request.tag(vn.edu.usth.online_music_player.data.session.SessionManager.Stamp.class, current);
                                    String token = AccessTokenStore.getAccessToken();
                                    if (token != null) request.header("Authorization", "Bearer " + token);
                                }
                                return chain.proceed(request.build());
                            })
                            .authenticator((route, response) ->
                                    vn.edu.usth.online_music_player.data.session.SessionManager.get().authenticate(response))
                            .build();

                    retrofit = new Retrofit.Builder()
                            .baseUrl(BuildConfig.BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }
}
