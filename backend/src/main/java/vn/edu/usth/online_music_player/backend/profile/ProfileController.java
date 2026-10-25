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
    @GetMapping("/me/profile") public Profile profile(Principal p) {
        return db.queryForObject("""
            SELECT u.id, u.email, p.display_name, p.bio FROM users u JOIN user_profiles p ON p.user_id = u.id WHERE u.id = ?
            """, (r,n) -> new Profile(r.getObject("id", UUID.class), r.getString("email"), r.getString("display_name"), r.getString("bio")), users.require(p));
    }
    @PatchMapping("/me/profile") public Profile edit(Principal p, @Valid @RequestBody Edit edit) {
        db.update("UPDATE user_profiles SET display_name = ?, bio = ? WHERE user_id = ?",
            edit.displayName().trim(), edit.bio() == null ? null : edit.bio().trim(), users.require(p));
        return profile(p);
    }
    @GetMapping("/me/history") public List<Map<String,Object>> history(Principal p,
            @RequestParam(defaultValue="0") @Min(0) int page) {
        UUID id = users.require(p);
        if (page < 0 || page > 100000) throw new vn.edu.usth.online_music_player.backend.common.ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_page", "Trang không hợp lệ.");
        Integer days = plans.current(id).historyDays();
        return db.queryForList("""
            SELECT h.id, h.track_id, t.title, a.name AS artist_name, h.started_at, h.listened_seconds
            FROM listening_history h JOIN tracks t ON t.id = h.track_id
            JOIN artists a ON a.id = t.primary_artist_id
            WHERE h.user_id = ? AND h.counted_as_play = true
            AND (?::integer IS NULL OR h.started_at >= now() - make_interval(days => ?::integer))
            ORDER BY h.started_at DESC, h.id DESC LIMIT 50 OFFSET ?
            """, id, days, days, page * 50);
    }
}
