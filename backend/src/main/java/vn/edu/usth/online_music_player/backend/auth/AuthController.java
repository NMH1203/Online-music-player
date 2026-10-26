package vn.edu.usth.online_music_player.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    public record Login(@NotBlank @Email @Size(max=320) String email, @NotBlank @Size(max=72) String password) {}
    public record Register(@NotBlank @Email @Size(max=320) String email,
        @NotBlank @Size(min=8,max=72) String password, @NotBlank @Size(max=100) String displayName) {}
    public record Refresh(@NotBlank @Size(max=200) String refreshToken) {}
    @PostMapping("/register") public AuthService.Session register(@Valid @RequestBody Register request) {
        return auth.register(request.email(), request.password(), request.displayName());
    }
    @PostMapping("/login") public AuthService.Session login(@Valid @RequestBody Login request) {
        return auth.login(request.email(), request.password());
    }
    @PostMapping("/refresh") public AuthService.Session refresh(@Valid @RequestBody Refresh request) {
        return auth.refresh(request.refreshToken());
    }
    @PostMapping("/logout") public void logout(@Valid @RequestBody Refresh request) { auth.logout(request.refreshToken()); }
}
