package vn.edu.usth.online_music_player.backend.profile;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;
import vn.edu.usth.online_music_player.backend.subscriptions.SubscriptionService;

@RestController
public class ProfileController {
    private final JdbcTemplate db;
    private final CurrentUserId users;
    private final SubscriptionService plans;
    public ProfileController(JdbcTemplate db, CurrentUserId users, SubscriptionService plans) {
        this.db = db; this.users = users; this.plans = plans;
    }
    public record Profile(UUID id, String email, String displayName, String bio) {}
    public record Edit(@NotBlank @Size(max=100) String displayName, @Size(max=500) String bio) {}
    public record Stats(long validPlays, long minutesListened, long likedTracks, long likedAlbums, long playlists) {}
    @GetMapping("/me/profile") public Profile profile(Principal p) {
        var rows = db.query("""
            SELECT u.id, u.email, p.display_name, p.bio FROM users u JOIN user_profiles p ON p.user_id = u.id WHERE u.id = ?
            """, (r,n) -> new Profile(r.getObject("id", UUID.class), r.getString("email"), r.getString("display_name"), r.getString("bio")), users.require(p));
        if (rows.isEmpty()) throw new vn.edu.usth.online_music_player.backend.common.ApiException(
            org.springframework.http.HttpStatus.NOT_FOUND, "profile_not_found", "Không tìm thấy hồ sơ người dùng.");
        return rows.get(0);
    }
    @PatchMapping("/me/profile") public Profile edit(Principal p, @Valid @RequestBody Edit edit) {
        db.update("UPDATE user_profiles SET display_name = ?, bio = ? WHERE user_id = ?",
            edit.displayName().trim(), edit.bio() == null ? null : edit.bio().trim(), users.require(p));
        return profile(p);
    }
    @GetMapping("/me/stats") public Stats stats(Principal p) {
        UUID id = users.require(p);
        return db.queryForObject("""
            SELECT
                (SELECT count(*) FROM listening_history WHERE user_id = ? AND counted_as_play = true) AS valid_plays,
                (SELECT COALESCE(SUM(listened_seconds), 0) / 60 FROM listening_history WHERE user_id = ?) AS minutes_listened,
                (SELECT count(*) FROM library_tracks WHERE user_id = ?) AS liked_tracks,
                (SELECT count(*) FROM library_albums WHERE user_id = ?) AS liked_albums,
                (SELECT count(*) FROM playlists WHERE user_id = ?) AS playlists
            """,
            (r, n) -> new Stats(
                r.getLong("valid_plays"),
                r.getLong("minutes_listened"),
                r.getLong("liked_tracks"),
                r.getLong("liked_albums"),
                r.getLong("playlists")),
            id, id, id, id, id);
    }
    @GetMapping("/me/history") public List<Map<String,Object>> history(Principal p,
            @RequestParam(name="page", defaultValue="0") int page) {
        UUID id = users.require(p);
        if (page < 0 || page > 100000) throw new vn.edu.usth.online_music_player.backend.common.ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_page", "Trang không hợp lệ.");
        Integer days = plans.current(id).historyDays();
        return db.queryForList("""
            SELECT h.id, h.track_id, t.title,
                   a.id AS artist_id, a.name AS artist_name,
                   al.id AS album_id, al.title AS album_title,
                   t.duration_seconds,
                   CASE
                       WHEN al.id IS NOT NULL AND al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                           THEN '/albums/' || al.id || '/cover'
                       ELSE NULL
                   END AS cover_url,
                   h.started_at, h.listened_seconds
            FROM listening_history h
            JOIN tracks t ON t.id = h.track_id
            JOIN artists a ON a.id = t.primary_artist_id
            LEFT JOIN albums al ON al.id = t.album_id
            WHERE h.user_id = ? AND h.counted_as_play = true
            AND (?::integer IS NULL OR h.started_at >= now() - make_interval(days => ?::integer))
            ORDER BY h.started_at DESC, h.id DESC LIMIT 50 OFFSET ?
            """, id, days, days, page * 50);
    }
}
