package vn.edu.usth.online_music_player.ui.player;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import java.util.HashSet;
import java.util.Set;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.library.FavoriteTrack;
import vn.edu.usth.online_music_player.data.library.LibraryContent;
import vn.edu.usth.online_music_player.data.library.LibraryRepository;
import vn.edu.usth.online_music_player.data.playback.BoundaryAds;
import vn.edu.usth.online_music_player.data.playback.PlaybackManager;
import vn.edu.usth.online_music_player.databinding.FragmentNowPlayingBinding;
import vn.edu.usth.online_music_player.ui.common.Artwork;

/** Figma "Now Playing": full-screen view of the shared MediaController owned by MainActivity. */
public class NowPlayingFragment extends Fragment implements PlaybackManager.Listener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::renderProgress;
    private final LibraryRepository library = new LibraryRepository();
    private final Set<String> likedTrackIds = new HashSet<>();
    private FragmentNowPlayingBinding binding;
    private PlaybackManager playback;
    private boolean seeking;
    private boolean likesLoaded;
    @Nullable private String trackId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentNowPlayingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        playback = ((MainActivity) requireActivity()).getPlaybackManager();
        NavController nav = Navigation.findNavController(view);
        binding.nowPlayingClose.setOnClickListener(ignored -> nav.navigateUp());
        binding.nowPlayingToggle.setOnClickListener(ignored -> playback.togglePlayPause());
        binding.nowPlayingPrevious.setOnClickListener(ignored -> playback.skipPrevious());
        binding.nowPlayingNext.setOnClickListener(ignored -> playback.skipNext());
        binding.nowPlayingRetry.setOnClickListener(ignored -> playback.retry());
        binding.nowPlayingQueue.setOnClickListener(ignored -> nav.navigate(R.id.queue_fragment));
        binding.nowPlayingLike.setOnClickListener(ignored -> toggleLike());
        binding.nowPlayingAdd.setOnClickListener(ignored -> {
            MediaItem item = currentItem();
            if (item != null) AddToPlaylistSheet.show(getChildFragmentManager(), item);
        });
        binding.nowPlayingSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                MediaController controller = playback.getController();
                if (fromUser && controller != null && hasDuration(controller)) {
                    binding.nowPlayingPosition.setText(
                            Artwork.time(controller.getDuration() * progress / bar.getMax()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                MediaController controller = playback.getController();
                if (controller != null && hasDuration(controller)) {
                    playback.seekTo(controller.getDuration() * bar.getProgress() / bar.getMax());
                }
            }
        });
        loadLikes();
    }

    @Override
    public void onStart() {
        super.onStart();
        playback.addListener(this);
    }

    @Override
    public void onStop() {
        playback.removeListener(this);
        handler.removeCallbacks(ticker);
        super.onStop();
    }

    @Override
    public void onPlaybackChanged() {
        if (binding == null) return;
        MediaController controller = playback.getController();
        MediaItem item = currentItem();
        if (controller == null || item == null) {
            // Queue ended or was cleared: nothing left to show.
            if (controller != null) Navigation.findNavController(binding.getRoot()).navigateUp();
            return;
        }
        boolean isAd = BoundaryAds.AD_ID.equals(item.mediaId);
        MediaMetadata metadata = item.mediaMetadata;
        trackId = isAd ? null : item.mediaId;
        String title = metadata.title == null ? getString(R.string.now_playing_unknown_title)
                : metadata.title.toString();
        binding.nowPlayingTitle.setText(title);
        binding.nowPlayingContext.setText(metadata.albumTitle == null ? "" : metadata.albumTitle);
        Artwork.load(binding.nowPlayingArtwork, metadata.artworkUri);

        Bundle extras = metadata.extras;
        String artistId = extras == null ? null : extras.getString(PlaybackManager.EXTRA_ARTIST_ID);
        String albumId = extras == null ? null : extras.getString(PlaybackManager.EXTRA_ALBUM_ID);
        binding.nowPlayingArtist.setText(metadata.artist == null ? "" : metadata.artist);
        bindLink(binding.nowPlayingArtist, R.id.artistDetailFragment, "artistId", artistId);
        binding.nowPlayingAlbum.setVisibility(metadata.albumTitle == null ? View.GONE : View.VISIBLE);
        if (metadata.albumTitle != null) {
            binding.nowPlayingAlbum.setText(getString(R.string.now_playing_album, metadata.albumTitle));
        }
        bindLink(binding.nowPlayingAlbum, R.id.albumDetailFragment, "albumId", albumId);
        binding.nowPlayingLike.setVisibility(isAd ? View.INVISIBLE : View.VISIBLE);
        binding.nowPlayingAdd.setVisibility(isAd ? View.INVISIBLE : View.VISIBLE);
        renderLike();

        boolean error = controller.getPlayerError() != null;
        boolean buffering = controller.getPlaybackState() == Player.STATE_BUFFERING && !error;
        boolean loading = buffering && controller.getCurrentPosition() < 1000;
        boolean playing = controller.isPlaying();

        binding.nowPlayingError.setVisibility(error ? View.VISIBLE : View.GONE);
        binding.nowPlayingStatus.setVisibility(error ? View.GONE : View.VISIBLE);
        binding.nowPlayingArtworkProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.nowPlayingToggleProgress.setVisibility(buffering && !loading ? View.VISIBLE : View.GONE);
        if (loading) {
            setStatus(getString(R.string.now_playing_loading_title),
                    getString(R.string.now_playing_loading_message, title), 0);
        } else if (buffering) {
            setStatus(getString(R.string.now_playing_buffering_title),
                    getString(R.string.now_playing_buffering_message), 0);
        } else {
            setStatus(getString(playing ? R.string.now_playing_status_playing
                    : R.string.now_playing_status_paused), null, playing ? R.drawable.ic_audio_lines : 0);
        }

        boolean ready = !loading && !error;
        // Grey when disabled comes from the PlayLarge style's colour selector.
        binding.nowPlayingToggle.setEnabled(ready);
        binding.nowPlayingToggle.setIconResource(buffering && !loading ? 0
                : playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.nowPlayingToggle.setContentDescription(
                getString(playing ? R.string.action_pause : R.string.action_play));
        binding.nowPlayingNext.setEnabled(controller.hasNextMediaItem());
        binding.nowPlayingSeek.setEnabled(!isAd && hasDuration(controller));
        renderProgress();
    }

    @Override
    public void onConnectionFailed() {
        if (binding != null) binding.nowPlayingError.setVisibility(View.VISIBLE);
    }

    private void setStatus(String title, @Nullable String message, int iconRes) {
        binding.nowPlayingStatusTitle.setText(title);
        binding.nowPlayingStatusTitle.setTextAppearance(message == null
                ? R.style.TextAppearance_App_Caption : R.style.TextAppearance_App_Body);
        binding.nowPlayingStatusTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        binding.nowPlayingStatusTitle.setCompoundDrawableTintList(
                ContextCompat.getColorStateList(requireContext(), R.color.accent_primary));
        binding.nowPlayingStatusMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
        binding.nowPlayingStatusMessage.setText(message);
    }

    private void renderProgress() {
        handler.removeCallbacks(ticker);
        if (binding == null) return;
        MediaController controller = playback.getController();
        if (controller == null) return;
        long duration = hasDuration(controller) ? controller.getDuration() : 0;
        long position = controller.getCurrentPosition();
        binding.nowPlayingDuration.setText(Artwork.time(duration));
        if (!seeking) {
            binding.nowPlayingPosition.setText(Artwork.time(position));
            binding.nowPlayingSeek.setProgress(duration > 0
                    ? (int) Math.min(1000, position * 1000 / duration) : 0);
        }
        if (controller.isPlaying()) handler.postDelayed(ticker, 500);
    }

    private void bindLink(View view, int destination, String argument, @Nullable String id) {
        boolean enabled = id != null && !id.isEmpty();
        view.setEnabled(enabled);
        view.setOnClickListener(enabled ? ignored -> {
            Bundle args = new Bundle();
            args.putString(argument, id);
            Navigation.findNavController(view).navigate(destination, args);
        } : null);
    }

    private void loadLikes() {
        library.load(new LibraryRepository.LoadCallback() {
            @Override
            public void onLoaded(LibraryContent content) {
                likedTrackIds.clear();
                for (FavoriteTrack track : content.getFavoriteTracks()) likedTrackIds.add(track.getId());
                likesLoaded = true;
                if (binding != null) renderLike();
            }

            @Override
            public void onFailed(LibraryRepository.FailureType failureType) {
                // Heart stays neutral; tapping it still works because PUT/DELETE are idempotent.
                likesLoaded = true;
            }
        });
    }

    private void renderLike() {
        boolean liked = trackId != null && likedTrackIds.contains(trackId);
        binding.nowPlayingLike.setEnabled(likesLoaded && trackId != null);
        ImageViewCompat.setImageTintList(binding.nowPlayingLike, ContextCompat.getColorStateList(
                requireContext(), liked ? R.color.accent_primary : R.color.icon_primary));
        binding.nowPlayingLike.setContentDescription(
                getString(liked ? R.string.now_playing_unlike : R.string.now_playing_like));
    }

    private void toggleLike() {
        String id = trackId;
        if (id == null) return;
        boolean like = !likedTrackIds.contains(id);
        if (like) likedTrackIds.add(id); else likedTrackIds.remove(id);
        renderLike();
        LibraryRepository.ActionCallback callback = (succeeded, failure) -> {
            if (succeeded) return;
            if (like) likedTrackIds.remove(id); else likedTrackIds.add(id);
            if (binding == null) return;
            renderLike();
            Toast.makeText(requireContext(), R.string.now_playing_like_failed, Toast.LENGTH_SHORT).show();
        };
        if (like) library.addFavoriteTrack(id, callback); else library.removeFavoriteTrack(id, callback);
    }

    @Nullable
    private MediaItem currentItem() {
        MediaController controller = playback.getController();
        return controller == null ? null : controller.getCurrentMediaItem();
    }

    private static boolean hasDuration(MediaController controller) {
        return controller.getDuration() > 0 && controller.getDuration() != C.TIME_UNSET;
    }

    @Override
    public void onDestroyView() {
        library.cancelAll();
        handler.removeCallbacks(ticker);
        binding = null;
        super.onDestroyView();
    }
}
