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
public final class PlaybackService extends MediaSessionService {
    @Nullable private ExoPlayer player;
    @Nullable private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request.Builder request = chain.request().newBuilder();
                    String token = AccessTokenStore.getAccessToken();
                    if (token != null) {
                        request.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(request.build());
                })
                .build();
        DataSource.Factory dataSourceFactory = new OkHttpDataSource.Factory(httpClient);
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
        mediaSession = new MediaSession.Builder(this, player).build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
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
