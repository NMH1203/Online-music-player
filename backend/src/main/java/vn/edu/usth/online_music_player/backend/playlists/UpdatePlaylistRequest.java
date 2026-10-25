package vn.edu.usth.online_music_player.backend.playlists;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePlaylistRequest(
        @NotBlank(message = "Tên playlist không được để trống.")
                @Size(max = 200, message = "Tên playlist không được dài quá 200 ký tự.")
                String name,
        @Size(max = 2000, message = "Mô tả không được dài quá 2000 ký tự.") String description) {}
