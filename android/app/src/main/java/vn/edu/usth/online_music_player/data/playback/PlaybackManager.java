package vn.edu.usth.online_music_player.data.playback;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import vn.edu.usth.online_music_player.BuildConfig;

/** Activity-scoped controller for queue and transport commands sent to PlaybackService. */
public final class PlaybackManager {
    public interface Listener {
        void onPlaybackChanged();
        void onConnectionFailed();
    }

    private final Context applicationContext;
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(Player player, Player.Events events) {
            notifyPlaybackChanged();
        }
    };

    @Nullable private Listener listener;
    @Nullable private MediaController controller;
    @Nullable private ListenableFuture<MediaController> controllerFuture;
    @Nullable private Runnable pendingCommand;

    public PlaybackManager(Context context) {
        applicationContext = context.getApplicationContext();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
        notifyPlaybackChanged();
    }

    public void connect() {
        if (controllerFuture != null) {
            return;
        }
        SessionToken token = new SessionToken(
                applicationContext,
                new ComponentName(applicationContext, PlaybackService.class));
        ListenableFuture<MediaController> future =
                new MediaController.Builder(applicationContext, token).buildAsync();
        controllerFuture = future;
        future.addListener(() -> {
            if (controllerFuture != future) {
                return;
            }
            try {
                controller = future.get();
                controller.addListener(playerListener);
                notifyPlaybackChanged();
                Runnable command = pendingCommand;
                pendingCommand = null;
                if (command != null) {
                    command.run();
                }
            } catch (ExecutionException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                controllerFuture = null;
                Listener currentListener = listener;
                if (currentListener != null) {
                    currentListener.onConnectionFailed();
                }
            }
        }, ContextCompat.getMainExecutor(applicationContext));
    }

    public void disconnect() {
        ListenableFuture<MediaController> future = controllerFuture;
        controllerFuture = null;
        pendingCommand = null;
        if (controller != null) {
            controller.removeListener(playerListener);
            controller = null;
        }
        if (future != null) {
            MediaController.releaseFuture(future);
        }
    }

    public void playQueue(List<TrackPlayback> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty() || startIndex < 0 || startIndex >= tracks.size()) {
            return;
        }
        List<MediaItem> items = new ArrayList<>(tracks.size());
        for (TrackPlayback track : tracks) {
            items.add(toMediaItem(track));
        }
        runWhenConnected(() -> {
            controller.setMediaItems(items, startIndex, 0L);
            controller.prepare();
            controller.play();
        });
    }

    public void addToQueue(TrackPlayback track, boolean playImmediately) {
        MediaItem item = toMediaItem(track);
        runWhenConnected(() -> {
            if (playImmediately || controller.getMediaItemCount() == 0) {
                controller.setMediaItem(item);
                controller.prepare();
                controller.play();
            } else {
                controller.addMediaItem(item);
            }
        });
    }

    public void togglePlayPause() {
        if (controller == null) {
            connect();
            return;
        }
        if (controller.isPlaying()) {
            controller.pause();
        } else {
            controller.play();
        }
    }

    public void skipNext() {
        if (controller != null && controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem();
        }
    }

    public void skipPrevious() {
        if (controller != null) {
            controller.seekToPreviousMediaItem();
        }
    }

    public void seekTo(long positionMs) {
        if (controller != null) {
            controller.seekTo(positionMs);
        }
    }

    @Nullable
    public MediaController getController() {
        return controller;
    }

    private void runWhenConnected(Runnable command) {
        if (controller != null) {
            command.run();
        } else {
            pendingCommand = command;
            connect();
        }
    }

    private MediaItem toMediaItem(TrackPlayback track) {
        String streamUrl = track.getStreamUrl();
        String absoluteUrl = streamUrl.startsWith("http://") || streamUrl.startsWith("https://")
                ? streamUrl
                : BuildConfig.BASE_URL + (streamUrl.startsWith("/") ? streamUrl.substring(1) : streamUrl);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(track.getTitle())
                .setArtist(track.getArtistName())
                .build();
        return new MediaItem.Builder()
                .setMediaId(track.getTrackId())
                .setUri(Uri.parse(absoluteUrl))
                .setMimeType(track.getMimeType())
                .setMediaMetadata(metadata)
                .build();
    }

    private void notifyPlaybackChanged() {
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onPlaybackChanged();
        }
    }
}
