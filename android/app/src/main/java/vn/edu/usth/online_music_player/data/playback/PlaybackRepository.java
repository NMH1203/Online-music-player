package vn.edu.usth.online_music_player.data.playback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.online_music_player.data.network.ApiClient;

/** Loads server-authorized playback metadata before giving stream URLs to the player. */
public final class PlaybackRepository {
    public enum FailureType {
        AUTHENTICATION_REQUIRED,
        NETWORK,
        SERVER
    }

    public interface QueueCallback {
        void onLoaded(List<TrackPlayback> tracks);
        void onFailed(FailureType failureType);
    }

    private final PlaybackApi api = ApiClient.create(PlaybackApi.class);
    private final Set<Call<?>> activeCalls = Collections.synchronizedSet(new HashSet<>());

    public void loadQueue(List<String> trackIds, QueueCallback callback) {
        List<String> ids = trackIds == null ? Collections.emptyList() : new ArrayList<>(trackIds);
        if (ids.isEmpty()) {
            callback.onLoaded(Collections.emptyList());
            return;
        }

        List<TrackPlayback> results = new ArrayList<>(Collections.nCopies(ids.size(), null));
        AtomicInteger remaining = new AtomicInteger(ids.size());
        AtomicReference<FailureType> failure = new AtomicReference<>();
        for (int index = 0; index < ids.size(); index++) {
            final int resultIndex = index;
            Call<TrackPlayback> call = api.getPlayback(ids.get(index));
            activeCalls.add(call);
            call.enqueue(new Callback<TrackPlayback>() {
                @Override
                public void onResponse(Call<TrackPlayback> completed, Response<TrackPlayback> response) {
                    activeCalls.remove(completed);
                    if (response.isSuccessful() && response.body() != null) {
                        results.set(resultIndex, response.body());
                    } else {
                        failure.compareAndSet(null, classify(response.code()));
                    }
                    finishOne();
                }

                @Override
                public void onFailure(Call<TrackPlayback> failed, Throwable error) {
                    activeCalls.remove(failed);
                    if (!failed.isCanceled()) {
                        failure.compareAndSet(null, FailureType.NETWORK);
                        finishOne();
                    }
                }

                private void finishOne() {
                    if (remaining.decrementAndGet() == 0) {
                        FailureType finalFailure = failure.get();
                        if (finalFailure == null) {
                            callback.onLoaded(Collections.unmodifiableList(new ArrayList<>(results)));
                        } else {
                            callback.onFailed(finalFailure);
                        }
                    }
                }
            });
        }
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

    private static FailureType classify(int statusCode) {
        if (statusCode == 401) {
            return FailureType.AUTHENTICATION_REQUIRED;
        }
        return FailureType.SERVER;
    }
}
