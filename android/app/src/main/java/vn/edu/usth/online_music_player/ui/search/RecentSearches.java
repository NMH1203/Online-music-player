package vn.edu.usth.online_music_player.ui.search;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Recent search queries kept on the device (a simple setting, not cached catalog data). */
final class RecentSearches {
    static final int MAX = 10;
    private static final String KEY = "queries";
    // Unit separator never appears in typed queries, so it is a safe delimiter.
    private static final String SEPARATOR = "\u001F";
    private final SharedPreferences preferences;

    RecentSearches(Context context) {
        preferences = context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE);
    }

    List<String> load() {
        String stored = preferences.getString(KEY, "");
        return stored.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(stored.split(SEPARATOR)));
    }

    void add(String query) {
        save(withQuery(load(), query));
    }

    void remove(String query) {
        List<String> queries = load();
        queries.remove(query);
        save(queries);
    }

    void clear() {
        preferences.edit().remove(KEY).apply();
    }

    private void save(List<String> queries) {
        preferences.edit().putString(KEY, String.join(SEPARATOR, queries)).apply();
    }

    /** Puts the query first, drops case-insensitive duplicates and keeps at most {@link #MAX}. */
    static List<String> withQuery(List<String> current, String query) {
        String trimmed = query == null ? "" : query.trim().replace(SEPARATOR, "");
        List<String> result = new ArrayList<>();
        if (!trimmed.isEmpty()) result.add(trimmed);
        for (String existing : current) {
            if (result.size() >= MAX) break;
            if (!existing.equalsIgnoreCase(trimmed)) result.add(existing);
        }
        return result;
    }
}
