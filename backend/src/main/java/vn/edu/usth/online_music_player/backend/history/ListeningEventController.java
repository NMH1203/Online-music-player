package vn.edu.usth.online_music_player.backend.history;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;

@RestController
public class ListeningEventController {
    private final ListeningEventService listeningEventService;
    private final CurrentUserId currentUserId;

    public ListeningEventController(
            ListeningEventService listeningEventService, CurrentUserId currentUserId) {
        this.listeningEventService = listeningEventService;
        this.currentUserId = currentUserId;
    }

    @PostMapping("/listening-events")
    public ListeningEventResponse recordListeningEvent(
            Principal principal, @Valid @RequestBody ListeningEventRequest request) {
        return listeningEventService.recordEvent(currentUserId.require(principal), request);
    }
}
