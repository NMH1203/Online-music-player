package vn.edu.usth.online_music_player.data.session;

import androidx.annotation.Nullable;

/**
 * Holds the short-lived access token in process memory only.
 * The authentication flow should replace it after login/refresh and clear it on logout.
 */
public final class AccessTokenStore {
    private static volatile String accessToken;

    private AccessTokenStore() {}

    @Nullable
    public static String getAccessToken() {
        return accessToken;
    }

    public static void replaceAccessToken(@Nullable String token) {
        String trimmed = token == null ? null : token.trim();
        accessToken = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    public static void clear() {
        accessToken = null;
    }
}
