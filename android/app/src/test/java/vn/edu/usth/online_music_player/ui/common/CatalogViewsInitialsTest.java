package vn.edu.usth.online_music_player.ui.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CatalogViewsInitialsTest {
    @Test
    public void usesFirstAndLastWord() {
        assertEquals("AL", CatalogViews.initials("Alex Lane"));
        assertEquals("NM", CatalogViews.initials("  nguyen  minh  "));
        assertEquals("A", CatalogViews.initials("Alex"));
        assertEquals("", CatalogViews.initials(" "));
        assertEquals("", CatalogViews.initials(null));
    }
}
