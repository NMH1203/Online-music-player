package vn.edu.usth.online_music_player.ui.history;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import org.junit.Test;
import vn.edu.usth.online_music_player.R;

public class HistoryGroupsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    @Test
    public void groupsByDayDistance() {
        assertEquals(R.string.history_today, HistoryGroups.titleFor(TODAY, TODAY));
        assertEquals(R.string.history_yesterday, HistoryGroups.titleFor(TODAY.minusDays(1), TODAY));
        assertEquals(R.string.history_this_week, HistoryGroups.titleFor(TODAY.minusDays(6), TODAY));
        assertEquals(R.string.history_older, HistoryGroups.titleFor(TODAY.minusDays(7), TODAY));
    }

    @Test
    public void clockSkewIntoTomorrowStillCountsAsToday() {
        assertEquals(R.string.history_today, HistoryGroups.titleFor(TODAY.plusDays(1), TODAY));
    }
}
