package vn.edu.usth.online_music_player.data.catalog;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

import vn.edu.usth.online_music_player.data.catalog.CatalogModels.HomeResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.SearchResponse;

public interface CatalogApi {
    @GET("home")
    Call<HomeResponse> getHome();

    @GET("search")
    Call<SearchResponse> search(@Query("q") String query);
}