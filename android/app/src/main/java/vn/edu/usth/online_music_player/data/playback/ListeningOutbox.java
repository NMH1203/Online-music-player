package vn.edu.usth.online_music_player.data.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Tag;
import vn.edu.usth.online_music_player.data.network.ApiClient;
import vn.edu.usth.online_music_player.data.session.SessionManager;

/** App-scoped retry loop. Completed listens survive process death; replay resumes on next app start/login. */
public final class ListeningOutbox {
    interface Api {
        @POST("listening-events") Call<Void> report(@Body ListeningReporter.Event event, @Tag SessionManager.Stamp account);
    }
    private static ListeningOutbox instance;
    private final EventDelivery delivery;
    private final Gson gson = new Gson();
    private final Api api = ApiClient.create(Api.class);
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private long retryAt;
    private int delaySeconds = 5;
    private ListeningOutbox(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("listening_outbox", Context.MODE_PRIVATE);
        delivery = new EventDelivery(new EventDelivery.Store() {
            public Map<String, String> entries() {
                Map<String, String> result = new HashMap<>();
                prefs.getAll().forEach((key, value) -> { if (value instanceof String) result.put(key, (String) value); });
                return result;
            }
            public void put(String key, String json) throws IOException {
                if (!prefs.edit().putString(key, json).commit()) throw new IOException("Cannot save listening event");
            }
            public void remove(String key) throws IOException {
                if (!prefs.edit().remove(key).commit()) throw new IOException("Cannot acknowledge listening event");
            }
        });
        worker.scheduleWithFixedDelay(this::flush, 5, 5, TimeUnit.SECONDS);
    }
    public static synchronized void initialize(Context context) {
        if (instance == null) instance = new ListeningOutbox(context.getApplicationContext());
    }
    static synchronized ListeningOutbox get() { return instance; }
    void enqueue(String owner, ListeningReporter.Event event) {
        if (owner == null) return;
        try {
            delivery.enqueue(owner, event.clientEventId, gson.toJson(event));
            worker.execute(this::flush);
        } catch (IOException e) { Log.e("ListeningOutbox", "Cannot persist listen", e); }
    }
    private void flush() {
        if (android.os.SystemClock.elapsedRealtime() < retryAt) return;
        SessionManager manager = SessionManager.get();
        String owner = manager.currentUserId();
        if (owner == null) return;
        boolean complete = false;
        try {
            complete = delivery.drain(owner, json -> {
                SessionManager.Stamp stamp;
                synchronized (manager) {
                    if (!owner.equals(manager.currentUserId())) throw new IOException("Account changed");
                    stamp = manager.stamp();
                }
                retrofit2.Response<Void> response = api.report(gson.fromJson(json, ListeningReporter.Event.class), stamp).execute();
                if (response.errorBody() != null) response.errorBody().close();
                return response.code();
            });
        } catch (IOException e) { Log.d("ListeningOutbox", "Retrying saved listens later"); }
        catch (RuntimeException e) { Log.e("ListeningOutbox", "Unable to replay saved listens", e); }
        if (complete) { retryAt = 0; delaySeconds = 5; }
        else {
            retryAt = android.os.SystemClock.elapsedRealtime() + delaySeconds * 1000L;
            delaySeconds = Math.min(300, delaySeconds * 2);
        }
    }
}
