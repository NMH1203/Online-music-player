package vn.edu.usth.online_music_player;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import vn.edu.usth.online_music_player.data.session.SessionManager;
import vn.edu.usth.online_music_player.ui.profile.ProfileViewModel;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels;
import vn.edu.usth.online_music_player.data.playback.PlaybackManager;
import vn.edu.usth.online_music_player.data.playback.PlaybackRepository;
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
    private ImageButton playbackToggle;
    private LinearProgressIndicator playbackProgress;
    private ProfileViewModel accountModel;
    private NavController navController;
    private boolean onAuthScreen;
    private boolean hideChrome;
    private final PlaybackRepository playbackRepository = new PlaybackRepository();
    private androidx.appcompat.app.AlertDialog sessionExpiredDialog;

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
        playbackManager.addListener(this);
        setupMiniPlayer();
        accountModel = new androidx.lifecycle.ViewModelProvider(this).get(ProfileViewModel.class);
        accountModel.state().observe(this, state -> {
            routeForSession(state);
            if (!state.signedIn || state.profile == null || state.entitlement == null || state.busy) return;
            String user = state.profile.id;
            if (!state.entitlement.adFree && !user.equals(accountModel.appAdShownFor)) {
                MediaController controller = playbackManager.getController();
                if (controller != null && controller.getCurrentMediaItem() != null) return;
                accountModel.appAdShownFor = user;
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_crown)
                        .setTitle(R.string.ad_open_title)
                        .setMessage(R.string.ad_open_message)
                        .setPositiveButton(R.string.ad_open_continue, null).show();
            }
        });

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                onAuthScreen = isAuthDestination(destination.getId());
                // Auth and full-screen player pages have no bottom navigation or mini player.
                hideChrome = onAuthScreen || destination.getId() == R.id.now_playing_fragment
                        || destination.getId() == R.id.queue_fragment;
                binding.bottomNav.setVisibility(hideChrome ? View.GONE : View.VISIBLE);
                updatePlaybackBar();
            });
        }
        SessionManager.get().expired().observe(this, expired -> {
            if (Boolean.TRUE.equals(expired)) showSessionExpired();
        });
    }

    private static boolean isAuthDestination(int id) {
        return id == R.id.sign_in_fragment || id == R.id.create_account_fragment;
    }

    /** Sends signed-out users to Sign in and signed-in users away from the auth screens. */
    private void routeForSession(ProfileViewModel.State state) {
        if (navController == null || state.busy || sessionExpiredDialog != null) return;
        NavDestination current = navController.getCurrentDestination();
        boolean onAuth = current != null && isAuthDestination(current.getId());
        NavOptions clearStack = new NavOptions.Builder()
                .setPopUpTo(navController.getGraph().getId(), true).build();
        if (!state.signedIn && !onAuth) {
            playbackManager.stop();
            navController.navigate(R.id.sign_in_fragment, null, clearStack);
        } else if (state.signedIn && onAuth) {
            navController.navigate(R.id.navigation_home, null, clearStack);
        }
    }

    private void showSessionExpired() {
        if (sessionExpiredDialog != null) return;
        sessionExpiredDialog = new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_shield_check)
                .setTitle(R.string.session_expired_title)
                .setMessage(R.string.session_expired_message)
                .setCancelable(false)
                .setPositiveButton(R.string.session_expired_action, (dialog, which) -> {
                    sessionExpiredDialog = null;
                    SessionManager.get().acknowledgeExpired();
                    accountModel.refresh();
                })
                .show();
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
        setMiniPlayerVisible(false);
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

    /** Loads playback metadata for catalog track ids, then plays them as a queue. */
    public void playTrackIds(List<String> trackIds, int startIndex) {
        playbackRepository.loadQueue(trackIds, new PlaybackRepository.QueueCallback() {
            @Override
            public void onLoaded(List<TrackPlayback> tracks) {
                playQueue(tracks, startIndex);
            }

            @Override
            public void onFailed(PlaybackRepository.FailureType failureType) {
                Toast.makeText(MainActivity.this, R.string.playback_start_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Plays the playable tracks of a catalog list, starting at the one the user tapped. */
    public void playCatalogTracks(List<CatalogModels.Track> tracks, CatalogModels.Track clicked) {
        List<String> ids = new java.util.ArrayList<>();
        int start = 0;
        for (CatalogModels.Track track : tracks) {
            if (!track.playable || track.id == null) continue;
            if (track == clicked) start = ids.size();
            ids.add(track.id);
        }
        if (!ids.isEmpty()) playTrackIds(ids, start);
    }

    private void setupMiniPlayer() {
        miniPlayer = findViewById(R.id.mini_player);
        playbackTitle = miniPlayer.findViewById(R.id.playback_title);
        playbackArtist = miniPlayer.findViewById(R.id.playback_artist);
        playbackToggle = miniPlayer.findViewById(R.id.playback_toggle);
        playbackProgress = miniPlayer.findViewById(R.id.playback_progress);
        // Keeps the 2dp progress line inside the card's rounded corners.
        miniPlayer.findViewById(R.id.playback_card).setClipToOutline(true);
        playbackToggle.setOnClickListener(view -> playbackManager.togglePlayPause());
        miniPlayer.findViewById(R.id.playback_card).setOnClickListener(view -> {
            if (navController != null) navController.navigate(R.id.now_playing_fragment);
        });
    }

    private void updatePlaybackBar() {
        if (playbackManager == null || miniPlayer == null) {
            return;
        }
        MediaController controller = playbackManager.getController();
        MediaItem item = controller == null ? null : controller.getCurrentMediaItem();
        if (item == null) {
            setMiniPlayerVisible(false);
            mainHandler.removeCallbacks(progressUpdater);
            return;
        }

        MediaMetadata metadata = item.mediaMetadata;
        playbackTitle.setText(metadata.title == null
                ? getString(R.string.now_playing_unknown_title) : metadata.title);
        playbackArtist.setText(metadata.artist == null ? "" : metadata.artist);
        vn.edu.usth.online_music_player.ui.common.Artwork.load(
                miniPlayer.findViewById(R.id.playback_artwork), metadata.artworkUri);
        boolean playing = controller.isPlaying();
        playbackToggle.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        playbackToggle.setContentDescription(
                getString(playing ? R.string.action_pause : R.string.action_play));
        setMiniPlayerVisible(true);
        long duration = controller.getDuration();
        long position = controller.getCurrentPosition();
        playbackProgress.setProgress(duration > 0 && duration != C.TIME_UNSET
                ? (int) Math.min(playbackProgress.getMax(), position * playbackProgress.getMax() / duration)
                : 0);

        mainHandler.removeCallbacks(progressUpdater);
        if (controller.isPlaying()) {
            mainHandler.postDelayed(progressUpdater, 500);
        }
    }

    private void setMiniPlayerVisible(boolean visible) {
        if (miniPlayer == null || binding == null) {
            return;
        }
        visible = visible && !hideChrome;
        int bottomAnchor = visible ? R.id.mini_player : R.id.bottom_nav;
        ConstraintLayout.LayoutParams navHostParams =
                (ConstraintLayout.LayoutParams) binding.navHostFragment.getLayoutParams();
        if (navHostParams.bottomToTop != bottomAnchor) {
            navHostParams.bottomToTop = bottomAnchor;
            binding.navHostFragment.setLayoutParams(navHostParams);
        }
        miniPlayer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
