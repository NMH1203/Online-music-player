package vn.edu.usth.online_music_player.backend.history;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ListeningEventRequest(
        @NotNull UUID clientEventId,
        @NotNull UUID trackId,
        @NotBlank @Size(max = 64) String sourceContext,
        UUID sourcePlaylistId,
        @NotNull Instant startedAt,
        Instant endedAt,
        @NotNull Integer listenedSeconds) {}
