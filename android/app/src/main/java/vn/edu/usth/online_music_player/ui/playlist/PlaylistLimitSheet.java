package vn.edu.usth.online_music_player.ui.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.playlist.PlaylistQuota;
import vn.edu.usth.online_music_player.databinding.SheetPlaylistLimitBinding;

/** Figma "Playlist limit – Free": explains the Free quota and points to Premium in Profile. */
public class PlaylistLimitSheet extends BottomSheetDialogFragment {
    private static final String ARG_USED = "used";
    private static final String ARG_MAX = "max";

    /** Opens Create playlist, or this sheet when the Free quota is already used up. */
    public static void startCreate(Fragment from, @Nullable PlaylistQuota quota) {
        if (quota != null && !quota.canCreatePlaylist() && quota.getMaxPlaylists() != null) {
            Bundle args = new Bundle();
            args.putInt(ARG_USED, (int) quota.getPlaylistCount());
            args.putInt(ARG_MAX, quota.getMaxPlaylists());
            PlaylistLimitSheet sheet = new PlaylistLimitSheet();
            sheet.setArguments(args);
            sheet.show(from.getChildFragmentManager(), "playlist_limit");
            return;
        }
        // The server re-checks the quota, so an unknown quota still lets the user try.
        Navigation.findNavController(from.requireView()).navigate(R.id.create_playlist_fragment);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        SheetPlaylistLimitBinding binding = SheetPlaylistLimitBinding.inflate(inflater, container, false);
        int used = requireArguments().getInt(ARG_USED);
        int max = requireArguments().getInt(ARG_MAX);
        binding.limitMessage.setText(getString(R.string.limit_message, max));
        binding.limitUsed.setText(getString(R.string.free_plan_used, used, max));
        binding.limitClose.setOnClickListener(ignored -> dismiss());
        binding.limitNotNow.setOnClickListener(ignored -> dismiss());
        binding.limitPremium.setOnClickListener(ignored -> {
            dismiss();
            Navigation.findNavController(requireParentFragment().requireView()).navigate(R.id.navigation_profile);
        });
        return binding.getRoot();
    }
}
