package vn.edu.usth.online_music_player.data.playback;

import java.util.UUID;

/**
 * One listen of one track. Counts only time spent actually playing (clock time between
 * resume and pause), so seeking forward or buffering never adds listened seconds.
 */
final class ListeningSession {
    final String clientEventId = UUID.randomUUID().toString();
    final String trackId;
    final long startedAtEpochMs;
    private long playedMs;
    private long resumedAtMs = -1;

    ListeningSession(String trackId, long startedAtEpochMs) {
        this.trackId = trackId;
        this.startedAtEpochMs = startedAtEpochMs;
    }

    /** @param nowMs a monotonic clock (SystemClock.elapsedRealtime in the app). */
    void resume(long nowMs) {
        if (resumedAtMs < 0) resumedAtMs = nowMs;
    }

    void pause(long nowMs) {
        if (resumedAtMs >= 0) {
            playedMs += Math.max(0, nowMs - resumedAtMs);
            resumedAtMs = -1;
        }
    }

    int listenedSeconds(long nowMs) {
        pause(nowMs);
        return (int) (playedMs / 1000);
    }
}
