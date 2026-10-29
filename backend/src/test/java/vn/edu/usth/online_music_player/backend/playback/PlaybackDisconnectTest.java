package vn.edu.usth.online_music_player.backend.playback;

import io.minio.*;
import java.io.*;
import java.util.UUID;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import okhttp3.Headers;
import org.junit.Test;
import static org.junit.Assert.*;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;

public class PlaybackDisconnectTest {
    @Test public void clientDisconnectClosesStorageStream() throws Exception {
        boolean[] closed = {false};
        UUID id = UUID.randomUUID();
        PlaybackService tracks = new PlaybackService(null) {
            @Override public TrackPlayback requirePlayableTrack(UUID ignored) {
                return new TrackPlayback(id, "Track", id, "Artist", null, null, null, 30,
                        "audio/mpeg", 4, "music-demo", "track.mp3", "Test");
            }
        };
        MinioClient storage = new MinioClient(MinioClient.builder().endpoint("http://127.0.0.1:9000").build()) {
            @Override public GetObjectResponse getObject(GetObjectArgs args) {
                return new GetObjectResponse(Headers.of(), args.bucket(), args.region(), args.object(),
                        new ByteArrayInputStream(new byte[4]) {
                            @Override public void close() { closed[0] = true; }
                        });
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override public ServletOutputStream getOutputStream() {
                return new ServletOutputStream() {
                    public boolean isReady() { return true; }
                    public void setWriteListener(WriteListener listener) {}
                    public void write(int value) throws IOException { throw new IOException("Client disconnected"); }
                };
            }
        };
        try {
            new PlaybackController(tracks, new CurrentUserId(), storage).stream(id::toString, id, null, response);
            fail("Expected disconnected output");
        } catch (IOException expected) { assertTrue(closed[0]); }
    }
}
