package vn.edu.usth.online_music_player.ui.auth;

import java.util.regex.Pattern;
import vn.edu.usth.online_music_player.R;

/** Client-side checks matching the backend limits; returns a string resource id or 0 when valid. */
public final class AuthValidator {
    // Same shape as android.util.Patterns.EMAIL_ADDRESS, kept here so it runs in plain JVM tests.
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+");
    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 72;
    static final int MIN_NAME = 2;
    static final int MAX_NAME = 100;

    private AuthValidator() {}

    public static int email(String value) {
        String email = value == null ? "" : value.trim();
        return email.length() <= 320 && EMAIL.matcher(email).matches() ? 0 : R.string.auth_error_email;
    }

    public static int password(String value) {
        if (value == null || value.length() < MIN_PASSWORD) return R.string.auth_error_password_short;
        // Backend limit is 72 bytes of UTF-8, not 72 characters.
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD) {
            return R.string.auth_error_password_long;
        }
        return 0;
    }

    public static int displayName(String value) {
        int length = value == null ? 0 : value.trim().length();
        if (length < MIN_NAME) return R.string.auth_error_name_short;
        return length > MAX_NAME ? R.string.auth_error_name_long : 0;
    }
}
