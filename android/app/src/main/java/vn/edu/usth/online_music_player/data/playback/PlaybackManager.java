package vn.edu.usth.online_music_player.data.playback;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
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
    /** MediaMetadata extras keys used by Now Playing to open the artist/album pages. */
    public static final String EXTRA_ARTIST_ID = "artistId";
    public static final String EXTRA_ALBUM_ID = "albumId";

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

    private final List<Listener> listeners = new ArrayList<>();
    @Nullable private MediaController controller;
    @Nullable private ListenableFuture<MediaController> controllerFuture;
    @Nullable private Runnable pendingCommand;

    public PlaybackManager(Context context) {
        applicationContext = context.getApplicationContext();
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        listener.onPlaybackChanged();
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
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
                for (Listener current : new ArrayList<>(listeners)) {
                    current.onConnectionFailed();
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

    /** Jumps to a queue entry by its index in the player's timeline. */
    public void playItemAt(int index) {
        if (controller != null && index >= 0 && index < controller.getMediaItemCount()) {
            controller.seekTo(index, 0L);
            controller.play();
        }
    }

    public void removeItemAt(int index) {
        if (controller != null && index >= 0 && index < controller.getMediaItemCount()
                && index != controller.getCurrentMediaItemIndex()) {
            controller.removeMediaItem(index);
        }
    }

    /** Drops everything after the current track ("Clear" on the Queue screen). */
    public void clearUpNext() {
        if (controller != null) {
            int count = controller.getMediaItemCount();
            int from = controller.getCurrentMediaItemIndex() + 1;
            if (from < count) controller.removeMediaItems(from, count);
        }
    }

    /** Re-prepares after a playback error, keeping the queue and position. */
    public void retry() {
        if (controller != null) {
            controller.prepare();
            controller.play();
        }
    }

    /** Stops playback and drops the queue, e.g. when the user is signed out. */
    public void stop() {
        if (controller != null) {
            controller.stop();
            controller.clearMediaItems();
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

    /** Resolves a server-relative path (stream or artwork) against the API base URL. */
    @Nullable
    public static String absoluteUrl(@Nullable String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return BuildConfig.BASE_URL + (path.startsWith("/") ? path.substring(1) : path);
    }

    private MediaItem toMediaItem(TrackPlayback track) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_ARTIST_ID, track.getArtistId());
        extras.putString(EXTRA_ALBUM_ID, track.getAlbumId());
        String cover = absoluteUrl(track.getCoverUrl());
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(track.getTitle())
                .setArtist(track.getArtistName())
                .setAlbumTitle(track.getAlbumTitle())
                .setArtworkUri(cover == null ? null : Uri.parse(cover))
                .setExtras(extras)
                .build();
        return new MediaItem.Builder()
                .setMediaId(track.getTrackId())
                .setUri(Uri.parse(absoluteUrl(track.getStreamUrl())))
                .setMimeType(track.getMimeType())
                .setMediaMetadata(metadata)
                .build();
    }

    private void notifyPlaybackChanged() {
        for (Listener current : new ArrayList<>(listeners)) {
            current.onPlaybackChanged();
        }
    }
}
