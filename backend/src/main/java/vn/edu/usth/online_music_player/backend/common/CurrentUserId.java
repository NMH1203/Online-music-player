package vn.edu.usth.online_music_player.backend.common;

import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves the authenticated UUID subject; the user id is never accepted from the request body. */
@Component
public class CurrentUserId {
    public UUID require(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw unauthenticated();
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw unauthenticated();
        }
    }

    private ApiException unauthenticated() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "authentication_required",
                "Vui lòng đăng nhập để sử dụng thư viện.");
    }
}
