package vn.edu.usth.online_music_player.backend.common;

import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves the authenticated UUID subject; the user id is never accepted from the request body. */
@Component
public class CurrentUserId {
    public UUID require(Principal principal) {
        UUID userId = optional(principal);
        if (userId == null) {
            throw unauthenticated();
        }
        return userId;
    }

    public UUID optional(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ApiException unauthenticated() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "authentication_required",
                "Vui lòng đăng nhập để sử dụng thư viện.");
    }
}
