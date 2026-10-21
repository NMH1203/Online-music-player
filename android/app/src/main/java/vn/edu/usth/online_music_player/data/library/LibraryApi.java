package vn.edu.usth.online_music_player.data.library;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface LibraryApi {
    @GET("me/library/tracks")
    Call<List<FavoriteTrack>> getFavoriteTracks();

    @GET("me/library/albums")
    Call<List<FavoriteAlbum>> getFavoriteAlbums();

    @GET("me/playlists")
    Call<List<PlaylistSummary>> getPlaylists();

    @DELETE("me/library/tracks/{trackId}")
    Call<Void> removeFavoriteTrack(@Path("trackId") String trackId);

    @DELETE("me/library/albums/{albumId}")
    Call<Void> removeFavoriteAlbum(@Path("albumId") String albumId);
}
