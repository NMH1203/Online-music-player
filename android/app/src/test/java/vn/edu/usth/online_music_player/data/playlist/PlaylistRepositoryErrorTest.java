package vn.edu.usth.online_music_player.data.playlist;

import static org.junit.Assert.assertEquals;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.Response;

public class PlaylistRepositoryErrorTest {
    @Test
    public void classifiesAuthenticationAndMissingPlaylistResponses() {
        assertEquals(PlaylistRepository.FailureType.AUTHENTICATION_REQUIRED,
                PlaylistRepository.classify(Response.error(401, errorBody("{}"))));
        assertEquals(PlaylistRepository.FailureType.NOT_FOUND,
                PlaylistRepository.classify(Response.error(404, errorBody("{}"))));
    }

    @Test
    public void classifiesPlaylistQuotaConflictOnlyForItsErrorCode() {
        assertEquals(PlaylistRepository.FailureType.PLAYLIST_LIMIT_REACHED,
                PlaylistRepository.classify(Response.error(409,
                        errorBody("{\"code\":\"playlist_limit_reached\"}"))));
        assertEquals(PlaylistRepository.FailureType.SERVER,
                PlaylistRepository.classify(Response.error(409,
                        errorBody("{\"code\":\"different_conflict\"}"))));
        assertEquals(PlaylistRepository.FailureType.SERVER,
                PlaylistRepository.classify(Response.error(409, errorBody("not-json"))));
    }

    @Test
    public void classifiesUnrecognizedServerErrorsAsRetryableServerFailures() {
        assertEquals(PlaylistRepository.FailureType.SERVER,
                PlaylistRepository.classify(Response.error(503, errorBody("{}"))));
    }

    private static ResponseBody errorBody(String json) {
        return ResponseBody.create(MediaType.parse("application/json"), json);
    }
}
