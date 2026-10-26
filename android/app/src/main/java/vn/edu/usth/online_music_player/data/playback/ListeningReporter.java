package vn.edu.usth.online_music_player.data.playback;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import java.time.Instant;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.POST;
import vn.edu.usth.online_music_player.data.network.ApiClient;

/** Sends POST /listening-events for every track listen; the server decides if it counts as a play. */
final class ListeningReporter implements Player.Listener {
    interface ListeningApi {
        @POST("listening-events")
        Call<Void> report(@Body Event event);
    }

    static final class Event {
        final String clientEventId, trackId, sourceContext, startedAt, endedAt;
        final int listenedSeconds;

        Event(ListeningSession session, int listenedSeconds, long endedAtEpochMs) {
            clientEventId = session.clientEventId;
            trackId = session.trackId;
            // shortcut: every listen is reported as "queue"; pass the real source through MediaItem extras when stats need it.
            sourceContext = "queue";
            startedAt = Instant.ofEpochMilli(session.startedAtEpochMs).toString();
            endedAt = Instant.ofEpochMilli(endedAtEpochMs).toString();
            this.listenedSeconds = listenedSeconds;
        }
    }

    private final Player player;
    private final ListeningApi api = ApiClient.create(ListeningApi.class);
    @Nullable private ListeningSession session;

    ListeningReporter(Player player) {
        this.player = player;
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        long now = SystemClock.elapsedRealtime();
        if (isPlaying) {
            MediaItem item = player.getCurrentMediaItem();
            if (session == null && item != null && !BoundaryAds.AD_ID.equals(item.mediaId)) {
                session = new ListeningSession(item.mediaId, System.currentTimeMillis());
            }
            if (session != null) session.resume(now);
        } else if (session != null) {
            session.pause(now);
        }
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        finish();
        // A skip while playing does not toggle isPlaying, so open the next session here.
        if (player.isPlaying()) onIsPlayingChanged(true);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) finish();
    }

    /** Reports the current listen, if any. Called on track change, stop and service shutdown. */
    void finish() {
        ListeningSession done = session;
        session = null;
        if (done == null) return;
        int seconds = done.listenedSeconds(SystemClock.elapsedRealtime());
        if (seconds <= 0) return;
        // shortcut: a failed report is dropped (no retry queue); add one if offline listening must count.
        api.report(new Event(done, seconds, System.currentTimeMillis())).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {}
            @Override public void onFailure(Call<Void> call, Throwable error) {}
        });
    }
}
