package vn.edu.usth.online_music_player.backend.history;

import java.util.UUID;

public record ListeningEventResponse(UUID clientEventId, boolean countedAsPlay) {}
