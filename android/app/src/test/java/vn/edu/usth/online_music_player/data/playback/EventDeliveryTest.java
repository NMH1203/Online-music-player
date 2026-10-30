package vn.edu.usth.online_music_player.data.playback;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.io.IOException;

public class EventDeliveryTest {
    static class MemoryStore implements EventDelivery.Store {
        final Map<String,String> data = new LinkedHashMap<>();
        public Map<String,String> entries() { return new LinkedHashMap<>(data); }
        public void put(String key,String value) { data.put(key,value); }
        public void remove(String key) { data.remove(key); }
    }
    @Test public void retriesSamePayloadAfterNetworkFailureAndRestart() throws Exception {
        var store = new MemoryStore();
        var queue = new EventDelivery(store);
        queue.enqueue("alice","event-1","payload-with-stable-event-id");
        try { queue.drain("alice", json -> { throw new IOException("lost response"); }); fail(); }
        catch(IOException expected) { assertEquals(1,store.data.size()); }
        var restarted = new EventDelivery(store);
        assertTrue(restarted.drain("alice", json -> { assertEquals("payload-with-stable-event-id",json); return 200; }));
        assertTrue(store.data.isEmpty());
    }
    @Test public void neverSendsAnotherUsersEvents() throws Exception {
        var store = new MemoryStore(); var queue = new EventDelivery(store);
        queue.enqueue("alice","1","alice-event"); queue.enqueue("bob","2","bob-event");
        assertTrue(queue.drain("bob", json -> { assertEquals("bob-event",json); return 200; }));
        assertEquals(Set.of("alice:1"),store.data.keySet());
        queue.drain(null,json -> { fail("Logged-out delivery"); return 200; });
    }
    @Test public void transientStatusesKeepEventsButInvalidPayloadIsRemoved() throws Exception {
        for(int status : new int[]{401,403,408,429,500,503}) {
            var store = new MemoryStore(); var queue = new EventDelivery(store);
            queue.enqueue("alice","1","event");
            assertFalse(queue.drain("alice",json -> status)); assertEquals(1,store.data.size());
            assertTrue(queue.drain("alice",json -> 400)); assertTrue(store.data.isEmpty());
        }
    }
    @Test public void burstIsBoundedAndContinuesLater() throws Exception {
        var store = new MemoryStore(); var queue = new EventDelivery(store);
        for(int i=0;i<101;i++) queue.enqueue("alice",""+i,"event");
        assertFalse(queue.drain("alice",json -> 200)); assertEquals(1,store.data.size());
        assertTrue(queue.drain("alice",json -> 200)); assertTrue(store.data.isEmpty());
    }
}
