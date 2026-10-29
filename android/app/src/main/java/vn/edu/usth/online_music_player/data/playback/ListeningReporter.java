package vn.edu.usth.online_music_player.data.playback;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import java.time.Instant;

/** Sends POST /listening-events for every track listen; the server decides if it counts as a play. */
final class ListeningReporter implements Player.Listener {

    static final class Event {
        final String clientEventId, trackId, sourceContext, sourcePlaylistId, startedAt, endedAt;
        final int listenedSeconds;

        Event(ListeningSession session, int listenedSeconds, long endedAtEpochMs) {
            clientEventId = session.clientEventId;
            trackId = session.trackId;
            sourceContext = session.sourceContext;
            sourcePlaylistId = session.sourcePlaylistId;
            startedAt = Instant.ofEpochMilli(session.startedAtEpochMs).toString();
            endedAt = Instant.ofEpochMilli(endedAtEpochMs).toString();
            this.listenedSeconds = listenedSeconds;
        }
    }

    private final Player player;
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
                session.owner = vn.edu.usth.online_music_player.data.session.SessionManager.get().currentUserId();
                android.os.Bundle extras = item.mediaMetadata.extras;
                if (extras != null) {
                    session.sourceContext = extras.getString(PlaybackManager.EXTRA_SOURCE_CONTEXT, "queue");
                    session.sourcePlaylistId = extras.getString(PlaybackManager.EXTRA_SOURCE_PLAYLIST);
                }
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
        ListeningOutbox.get().enqueue(done.owner, new Event(done, seconds, System.currentTimeMillis()));
    }
}
