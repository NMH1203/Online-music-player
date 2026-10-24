package vn.edu.usth.online_music_player.data.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.online_music_player.data.network.ApiClient;

public final class LibraryRepository {
    public enum FailureType {
        AUTHENTICATION_REQUIRED,
        NETWORK,
        SERVER
    }

    public interface LoadCallback {
        void onLoaded(LibraryContent content);
        void onFailed(FailureType failureType);
    }

    public interface ActionCallback {
        void onCompleted(boolean succeeded, FailureType failureType);
    }

    private interface ListResult<T> {
        void set(List<T> items);
    }

    private final LibraryApi api = ApiClient.create(LibraryApi.class);
    private final Set<Call<?>> activeCalls = Collections.synchronizedSet(new HashSet<>());

    public void load(LoadCallback callback) {
        LoadAccumulator accumulator = new LoadAccumulator(callback);
        enqueueList(api.getFavoriteTracks(), accumulator::setFavoriteTracks, accumulator);
        enqueueList(api.getFavoriteAlbums(), accumulator::setFavoriteAlbums, accumulator);
        enqueueList(api.getPlaylists(), accumulator::setPlaylists, accumulator);
    }

    public void removeFavoriteTrack(String trackId, ActionCallback callback) {
        enqueueAction(api.removeFavoriteTrack(trackId), callback);
    }

    public void removeFavoriteAlbum(String albumId, ActionCallback callback) {
        enqueueAction(api.removeFavoriteAlbum(albumId), callback);
    }

    public void cancelAll() {
        List<Call<?>> calls;
        synchronized (activeCalls) {
            calls = new ArrayList<>(activeCalls);
            activeCalls.clear();
        }
        for (Call<?> call : calls) {
            call.cancel();
        }
    }

    private <T> void enqueueList(
            Call<List<T>> call,
            ListResult<T> result,
            LoadAccumulator accumulator
    ) {
        activeCalls.add(call);
        call.enqueue(new Callback<List<T>>() {
            @Override
            public void onResponse(Call<List<T>> completedCall, Response<List<T>> response) {
                activeCalls.remove(completedCall);
                if (response.isSuccessful()) {
                    List<T> body = response.body();
                    result.set(body == null ? Collections.emptyList() : body);
                    accumulator.complete(null);
                } else {
                    accumulator.complete(classify(response.code()));
                }
            }

            @Override
            public void onFailure(Call<List<T>> failedCall, Throwable throwable) {
                activeCalls.remove(failedCall);
                if (!failedCall.isCanceled()) {
                    accumulator.complete(FailureType.NETWORK);
                }
            }
        });
    }

    private void enqueueAction(Call<Void> call, ActionCallback callback) {
        activeCalls.add(call);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> completedCall, Response<Void> response) {
                activeCalls.remove(completedCall);
                callback.onCompleted(response.isSuccessful(),
                        response.isSuccessful() ? null : classify(response.code()));
            }

            @Override
            public void onFailure(Call<Void> failedCall, Throwable throwable) {
                activeCalls.remove(failedCall);
                if (!failedCall.isCanceled()) {
                    callback.onCompleted(false, FailureType.NETWORK);
                }
            }
        });
    }

    static FailureType classify(int responseCode) {
        if (responseCode == 401) {
            return FailureType.AUTHENTICATION_REQUIRED;
        }
        return FailureType.SERVER;
    }

    private static final class LoadAccumulator {
        private final LoadCallback callback;
        private int remaining = 3;
        private FailureType failureType;
        private List<FavoriteTrack> favoriteTracks;
        private List<FavoriteAlbum> favoriteAlbums;
        private List<PlaylistSummary> playlists;

        private LoadAccumulator(LoadCallback callback) {
            this.callback = callback;
        }

        private synchronized void setFavoriteTracks(List<FavoriteTrack> items) {
            favoriteTracks = items;
        }

        private synchronized void setFavoriteAlbums(List<FavoriteAlbum> items) {
            favoriteAlbums = items;
        }

        private synchronized void setPlaylists(List<PlaylistSummary> items) {
            playlists = items;
        }

        private void complete(FailureType failure) {
            LibraryContent content = null;
            FailureType finalFailure = null;
            boolean finished = false;
            synchronized (this) {
                if (failure != null && failureType == null) {
                    failureType = failure;
                }
                remaining--;
                if (remaining == 0) {
                    finished = true;
                    finalFailure = failureType;
                    if (finalFailure == null) {
                        content = new LibraryContent(favoriteTracks, favoriteAlbums, playlists);
                    }
                }
            }
            if (!finished) {
                return;
            }
            if (finalFailure == null) {
                callback.onLoaded(content);
            } else {
                callback.onFailed(finalFailure);
            }
        }
    }
}
