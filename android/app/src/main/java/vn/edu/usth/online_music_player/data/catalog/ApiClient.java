package vn.edu.usth.online_music_player.data.catalog;

import vn.edu.usth.online_music_player.BuildConfig;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static final CatalogApi CATALOG_API =
            new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(CatalogApi.class);
    private ApiClient() {}

    public static CatalogApi catalog() {
        return CATALOG_API;
    }
}