package vn.edu.usth.online_music_player.data.playlist;

public final class PlaylistWriteRequest {
    private final String name;
    private final String description;

    public PlaylistWriteRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
