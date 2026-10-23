package vn.edu.usth.online_music_player.data.playlist;

public final class PlaylistQuota {
    private long playlistCount;
    private Integer maxPlaylists;
    private boolean canCreatePlaylist;

    public long getPlaylistCount() {
        return playlistCount;
    }

    public Integer getMaxPlaylists() {
        return maxPlaylists;
    }

    public boolean canCreatePlaylist() {
        return canCreatePlaylist;
    }
}
