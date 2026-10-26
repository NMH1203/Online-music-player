package vn.edu.usth.online_music_player.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.online_music_player.backend.common.ApiException;

@Service
public class AuthService {
    private final JdbcTemplate db;
    private final TokenService tokens;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyHash = passwords.encode("not-a-real-account-password");
    public AuthService(JdbcTemplate db, TokenService tokens) { this.db = db; this.tokens = tokens; }
    public record Session(String accessToken, String refreshToken, long expiresIn, UUID userId) {}
    @Transactional
    public Session register(String email, String password, String displayName) {
        validatePassword(password);
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO users(id, email, password_hash) VALUES (?, ?, ?)", id,
            email.trim().toLowerCase(Locale.ROOT), passwords.encode(password));
        db.update("INSERT INTO user_profiles(user_id, display_name) VALUES (?, ?)", id, displayName.trim());
        db.update("""
            INSERT INTO subscriptions(user_id, plan_id, source)
            SELECT ?, id, 'registration' FROM plans WHERE code = 'FREE' AND active = true
            """, id);
        return createSession(id, UUID.randomUUID());
    }
    @Transactional
    public Session login(String email, String password) {
        validatePassword(password);
        var rows = db.query("SELECT id, password_hash, status FROM users WHERE lower(email) = ?",
            (r,n) -> new String[]{r.getString("id"), r.getString("password_hash"), r.getString("status")},
            email.trim().toLowerCase(Locale.ROOT));
        String hash = rows.isEmpty() ? dummyHash : rows.get(0)[1];
        boolean matched = passwords.matches(password, hash);
        if (rows.isEmpty() || !matched || !"active".equals(rows.get(0)[2])) throw unauthorized();
        UUID id = UUID.fromString(rows.get(0)[0]);
        db.update("UPDATE users SET last_login_at = now() WHERE id = ?", id);
        return createSession(id, UUID.randomUUID());
    }
    private record StoredSession(UUID id, UUID user, UUID family, OffsetDateTime expires, OffsetDateTime revoked) {}
    // Commit family revocation on token replay even though the response is an error.
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String refreshToken) {
        String hash = hash(refreshToken);
        var owner = db.query("SELECT user_id FROM user_sessions WHERE refresh_token_hash = ?",
            (r,n) -> r.getObject(1, UUID.class), hash);
        if (owner.isEmpty()) throw unauthorized();
        db.query("SELECT id FROM users WHERE id = ? FOR UPDATE", (r,n) -> r.getObject(1), owner.get(0));
        var rows = db.query("SELECT * FROM user_sessions WHERE refresh_token_hash = ? FOR UPDATE",
            (r,n) -> new StoredSession(r.getObject("id", UUID.class), r.getObject("user_id", UUID.class),
                r.getObject("token_family_id", UUID.class), r.getObject("expires_at", OffsetDateTime.class),
                r.getObject("revoked_at", OffsetDateTime.class)), hash);
        StoredSession old = rows.get(0);
        if (old.revoked() != null || !old.expires().isAfter(OffsetDateTime.now()) || !activeUser(old.user())) {
            revoke(old.family()); throw unauthorized();
        }
        db.update("UPDATE user_sessions SET revoked_at = now() WHERE id = ?", old.id());
        Session next = createSession(old.user(), old.family());
        db.update("""
            UPDATE user_sessions SET replaced_by_session_id =
            (SELECT id FROM user_sessions WHERE refresh_token_hash = ?) WHERE id = ?
            """, hash(next.refreshToken()), old.id());
        return next;
    }
    @Transactional
    public void logout(String refreshToken) {
        var owners = db.query("SELECT user_id FROM user_sessions WHERE refresh_token_hash = ?",
            (r,n) -> r.getObject(1, UUID.class), hash(refreshToken));
        if (owners.isEmpty()) return;
        db.query("SELECT id FROM users WHERE id = ? FOR UPDATE", (r,n) -> r.getObject(1), owners.get(0));
        db.update("""
            UPDATE user_sessions SET revoked_at = COALESCE(revoked_at, now())
            WHERE token_family_id = (SELECT token_family_id FROM user_sessions WHERE refresh_token_hash = ?)
            """, hash(refreshToken));
    }
    public boolean validIdentity(TokenService.Identity identity) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM user_sessions s JOIN users u ON u.id = s.user_id
            WHERE u.id = ? AND u.status = 'active' AND s.token_family_id = ?
            AND s.revoked_at IS NULL AND s.expires_at > now())
            """, Boolean.class, identity.userId(), identity.familyId()));
    }
    private boolean activeUser(UUID user) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id = ? AND status = 'active')", Boolean.class, user));
    }
    private Session createSession(UUID user, UUID family) {
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        db.update("""
            INSERT INTO user_sessions(user_id, token_family_id, refresh_token_hash, expires_at)
            VALUES (?, ?, ?, now() + interval '7 days')
            """, user, family, hash(refresh));
        return new Session(tokens.issue(user, family), refresh, 900, user);
    }
    private void revoke(UUID family) {
        db.update("UPDATE user_sessions SET revoked_at = COALESCE(revoked_at, now()) WHERE token_family_id = ?", family);
    }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "password_too_long", "Mật khẩu tối đa 72 byte UTF-8.");
    }
    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_session", "Thông tin đăng nhập hoặc phiên không hợp lệ. Vui lòng đăng nhập lại.");
    }
}
