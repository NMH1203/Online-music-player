package vn.edu.usth.online_music_player.data.playlist;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;

public interface PlaylistApi {
    @GET("tracks")
    Call<List<CatalogTrack>> getPublishedTracks(@Query("query") String query);

    @POST("me/playlists")
    Call<PlaylistSummary> createPlaylist(@Body PlaylistWriteRequest request);

    @GET("me/playlists/quota")
    Call<PlaylistQuota> getPlaylistQuota();

    @GET("me/playlists/{playlistId}")
    Call<PlaylistDetail> getPlaylist(@Path("playlistId") String playlistId);

    @PUT("me/playlists/{playlistId}")
    Call<PlaylistSummary> updatePlaylist(
            @Path("playlistId") String playlistId, @Body PlaylistWriteRequest request);

    @DELETE("me/playlists/{playlistId}")
    Call<Void> deletePlaylist(@Path("playlistId") String playlistId);

    @POST("me/playlists/{playlistId}/tracks/{trackId}")
    Call<Void> addTrack(
            @Path("playlistId") String playlistId, @Path("trackId") String trackId);

    @DELETE("me/playlists/{playlistId}/tracks/{trackId}")
    Call<Void> removeTrack(
            @Path("playlistId") String playlistId, @Path("trackId") String trackId);
}
