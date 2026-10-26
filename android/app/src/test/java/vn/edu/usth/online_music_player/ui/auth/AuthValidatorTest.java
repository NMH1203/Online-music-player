package vn.edu.usth.online_music_player.ui.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import vn.edu.usth.online_music_player.R;

public class AuthValidatorTest {
    @Test
    public void acceptsValidEmailAndRejectsPartialOne() {
        assertEquals(0, AuthValidator.email(" alex.lane@example.com "));
        assertEquals(R.string.auth_error_email, AuthValidator.email("alex@"));
        assertEquals(R.string.auth_error_email, AuthValidator.email(""));
        assertEquals(R.string.auth_error_email, AuthValidator.email(null));
    }

    @Test
    public void passwordUsesBackendLengthLimits() {
        assertEquals(R.string.auth_error_password_short, AuthValidator.password("1234567"));
        assertEquals(0, AuthValidator.password("12345678"));
        assertEquals(0, AuthValidator.password("a".repeat(72)));
        assertEquals(R.string.auth_error_password_long, AuthValidator.password("a".repeat(73)));
        // 36 two-byte characters = 72 bytes is fine; 37 is over the byte limit.
        assertEquals(0, AuthValidator.password("é".repeat(36)));
        assertEquals(R.string.auth_error_password_long, AuthValidator.password("é".repeat(37)));
    }

    @Test
    public void displayNameNeedsTwoToHundredCharacters() {
        assertEquals(R.string.auth_error_name_short, AuthValidator.displayName(" A "));
        assertEquals(0, AuthValidator.displayName("Al"));
        assertEquals(R.string.auth_error_name_long, AuthValidator.displayName("a".repeat(101)));
    }
}
