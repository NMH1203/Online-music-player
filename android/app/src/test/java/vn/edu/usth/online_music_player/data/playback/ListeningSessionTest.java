package vn.edu.usth.online_music_player.data.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class ListeningSessionTest {
    @Test
    public void countsOnlyTimeWhilePlaying() {
        ListeningSession session = new ListeningSession("t1", 0);
        session.resume(1_000);
        session.pause(11_000);       // 10 s played
        session.resume(60_000);      // 49 s paused/buffering, not counted
        assertEquals(15, session.listenedSeconds(65_000));
    }

    @Test
    public void repeatedResumeOrPauseDoesNotDoubleCount() {
        ListeningSession session = new ListeningSession("t1", 0);
        session.resume(0);
        session.resume(5_000);
        session.pause(10_000);
        session.pause(20_000);
        assertEquals(10, session.listenedSeconds(30_000));
    }

    @Test
    public void eachSessionHasItsOwnEventId() {
        assertNotEquals(new ListeningSession("t1", 0).clientEventId,
                new ListeningSession("t1", 0).clientEventId);
    }
}
