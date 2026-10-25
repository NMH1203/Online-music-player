package vn.edu.usth.online_music_player.data.catalog;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Path;

import vn.edu.usth.online_music_player.data.catalog.CatalogModels.HomeResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.SearchResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.ArtistDetailResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.AlbumDetailResponse;

public interface CatalogApi {
    @GET("home")
    Call<HomeResponse> getHome();

    @GET("search")
    Call<SearchResponse> search(@Query("q") String query);

    @GET("artists/{id}")
    Call<ArtistDetailResponse> getArtist(@Path("id") String id);

    @GET("albums/{id}")
    Call<AlbumDetailResponse> getAlbum(@Path("id") String id);


}