package vn.edu.usth.online_music_player.backend.common;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details, String request_id) {}
