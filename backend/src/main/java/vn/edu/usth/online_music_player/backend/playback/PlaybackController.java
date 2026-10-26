package vn.edu.usth.online_music_player.backend.playback;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;

@RestController
@RequestMapping("/tracks/{trackId}")
public class PlaybackController {
    private final PlaybackService playbackService;
    private final CurrentUserId currentUserId;
    private final MinioClient minioClient;

    public PlaybackController(
            PlaybackService playbackService, CurrentUserId currentUserId, MinioClient minioClient) {
        this.playbackService = playbackService;
        this.currentUserId = currentUserId;
        this.minioClient = minioClient;
    }

    @GetMapping("/playback")
    public TrackPlaybackResponse getPlayback(Principal principal, @PathVariable UUID trackId) {
        currentUserId.require(principal);
        TrackPlayback track = playbackService.requirePlayableTrack(trackId);
        return new TrackPlaybackResponse(
                track.id(),
                track.title(),
                track.artistName(),
                track.durationSeconds(),
                track.mimeType(),
                track.fileSizeBytes(),
                "/tracks/" + track.id() + "/stream",
                track.attributionText(),
                track.artistId(),
                track.albumId(),
                track.albumTitle(),
                track.coverUrl());
    }

    @GetMapping("/stream")
    public ResponseEntity<StreamingResponseBody> stream(
            Principal principal,
            @PathVariable UUID trackId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        currentUserId.require(principal);
        TrackPlayback track = playbackService.requirePlayableTrack(trackId);

        ByteRange range;
        try {
            range = ByteRange.parse(rangeHeader, track.fileSizeBytes());
        } catch (InvalidByteRangeException exception) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + track.fileSizeBytes())
                    .build();
        }

        MediaType contentType = audioContentType(track.mimeType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentLength(range.length());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setCacheControl("private, no-store");
        if (range.partial()) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.start() + "-" + range.end() + "/" + track.fileSizeBytes());
        }

        StreamingResponseBody body = output -> {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(track.bucketName())
                    .object(track.objectKey())
                    .offset(range.start())
                    .length(range.length())
                    .build();
            try (InputStream input = minioClient.getObject(args)) {
                input.transferTo(output);
            } catch (Exception exception) {
                throw new IOException("Unable to stream the audio object from storage.", exception);
            }
        };

        return ResponseEntity.status(range.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .headers(headers)
                .body(body);
    }

    private MediaType audioContentType(String value) {
        try {
            MediaType contentType = MediaType.parseMediaType(value);
            if ("audio".equalsIgnoreCase(contentType.getType())) {
                return contentType;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to the catalog's default audio type for malformed legacy metadata.
        }
        return MediaType.parseMediaType("audio/mpeg");
    }
}
