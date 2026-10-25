package vn.edu.usth.online_music_player.backend.playlists;

public record PlaylistQuotaResponse(
        long playlistCount,
        Integer maxPlaylists,
        boolean canCreatePlaylist) {}
