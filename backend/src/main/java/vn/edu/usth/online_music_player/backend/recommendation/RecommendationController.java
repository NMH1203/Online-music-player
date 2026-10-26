package vn.edu.usth.online_music_player.backend.recommendation;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;

@RestController
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final CurrentUserId currentUserId;

    public RecommendationController(
            RecommendationService recommendationService, CurrentUserId currentUserId) {
        this.recommendationService = recommendationService;
        this.currentUserId = currentUserId;
    }

    @GetMapping("/recommendations")
    public List<RecommendationTrackResponse> getRecommendations(
            Principal principal,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "currentTrackId", required = false) UUID currentTrackId) {
        UUID userId = currentUserId.require(principal);
        return recommendationService.recommend(userId, limit, currentTrackId);
    }
}
