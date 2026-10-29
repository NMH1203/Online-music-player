package vn.edu.usth.online_music_player.data.playback;
import org.junit.Test;
import static org.junit.Assert.*;
import com.google.gson.Gson;
public class ListeningEventTest {
    @Test public void eventKeepsPlaylistSourceAndIdentityThroughSerialization() {
        ListeningSession session = new ListeningSession("track-id", 1000);
        session.owner="account-a"; session.sourceContext="playlist"; session.sourcePlaylistId="playlist-id";
        ListeningReporter.Event event = new ListeningReporter.Event(session,30,31000);
        Gson gson=new Gson();
        var restored=gson.fromJson(gson.toJson(event),ListeningReporter.Event.class);
        assertEquals(session.clientEventId,restored.clientEventId);
        assertEquals("playlist",restored.sourceContext);
        assertEquals("playlist-id",restored.sourcePlaylistId);
        assertEquals(30,restored.listenedSeconds);
        assertFalse(gson.toJson(event).contains("account-a"));
    }
}
