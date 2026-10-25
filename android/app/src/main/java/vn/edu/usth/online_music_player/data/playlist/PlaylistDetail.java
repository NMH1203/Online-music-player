package vn.edu.usth.online_music_player.data.playlist;

import java.util.Collections;
import java.util.List;

public final class PlaylistDetail {
    private String id;
    private String name;
    private String description;
    private List<PlaylistTrack> tracks;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<PlaylistTrack> getTracks() {
        return tracks == null ? Collections.emptyList() : tracks;
    }
}
