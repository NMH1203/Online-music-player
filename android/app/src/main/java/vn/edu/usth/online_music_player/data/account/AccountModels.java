package vn.edu.usth.online_music_player.data.account;

public final class AccountModels {
    private AccountModels() {}
    public static class Session { public String accessToken, refreshToken, userId; public long expiresIn; }
    public static class Profile { public String id, email, displayName, bio; }
    public static class Entitlement {
        public String subscriptionId, planCode, planName, startsAt, expiresAt;
        public boolean adFree, demoEnabled;
        public Integer maxPlaylists, historyDays;
    }
    public static class Payment { public String paymentId, status, subscriptionId; public Entitlement entitlement; }
    /** GET /me/history uses snake_case field names. */
    public static class History {
        public String id, track_id, title, artist_name, album_title, cover_url, started_at;
        public int duration_seconds, listened_seconds;
    }
    public static class Stats { public int validPlays, minutesListened, likedTracks, likedAlbums, playlists; }
    public static class Login {
        public final String email, password, displayName;
        public Login(String email, String password, String name) { this.email=email; this.password=password; this.displayName=name; }
    }
    public static class Refresh {
        public final String refreshToken;
        public Refresh(String token) { refreshToken=token; }
    }
    public static class Edit {
        public final String displayName, bio;
        public Edit(String name, String bio) { displayName=name; this.bio=bio; }
    }
    public static class PaymentRequest {
        public final String idempotencyKey, outcome;
        public PaymentRequest(String key, String outcome) { idempotencyKey=key; this.outcome=outcome; }
    }
}
