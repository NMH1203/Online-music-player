package vn.edu.usth.online_music_player.ui.player;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.playback.BoundaryAds;
import vn.edu.usth.online_music_player.data.playback.PlaybackManager;
import vn.edu.usth.online_music_player.databinding.FragmentQueueBinding;
import vn.edu.usth.online_music_player.databinding.ItemSongRowBinding;
import vn.edu.usth.online_music_player.ui.common.Artwork;

/** Figma "Queue": current track, up-next list with remove, and compact controls. */
public class QueueFragment extends Fragment implements PlaybackManager.Listener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::renderProgress;
    private FragmentQueueBinding binding;
    private PlaybackManager playback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentQueueBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        playback = ((MainActivity) requireActivity()).getPlaybackManager();
        binding.queueBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.queueClear.setOnClickListener(ignored -> playback.clearUpNext());
        binding.queuePrevious.setOnClickListener(ignored -> playback.skipPrevious());
        binding.queueToggle.setOnClickListener(ignored -> playback.togglePlayPause());
        binding.queueNext.setOnClickListener(ignored -> playback.skipNext());

        binding.queueEmpty.stateTitle.setText(R.string.queue_empty_title);
        binding.queueEmpty.stateMessage.setText(R.string.queue_empty_message);
        binding.queueEmpty.stateIcon.setImageResource(R.drawable.ic_queue);
        binding.queueEmpty.stateAction.setText(R.string.queue_empty_action);
        binding.queueEmpty.stateAction.setVisibility(View.VISIBLE);
        binding.queueEmpty.stateAction.setOnClickListener(ignored ->
                Navigation.findNavController(view).navigate(R.id.navigation_home, null,
                        new NavOptions.Builder().setPopUpTo(R.id.navigation_home, false).build()));

        ItemSongRowBinding current = binding.queueCurrent;
        current.getRoot().setBackgroundResource(R.drawable.bg_now_playing_row);
        current.getRoot().setPadding(dp(12), 0, dp(4), 0);
        current.songTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_primary));
        current.songAction.setImageResource(R.drawable.ic_audio_lines);
        current.songAction.setClickable(false);
        current.songAction.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageViewCompat.setImageTintList(current.songAction,
                ContextCompat.getColorStateList(requireContext(), R.color.accent_primary));
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
        MediaItem currentItem = controller == null ? null : controller.getCurrentMediaItem();
        if (controller == null || currentItem == null) {
            if (controller != null) Navigation.findNavController(binding.getRoot()).navigateUp();
            return;
        }
        bindRow(binding.queueCurrent, currentItem);

        binding.queueItems.removeAllViews();
        int count = controller.getMediaItemCount();
        int upNext = 0;
        for (int index = controller.getCurrentMediaItemIndex() + 1; index < count; index++) {
            MediaItem item = controller.getMediaItemAt(index);
            if (BoundaryAds.AD_ID.equals(item.mediaId)) continue;
            addUpNextRow(item, index);
            upNext++;
        }
        binding.queueCount.setText(vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), 
                R.plurals.queue_song_count, upNext, upNext));
        binding.queueUpNextHeader.setVisibility(upNext == 0 ? View.GONE : View.VISIBLE);
        binding.queueEmpty.getRoot().setVisibility(upNext == 0 ? View.VISIBLE : View.GONE);
        binding.queueClear.setVisibility(upNext == 0 ? View.INVISIBLE : View.VISIBLE);

        boolean playing = controller.isPlaying();
        binding.queueToggle.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.queueToggle.setContentDescription(
                getString(playing ? R.string.action_pause : R.string.action_play));
        binding.queueNext.setEnabled(controller.hasNextMediaItem());
        renderProgress();
    }

    @Override
    public void onConnectionFailed() {}

    private void addUpNextRow(MediaItem item, int index) {
        ItemSongRowBinding row = ItemSongRowBinding.inflate(getLayoutInflater(), binding.queueItems, false);
        bindRow(row, item);
        row.getRoot().setOnClickListener(ignored -> playback.playItemAt(index));
        row.songAction.setImageResource(R.drawable.ic_more);
        row.songAction.setContentDescription(getString(R.string.action_more));
        row.songAction.setOnClickListener(anchor -> {
            PopupMenu menu = new PopupMenu(requireContext(), anchor);
            menu.getMenu().add(R.string.action_remove_from_queue);
            menu.setOnMenuItemClickListener(ignored -> {
                playback.removeItemAt(index);
                return true;
            });
            menu.show();
        });
        binding.queueItems.addView(row.getRoot());
    }

    private void bindRow(ItemSongRowBinding row, MediaItem item) {
        MediaMetadata metadata = item.mediaMetadata;
        row.songTitle.setText(metadata.title);
        row.songSubtitle.setText(metadata.artist);
        Artwork.load(row.songArtwork, metadata.artworkUri);
    }

    private void renderProgress() {
        handler.removeCallbacks(ticker);
        if (binding == null) return;
        MediaController controller = playback.getController();
        if (controller == null) return;
        long duration = controller.getDuration() == C.TIME_UNSET ? 0 : Math.max(0, controller.getDuration());
        long position = controller.getCurrentPosition();
        binding.queuePosition.setText(Artwork.time(position));
        binding.queueDuration.setText(Artwork.time(duration));
        binding.queueProgress.setProgress(duration > 0 ? (int) Math.min(1000, position * 1000 / duration) : 0);
        if (controller.isPlaying()) handler.postDelayed(ticker, 500);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(ticker);
        binding = null;
        super.onDestroyView();
    }
}
