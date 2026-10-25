package vn.edu.usth.online_music_player.backend.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tracks")
public class TrackCatalogController {
    private final TrackCatalogService trackCatalogService;

    public TrackCatalogController(TrackCatalogService trackCatalogService) {
        this.trackCatalogService = trackCatalogService;
    }

    @GetMapping
    public List<TrackCatalogResponse> listPublishedTracks(
            @RequestParam(name = "query", required = false) String query) {
        return trackCatalogService.listPublishedTracks(query);
    }
}
