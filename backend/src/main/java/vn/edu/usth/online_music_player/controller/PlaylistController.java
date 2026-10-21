package vn.edu.usth.online_music_player.controller;

import vn.edu.usth.online_music_player.dto.PlaylistRequest;
import vn.edu.usth.online_music_player.service.PlaylistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import main.java.vn.edu.usth.online_music_player.dto.FavoriteRequest;
import main.java.vn.edu.usth.online_music_player.dto.PlaylistTrackRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/playlists")
public class PlaylistController {
    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @PostMapping
    public ResponseEntity<?> createPlaylist(@RequestAttribute("userId") Long userId, @RequestBody PlaylistRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên playlist không được để trống"));
        }
        return ResponseEntity.ok(playlistService.createPlaylist(userId, request));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getUserPlaylists(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(playlistService.getPlaylistsByUserId(userId));
    }
        // 4. API Thêm bài hát vào Playlist
    @PostMapping("/{id}/tracks")
    public ResponseEntity<?> addTrack(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id,
            @RequestBody PlaylistTrackRequest request) {
        
        boolean success = playlistService.addTrackToPlaylist(userId, id, request.getTrackId());
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Thêm bài hát vào playlist thành công"));
        }
        return ResponseEntity.status(403).body(Map.of("error", "Không có quyền chỉnh sửa playlist này"));
    }

    // 5. API Xóa bài hát khỏi Playlist
    @DeleteMapping("/{id}/tracks/{trackId}")
    public ResponseEntity<?> removeTrack(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id,
            @PathVariable Long trackId) {
        
        boolean success = playlistService.removeTrackFromPlaylist(userId, id, trackId);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Xóa bài hát khỏi playlist thành công"));
        }
        return ResponseEntity.status(403).body(Map.of("error", "Không có quyền chỉnh sửa playlist này"));
    }

    // 6. API Thao tác Yêu thích / Bỏ yêu thích (Toggle Favorite)
    @PostMapping("/favorites/toggle")
    public ResponseEntity<?> toggleFavorite(
            @RequestAttribute("userId") Long userId,
            @RequestBody FavoriteRequest request) {
        
        if (request.getTargetId() == null || request.getType() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thiếu tham số targetId hoặc type"));
        }
        
        playlistService.toggleFavorite(userId, request.getTargetId(), request.getType());
        return ResponseEntity.ok(Map.of("message", "Cập nhật trạng thái yêu thích thành công"));
    }

    // 7. API Lấy toàn bộ bài hát đã thích đổ về tab Library của Android
    @GetMapping("/favorites/tracks")
    public ResponseEntity<?> getFavoriteTracks(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(playlistService.getFavoriteTracks(userId));
    }

}
