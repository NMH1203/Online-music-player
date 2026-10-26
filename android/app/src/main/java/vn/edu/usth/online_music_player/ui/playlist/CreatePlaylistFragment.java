package vn.edu.usth.online_music_player.ui.playlist;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;
import vn.edu.usth.online_music_player.data.playlist.PlaylistQuota;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.databinding.FragmentCreatePlaylistBinding;

/** Figma "Create playlist – name form"; on success opens the new playlist ("just created" state). */
public class CreatePlaylistFragment extends Fragment {
    static final int MAX_NAME = 50;
    private final PlaylistRepository repository = new PlaylistRepository();
    private FragmentCreatePlaylistBinding binding;
    private boolean busy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentCreatePlaylistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.createPlaylistTopBar.topBarTitle.setText(R.string.create_playlist_title);
        binding.createPlaylistTopBar.topBarBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.createPlaylistSubmit.setOnClickListener(ignored -> submit());
        binding.createPlaylistName.setOnEditorActionListener((field, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
        binding.createPlaylistName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable text) {
                binding.createPlaylistNameLayout.setError(null);
                renderHelper(text.length());
            }
        });
        renderHelper(0);
        repository.getPlaylistQuota(new PlaylistRepository.ResultCallback<PlaylistQuota>() {
            @Override
            public void onSuccess(PlaylistQuota quota) {
                if (binding == null || quota == null || quota.getMaxPlaylists() == null) return;
                binding.createPlaylistQuota.setVisibility(View.VISIBLE);
                binding.createPlaylistQuota.setText(getString(R.string.create_playlist_quota,
                        (int) quota.getPlaylistCount(), quota.getMaxPlaylists()));
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {}
        });
    }

    private void renderHelper(int length) {
        binding.createPlaylistNameLayout.setHelperText(getString(R.string.create_playlist_helper, length));
    }

    private void submit() {
        if (busy) return;
        String name = binding.createPlaylistName.getText() == null ? ""
                : binding.createPlaylistName.getText().toString().trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            binding.createPlaylistNameLayout.setError(getString(R.string.playlist_name_required));
            return;
        }
        setBusy(true);
        repository.createPlaylist(name, "", new PlaylistRepository.ResultCallback<PlaylistSummary>() {
            @Override
            public void onSuccess(PlaylistSummary created) {
                if (binding == null) return;
                setBusy(false);
                if (created == null || created.getId() == null) {
                    binding.createPlaylistNameLayout.setError(getString(R.string.playlist_generic_error));
                    return;
                }
                Bundle args = new Bundle();
                args.putString("playlistId", created.getId());
                args.putBoolean("justCreated", true);
                // Replace this form so Back from the new playlist returns to the library.
                Navigation.findNavController(binding.getRoot()).navigate(R.id.playlist_detail_fragment, args,
                        new NavOptions.Builder().setPopUpTo(R.id.create_playlist_fragment, true).build());
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding == null) return;
                setBusy(false);
                binding.createPlaylistNameLayout.setError(getString(
                        failureType == PlaylistRepository.FailureType.PLAYLIST_LIMIT_REACHED
                                ? R.string.add_to_playlist_limit
                                : failureType == PlaylistRepository.FailureType.NETWORK
                                        ? R.string.auth_error_network : R.string.playlist_generic_error));
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.createPlaylistSubmit.setEnabled(!value);
        binding.createPlaylistSubmit.setText(value ? "" : getString(R.string.create_playlist_title));
        binding.createPlaylistProgress.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
