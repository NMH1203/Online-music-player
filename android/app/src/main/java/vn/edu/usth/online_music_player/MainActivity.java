package vn.edu.usth.online_music_player;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import java.util.List;
import vn.edu.usth.online_music_player.data.playback.PlaybackManager;
import vn.edu.usth.online_music_player.data.playback.TrackPlayback;
import vn.edu.usth.online_music_player.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements PlaybackManager.Listener {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updatePlaybackBar();
        }
    };
    private ActivityMainBinding binding;
    private PlaybackManager playbackManager;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private Runnable playbackAfterPermission;
    private View miniPlayer;
    private TextView playbackTitle;
    private TextView playbackArtist;
    private TextView playbackToggle;
    private SeekBar playbackSeek;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    Runnable action = playbackAfterPermission;
                    playbackAfterPermission = null;
                    if (action != null) {
                        action.run();
                    }
                });
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        playbackManager = new PlaybackManager(this);
        playbackManager.setListener(this);
        setupMiniPlayer();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        playbackManager.connect();
    }

    @Override
    protected void onStop() {
        mainHandler.removeCallbacks(progressUpdater);
        playbackManager.disconnect();
        super.onStop();
    }

    @Override
    public void onPlaybackChanged() {
        updatePlaybackBar();
    }

    @Override
    public void onConnectionFailed() {
        miniPlayer.setVisibility(View.GONE);
    }

    public PlaybackManager getPlaybackManager() {
        return playbackManager;
    }

    /** Requests notification permission at the moment playback is first started on Android 13+. */
    public void playQueue(List<TrackPlayback> tracks, int startIndex) {
        Runnable action = () -> playbackManager.playQueue(tracks, startIndex);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            playbackAfterPermission = action;
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            action.run();
        }
    }

    private void setupMiniPlayer() {
        miniPlayer = findViewById(R.id.mini_player);
        playbackTitle = miniPlayer.findViewById(R.id.playback_title);
        playbackArtist = miniPlayer.findViewById(R.id.playback_artist);
        playbackToggle = miniPlayer.findViewById(R.id.playback_toggle);
        playbackSeek = miniPlayer.findViewById(R.id.playback_seek);
        miniPlayer.findViewById(R.id.playback_previous).setOnClickListener(
                view -> playbackManager.skipPrevious());
        playbackToggle.setOnClickListener(view -> playbackManager.togglePlayPause());
        miniPlayer.findViewById(R.id.playback_next).setOnClickListener(
                view -> playbackManager.skipNext());
        playbackSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                MediaController controller = playbackManager.getController();
                if (controller == null || controller.getDuration() <= 0
                        || controller.getDuration() == C.TIME_UNSET) {
                    return;
                }
                long target = controller.getDuration() * seekBar.getProgress() / seekBar.getMax();
                playbackManager.seekTo(target);
            }
        });
    }

    private void updatePlaybackBar() {
        if (playbackManager == null || miniPlayer == null) {
            return;
        }
        MediaController controller = playbackManager.getController();
        MediaItem item = controller == null ? null : controller.getCurrentMediaItem();
        if (item == null) {
            miniPlayer.setVisibility(View.GONE);
            mainHandler.removeCallbacks(progressUpdater);
            return;
        }

        MediaMetadata metadata = item.mediaMetadata;
        playbackTitle.setText(metadata.title == null ? "Đang phát" : metadata.title);
        playbackArtist.setText(metadata.artist == null ? "" : metadata.artist);
        playbackToggle.setText(controller.isPlaying() ? "Ⅱ" : "▶");
        playbackToggle.setContentDescription(controller.isPlaying() ? "Tạm dừng" : "Phát");
        miniPlayer.setVisibility(View.VISIBLE);
        long duration = controller.getDuration();
        long position = controller.getCurrentPosition();
        if (duration > 0 && duration != C.TIME_UNSET) {
            playbackSeek.setEnabled(true);
            playbackSeek.setProgress((int) Math.min(
                    playbackSeek.getMax(), position * playbackSeek.getMax() / duration));
        } else {
            playbackSeek.setEnabled(false);
            playbackSeek.setProgress(0);
        }

        mainHandler.removeCallbacks(progressUpdater);
        if (controller.isPlaying()) {
            mainHandler.postDelayed(progressUpdater, 500);
        }
    }
}
