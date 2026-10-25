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
                                Request.Builder request = chain.request().newBuilder()
                                        .header("Accept", "application/json");
                                String token = AccessTokenStore.getAccessToken();
                                if (token != null) {
                                    request.header("Authorization", "Bearer " + token);
                                }
                                return chain.proceed(request.build());
                            })
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
