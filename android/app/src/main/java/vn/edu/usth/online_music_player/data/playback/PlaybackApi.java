package vn.edu.usth.online_music_player.data.playback;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface PlaybackApi {
    @GET("tracks/{trackId}/playback")
    Call<TrackPlayback> getPlayback(@Path("trackId") String trackId);
}
