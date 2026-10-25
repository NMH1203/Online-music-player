package vn.edu.usth.online_music_player.backend.catalog;

import java.util.UUID;

public record TrackCatalogResponse(
        UUID id, String title, int durationSeconds, String artistName, String albumTitle) {}
