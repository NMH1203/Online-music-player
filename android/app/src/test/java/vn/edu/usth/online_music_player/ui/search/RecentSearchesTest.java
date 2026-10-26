package vn.edu.usth.online_music_player.ui.search;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RecentSearchesTest {
    @Test
    public void newestFirstWithoutDuplicates() {
        List<String> result = RecentSearches.withQuery(Arrays.asList("After Hours", "nova lane"), " Nova Lane ");
        assertEquals(Arrays.asList("Nova Lane", "After Hours"), result);
    }

    @Test
    public void keepsAtMostTen() {
        List<String> current = new ArrayList<>();
        for (int i = 0; i < 10; i++) current.add("q" + i);
        List<String> result = RecentSearches.withQuery(current, "new");
        assertEquals(RecentSearches.MAX, result.size());
        assertEquals("new", result.get(0));
        assertEquals("q8", result.get(9));
    }

    @Test
    public void blankQueryChangesNothing() {
        assertEquals(Arrays.asList("a"), RecentSearches.withQuery(Arrays.asList("a"), "  "));
    }
}
