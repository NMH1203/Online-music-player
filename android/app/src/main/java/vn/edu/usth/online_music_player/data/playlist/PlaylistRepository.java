package vn.edu.usth.online_music_player.data.playlist;

import java.util.ArrayList;
import java.io.IOException;
import java.util.List;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;
import vn.edu.usth.online_music_player.data.network.ApiClient;

public final class PlaylistRepository {
    public enum FailureType {
        AUTHENTICATION_REQUIRED,
        PLAYLIST_LIMIT_REACHED,
        NOT_FOUND,
        NETWORK,
        SERVER
    }

    public interface ResultCallback<T> {
        void onSuccess(T result);
        void onFailure(FailureType failureType);
    }

    private final PlaylistApi api = ApiClient.create(PlaylistApi.class);

    public void getPlaylist(String playlistId, ResultCallback<PlaylistDetail> callback) {
        enqueue(api.getPlaylist(playlistId), callback);
    }

    public void createPlaylist(
            String name, String description, ResultCallback<PlaylistSummary> callback) {
        enqueue(api.createPlaylist(new PlaylistWriteRequest(name, description)), callback);
    }

    public void getPlaylistQuota(ResultCallback<PlaylistQuota> callback) {
        enqueue(api.getPlaylistQuota(), callback);
    }

    public void updatePlaylist(
            String playlistId,
            String name,
            String description,
            ResultCallback<PlaylistSummary> callback) {
        enqueue(api.updatePlaylist(
                playlistId, new PlaylistWriteRequest(name, description)), callback);
    }

    public void deletePlaylist(String playlistId, ResultCallback<Void> callback) {
        enqueue(api.deletePlaylist(playlistId), callback);
    }

    public void getPublishedTracks(String query, ResultCallback<List<CatalogTrack>> callback) {
        enqueue(api.getPublishedTracks(query == null ? "" : query.trim()), callback);
    }

    public void addTracks(
            String playlistId, List<String> trackIds, ResultCallback<Void> callback) {
        addTrackAt(playlistId, new ArrayList<>(trackIds), 0, callback);
    }

    public void removeTrack(
            String playlistId, String trackId, ResultCallback<Void> callback) {
        enqueue(api.removeTrack(playlistId, trackId), callback);
    }

    private void addTrackAt(
            String playlistId,
            List<String> trackIds,
            int index,
            ResultCallback<Void> callback) {
        if (index >= trackIds.size()) {
            callback.onSuccess(null);
            return;
        }
        enqueue(api.addTrack(playlistId, trackIds.get(index)), new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                addTrackAt(playlistId, trackIds, index + 1, callback);
            }

            @Override
            public void onFailure(FailureType failureType) {
                callback.onFailure(failureType);
            }
        });
    }

    private <T> void enqueue(Call<T> call, ResultCallback<T> callback) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> completedCall, Response<T> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(classify(response));
                }
            }

            @Override
            public void onFailure(Call<T> failedCall, Throwable throwable) {
                if (!failedCall.isCanceled()) {
                    callback.onFailure(FailureType.NETWORK);
                }
            }
        });
    }

    static FailureType classify(Response<?> response) {
        int responseCode = response.code();
        if (responseCode == 401) {
            return FailureType.AUTHENTICATION_REQUIRED;
        }
        if (responseCode == 404) {
            return FailureType.NOT_FOUND;
        }
        if (responseCode == 409 && response.errorBody() != null) {
            try {
                JsonElement payload = new JsonParser().parse(response.errorBody().string());
                if (payload.isJsonObject()) {
                    JsonElement code = payload.getAsJsonObject().get("code");
                    if (code != null && code.isJsonPrimitive()
                            && "playlist_limit_reached".equals(code.getAsString())) {
                        return FailureType.PLAYLIST_LIMIT_REACHED;
                    }
                }
            } catch (IOException | JsonSyntaxException ignored) {
                // Keep the generic server state when the error envelope is missing or invalid.
            }
        }
        return FailureType.SERVER;
    }
}
