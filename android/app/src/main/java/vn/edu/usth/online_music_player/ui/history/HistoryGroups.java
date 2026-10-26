package vn.edu.usth.online_music_player.ui.history;

import java.time.LocalDate;
import vn.edu.usth.online_music_player.R;

/** Figma history sections: Today, Yesterday, Earlier this week, Older. */
final class HistoryGroups {
    private HistoryGroups() {}

    static int titleFor(LocalDate played, LocalDate today) {
        if (!played.isBefore(today)) return R.string.history_today;
        if (played.equals(today.minusDays(1))) return R.string.history_yesterday;
        if (played.isAfter(today.minusDays(7))) return R.string.history_this_week;
        return R.string.history_older;
    }
}
