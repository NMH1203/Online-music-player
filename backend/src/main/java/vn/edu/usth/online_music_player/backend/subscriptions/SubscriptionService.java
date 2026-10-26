package vn.edu.usth.online_music_player.backend.subscriptions;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.online_music_player.backend.common.ApiException;

@Service
public class SubscriptionService {
    private final JdbcTemplate db;
    private final boolean demoEnabled;
    public SubscriptionService(JdbcTemplate db, @Value("${demo.enabled:false}") boolean demoEnabled) {
        this.db = db; this.demoEnabled = demoEnabled;
    }
    public record Plan(String code, String name, boolean adFree, Integer maxPlaylists, Integer historyDays) {}
    public record Payment(UUID paymentId, String status, UUID subscriptionId, Entitlement entitlement) {}
    public List<Plan> plans() {
        return db.query("SELECT * FROM plans WHERE active = true ORDER BY code",
            (r,n) -> new Plan(r.getString("code"), r.getString("name"), r.getBoolean("ad_free"),
                (Integer) r.getObject("max_playlists"), (Integer) r.getObject("history_days")));
    }
    public void lockUser(UUID userId) {
        if (db.query("SELECT id FROM users WHERE id = ? AND status = 'active' FOR UPDATE",
                (r,n) -> r.getObject(1, UUID.class), userId).isEmpty())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Vui lòng đăng nhập lại.");
    }
    // The deferred V6 constraint requires a replacement Free subscription in the same transaction.
    @Transactional
    public Entitlement current(UUID userId) {
        lockUser(userId);
        db.update("""
            UPDATE subscriptions SET status = 'expired', ended_at = GREATEST(now(), starts_at)
            WHERE user_id = ? AND status = 'active' AND expires_at <= now()
            """, userId);
        db.update("""
            INSERT INTO subscriptions(user_id, plan_id, source)
            SELECT ?, p.id, 'free' FROM plans p WHERE p.code = 'FREE' AND p.active = true
            AND NOT EXISTS (SELECT 1 FROM subscriptions WHERE user_id = ? AND status = 'active')
            """, userId, userId);
        return read(userId);
    }
    private Entitlement read(UUID userId) {
        var rows = db.query("""
            SELECT s.*, p.code, p.name, p.ad_free, p.max_playlists, p.history_days
            FROM subscriptions s JOIN plans p ON p.id = s.plan_id
            WHERE s.user_id = ? AND s.status = 'active'
            """, (r,n) -> new Entitlement(r.getObject("id", UUID.class), r.getString("code"),
            r.getString("name"), r.getBoolean("ad_free"), (Integer) r.getObject("max_playlists"),
            (Integer) r.getObject("history_days"), r.getObject("starts_at", OffsetDateTime.class).toInstant(),
            r.getObject("expires_at", OffsetDateTime.class) == null ? null :
                r.getObject("expires_at", OffsetDateTime.class).toInstant(), demoEnabled), userId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "plan_unavailable", "Chưa cấu hình gói Free.");
        return rows.get(0);
    }
    @Transactional
    public Payment pay(UUID userId, UUID key, String outcome) {
        requireDemo();
        if (!Set.of("success", "failed").contains(outcome))
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_outcome", "Kết quả phải là success hoặc failed.");
        // Serialize the globally unique key BEFORE locking the account, including cross-account reuse.
        db.query("SELECT pg_advisory_xact_lock(?)", (r,n) -> 0, key.getMostSignificantBits() ^ key.getLeastSignificantBits());
        Entitlement entitlement = current(userId);
        var existing = db.query("SELECT * FROM payment_events WHERE idempotency_key = ?", (r,n) ->
            new Object[]{r.getObject("id", UUID.class), r.getObject("user_id", UUID.class),
                r.getString("status"), r.getObject("subscription_id", UUID.class)}, key);
        if (!existing.isEmpty()) {
            Object[] event = existing.get(0);
            if (!userId.equals(event[1]) || !outcome.equals(event[2]))
                throw new ApiException(HttpStatus.CONFLICT, "idempotency_conflict", "Mã yêu cầu đã được sử dụng cho giao dịch khác.");
            // Replaying an old payment after reset must NOT re-activate Premium.
            return new Payment((UUID)event[0], (String)event[2], (UUID)event[3], entitlement);
        }
        var plans = db.query("SELECT id FROM plans WHERE code = 'PREMIUM' AND active = true",
            (r,n) -> r.getObject(1, UUID.class));
        if (plans.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "plan_unavailable", "Gói Premium chưa sẵn sàng.");
        UUID subscriptionId = null;
        if ("success".equals(outcome)) {
            if (!"PREMIUM".equals(entitlement.planCode())) {
                endActive(userId, "cancelled");
                db.update("""
                    INSERT INTO subscriptions(user_id, plan_id, source, expires_at)
                    VALUES (?, ?, 'mock', now() + interval '30 days')
                    """, userId, plans.get(0));
            }
            entitlement = read(userId);
            subscriptionId = entitlement.subscriptionId();
        }
        UUID paymentId = UUID.randomUUID();
        db.update("""
            INSERT INTO payment_events(id, idempotency_key, user_id, plan_id, subscription_id,
                event_type, status) VALUES (?, ?, ?, ?, ?, 'upgrade', ?)
            """, paymentId, key, userId, plans.get(0), subscriptionId, outcome);
        return new Payment(paymentId, outcome, subscriptionId, entitlement);
    }
    @Transactional
    public Entitlement reset(UUID userId) {
        requireDemo(); lockUser(userId); endActive(userId, "cancelled");
        return current(userId);
    }
    private void endActive(UUID userId, String status) {
        db.update("UPDATE subscriptions SET status = ?, ended_at = GREATEST(now(), starts_at) WHERE user_id = ? AND status = 'active'",
            status, userId);
    }
    private void requireDemo() {
        if (!demoEnabled) throw new ApiException(HttpStatus.FORBIDDEN, "demo_disabled", "Chế độ thanh toán mô phỏng đang tắt.");
    }
}
