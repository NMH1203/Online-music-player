package vn.edu.usth.online_music_player.backend.subscriptions;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;

@RestController
public class SubscriptionController {
    private final SubscriptionService subscriptions;
    private final CurrentUserId users;
    public SubscriptionController(SubscriptionService subscriptions, CurrentUserId users) {
        this.subscriptions = subscriptions; this.users = users;
    }
    public record PaymentRequest(@NotNull UUID idempotencyKey,
        @NotNull @Pattern(regexp="success|failed") String outcome) {}
    public record AdsPolicy(boolean showAppOpen, boolean playBetweenTracks) {}
    @GetMapping("/plans") public List<SubscriptionService.Plan> plans() { return subscriptions.plans(); }
    @GetMapping({"/me/entitlements", "/me/subscription"})
    public Entitlement entitlement(Principal p) { return subscriptions.current(users.require(p)); }
    @PostMapping("/demo/payments")
    public SubscriptionService.Payment pay(Principal p, @Valid @RequestBody PaymentRequest request) {
        return subscriptions.pay(users.require(p), request.idempotencyKey(), request.outcome());
    }
    @PostMapping("/demo/reset-plan")
    public Entitlement reset(Principal p) { return subscriptions.reset(users.require(p)); }
    @GetMapping("/me/ads/policy")
    public AdsPolicy ads(Principal p) {
        boolean enabled = !subscriptions.current(users.require(p)).adFree();
        return new AdsPolicy(enabled, enabled);
    }
}
