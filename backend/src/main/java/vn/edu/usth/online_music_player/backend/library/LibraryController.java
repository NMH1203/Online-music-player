package vn.edu.usth.online_music_player.backend.library;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;

@RestController
@RequestMapping("/me/library")
public class LibraryController {
    private final LibraryService libraryService;
    private final CurrentUserId currentUserId;

    public LibraryController(LibraryService libraryService, CurrentUserId currentUserId) {
        this.libraryService = libraryService;
        this.currentUserId = currentUserId;
    }

    @GetMapping("/tracks")
    public List<FavoriteTrackResponse> listFavoriteTracks(Principal principal) {
        return libraryService.listFavoriteTracks(currentUserId.require(principal));
    }

    @PutMapping("/tracks/{trackId}")
    public ResponseEntity<Void> addFavoriteTrack(
            Principal principal, @PathVariable("trackId") UUID trackId) {
        libraryService.addFavoriteTrack(currentUserId.require(principal), trackId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tracks/{trackId}")
    public ResponseEntity<Void> removeFavoriteTrack(
            Principal principal, @PathVariable("trackId") UUID trackId) {
        libraryService.removeFavoriteTrack(currentUserId.require(principal), trackId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/albums")
    public List<FavoriteAlbumResponse> listFavoriteAlbums(Principal principal) {
        return libraryService.listFavoriteAlbums(currentUserId.require(principal));
    }

    @PutMapping("/albums/{albumId}")
    public ResponseEntity<Void> addFavoriteAlbum(
            Principal principal, @PathVariable("albumId") UUID albumId) {
        libraryService.addFavoriteAlbum(currentUserId.require(principal), albumId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/albums/{albumId}")
    public ResponseEntity<Void> removeFavoriteAlbum(
            Principal principal, @PathVariable("albumId") UUID albumId) {
        libraryService.removeFavoriteAlbum(currentUserId.require(principal), albumId);
        return ResponseEntity.noContent().build();
    }
}
