package vn.edu.usth.online_music_player.data.library;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface LibraryApi {
    @GET("me/library/tracks")
    Call<List<FavoriteTrack>> getFavoriteTracks();

    @GET("me/library/albums")
    Call<List<FavoriteAlbum>> getFavoriteAlbums();

    @GET("me/playlists")
    Call<List<PlaylistSummary>> getPlaylists();

    @PUT("me/library/tracks/{trackId}")
    Call<Void> addFavoriteTrack(@Path("trackId") String trackId);

    @PUT("me/library/albums/{albumId}")
    Call<Void> addFavoriteAlbum(@Path("albumId") String albumId);

    @DELETE("me/library/tracks/{trackId}")
    Call<Void> removeFavoriteTrack(@Path("trackId") String trackId);

    @DELETE("me/library/albums/{albumId}")
    Call<Void> removeFavoriteAlbum(@Path("albumId") String albumId);
}
