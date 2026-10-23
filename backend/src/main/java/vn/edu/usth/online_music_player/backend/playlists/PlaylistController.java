package vn.edu.usth.online_music_player.backend.playlists;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;

@RestController
@RequestMapping("/me/playlists")
public class PlaylistController {
    private final PlaylistService playlistService;
    private final CurrentUserId currentUserId;

    public PlaylistController(PlaylistService playlistService, CurrentUserId currentUserId) {
        this.playlistService = playlistService;
        this.currentUserId = currentUserId;
    }

    @GetMapping
    public List<PlaylistSummaryResponse> listPlaylists(Principal principal) {
        return playlistService.listPlaylists(currentUserId.require(principal));
    }

    @GetMapping("/quota")
    public PlaylistQuotaResponse getPlaylistQuota(Principal principal) {
        return playlistService.getPlaylistQuota(currentUserId.require(principal));
    }

    @PostMapping
    public ResponseEntity<PlaylistSummaryResponse> createPlaylist(
            Principal principal, @Valid @RequestBody CreatePlaylistRequest request) {
        PlaylistSummaryResponse created =
                playlistService.createPlaylist(currentUserId.require(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{playlistId}")
    public PlaylistDetailResponse getPlaylist(
            Principal principal, @PathVariable("playlistId") UUID playlistId) {
        return playlistService.getPlaylist(currentUserId.require(principal), playlistId);
    }

    @PutMapping("/{playlistId}")
    public PlaylistSummaryResponse updatePlaylist(
            Principal principal,
            @PathVariable("playlistId") UUID playlistId,
            @Valid @RequestBody UpdatePlaylistRequest request) {
        return playlistService.updatePlaylist(
                currentUserId.require(principal), playlistId, request);
    }

    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Void> deletePlaylist(
            Principal principal, @PathVariable("playlistId") UUID playlistId) {
        playlistService.deletePlaylist(currentUserId.require(principal), playlistId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<Void> addTrack(
            Principal principal,
            @PathVariable("playlistId") UUID playlistId,
            @PathVariable("trackId") UUID trackId) {
        playlistService.addTrack(currentUserId.require(principal), playlistId, trackId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<Void> removeTrack(
            Principal principal,
            @PathVariable("playlistId") UUID playlistId,
            @PathVariable("trackId") UUID trackId) {
        playlistService.removeTrack(currentUserId.require(principal), playlistId, trackId);
        return ResponseEntity.noContent().build();
    }
}
