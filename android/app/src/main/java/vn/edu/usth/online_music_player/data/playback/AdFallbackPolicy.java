package vn.edu.usth.online_music_player.data.playback;

import java.time.Instant;

/** Short-lived grace for server-verified Premium; unknown/stale plans default to Free. */
final class AdFallbackPolicy {
    static boolean adFree(boolean cachedAdFree, String expiresAt, long ageMs, Instant now) {
        if (!cachedAdFree || ageMs < 0 || ageMs > 15 * 60_000L || expiresAt == null) return false;
        try { return Instant.parse(expiresAt).isAfter(now); }
        catch (RuntimeException e) { return false; }
    }
}
