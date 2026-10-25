package vn.edu.usth.online_music_player.backend.subscriptions;

import java.time.Instant;
import java.util.UUID;

public record Entitlement(UUID subscriptionId, String planCode, String planName, boolean adFree,
        Integer maxPlaylists, Integer historyDays, Instant startsAt, Instant expiresAt, boolean demoEnabled) {}
