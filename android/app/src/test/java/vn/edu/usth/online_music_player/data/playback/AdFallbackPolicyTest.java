package vn.edu.usth.online_music_player.data.playback;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.Instant;
public class AdFallbackPolicyTest {
    final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    @Test public void recentUnexpiredPremiumGetsGrace() {
        assertTrue(AdFallbackPolicy.adFree(true,now.plusSeconds(3600).toString(),1000,now));
    }
    @Test public void expiredStaleUnknownAndFreeCannotSkipAds() {
        assertFalse(AdFallbackPolicy.adFree(true,now.toString(),1000,now));
        assertFalse(AdFallbackPolicy.adFree(true,now.plusSeconds(3600).toString(),900001,now));
        assertFalse(AdFallbackPolicy.adFree(true,null,0,now));
        assertFalse(AdFallbackPolicy.adFree(true,"broken",0,now));
        assertFalse(AdFallbackPolicy.adFree(false,now.plusSeconds(3600).toString(),0,now));
    }
}
