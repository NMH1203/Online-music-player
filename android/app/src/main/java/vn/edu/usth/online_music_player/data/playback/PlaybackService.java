package vn.edu.usth.online_music_player.data.playback;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import vn.edu.usth.online_music_player.data.session.AccessTokenStore;

/** Owns the single app player so audio and its queue survive Activity and screen changes. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    @Nullable private ExoPlayer player;
    @Nullable private MediaSession mediaSession;
    @Nullable private BoundaryAds boundaryAds;
    @Nullable private ListeningReporter listeningReporter;

    @Override
    public void onCreate() {
        super.onCreate();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request.Builder request = chain.request().newBuilder()
                            .tag(vn.edu.usth.online_music_player.data.session.SessionManager.Stamp.class,
                                    vn.edu.usth.online_music_player.data.session.SessionManager.get().stamp());
                    String token = AccessTokenStore.getAccessToken();
                    if (token != null) {
                        request.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(request.build());
                })
                .authenticator((route, response) -> vn.edu.usth.online_music_player.data.session.SessionManager.get().authenticate(response))
                .build();
        DataSource.Factory dataSourceFactory = new androidx.media3.datasource.DefaultDataSource.Factory(
                this, new OkHttpDataSource.Factory(httpClient));
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();
        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                true);
        player.setHandleAudioBecomingNoisy(true);
        listeningReporter = new ListeningReporter(player);
        player.addListener(listeningReporter);
        boundaryAds = new BoundaryAds(this, player);
        mediaSession = new MediaSession.Builder(this, boundaryAds.sessionPlayer()).build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (listeningReporter != null) { listeningReporter.finish(); listeningReporter = null; }
        if (boundaryAds != null) { boundaryAds.close(); boundaryAds = null; }
        if (mediaSession != null) {
            Player sessionPlayer = mediaSession.getPlayer();
            mediaSession.release();
            mediaSession = null;
            sessionPlayer.release();
            player = null;
        }
        super.onDestroy();
    }
}
