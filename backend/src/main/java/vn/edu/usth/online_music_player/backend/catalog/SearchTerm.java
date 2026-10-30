package vn.edu.usth.online_music_player.backend.catalog;

import org.springframework.http.HttpStatus;
import vn.edu.usth.online_music_player.backend.common.ApiException;

final class SearchTerm {
    static String normalize(String value) {
        String term = value == null ? "" : value.trim();
        if (term.length() > 100) throw new ApiException(
                HttpStatus.BAD_REQUEST, "invalid_query", "Từ khóa tìm kiếm tối đa 100 ký tự.");
        return term;
    }
    static String pattern(String value) {
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
