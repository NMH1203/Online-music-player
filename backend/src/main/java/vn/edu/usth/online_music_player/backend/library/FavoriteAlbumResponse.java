package vn.edu.usth.online_music_player.backend.library;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FavoriteAlbumResponse(
        UUID id,
        String title,
        UUID artistId,
        String artistName,
        LocalDate releaseDate,
        Instant addedAt) {}
