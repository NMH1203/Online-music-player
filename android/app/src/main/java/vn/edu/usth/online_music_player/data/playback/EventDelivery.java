package vn.edu.usth.online_music_player.data.playback;

import java.io.IOException;
import java.util.Map;

/** Durable store is authoritative: remove only acknowledged or permanently rejected events. */
final class EventDelivery {
    interface Store {
        Map<String, String> entries();
        void put(String key, String json) throws IOException;
        void remove(String key) throws IOException;
    }
    interface Transport { int send(String json) throws IOException; }
    private final Store store;
    EventDelivery(Store store) { this.store = store; }
    void enqueue(String owner, String eventId, String json) throws IOException {
        store.put(owner + ":" + eventId, json);
    }
    /** Returns false on a transient failure; stable IDs make retries safe after a lost response. */
    boolean drain(String owner, Transport transport) throws IOException {
        if (owner == null) return true;
        int sent = 0;
        for (Map.Entry<String, String> entry : store.entries().entrySet()) {
            if (!entry.getKey().startsWith(owner + ":")) continue;
            if (++sent > 100) return false;
            int code = transport.send(entry.getValue());
            if (code == 401 || code == 403 || code == 408 || code == 429 || code >= 500) return false;
            if (code >= 200 && code < 300 || code >= 400 && code < 500) store.remove(entry.getKey());
            else return false;
        }
        return true;
    }
}
