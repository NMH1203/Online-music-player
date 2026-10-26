package vn.edu.usth.online_music_player.backend.playback;

import java.util.UUID;

/** Internal track and object metadata; storage identifiers are never serialized to the app. */
record TrackPlayback(
        UUID id,
        String title,
        UUID artistId,
        String artistName,
        UUID albumId,
        String albumTitle,
        String coverUrl,
        int durationSeconds,
        String mimeType,
        long fileSizeBytes,
        String bucketName,
        String objectKey,
        String attributionText) {}
