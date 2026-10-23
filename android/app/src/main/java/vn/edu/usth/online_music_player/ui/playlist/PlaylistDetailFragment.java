package vn.edu.usth.online_music_player.ui.playlist;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.Locale;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.playlist.PlaylistDetail;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.data.playlist.PlaylistTrack;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;
import vn.edu.usth.online_music_player.databinding.FragmentPlaylistDetailBinding;
import vn.edu.usth.online_music_player.databinding.ItemPlaylistTrackBinding;

public class PlaylistDetailFragment extends Fragment {
    private FragmentPlaylistDetailBinding binding;
    private final PlaylistRepository repository = new PlaylistRepository();
    private String playlistId;
    private PlaylistDetail playlist;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        playlistId = args == null ? null : args.getString("playlistId");
        binding.playlistBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.playlistAddTracks.setOnClickListener(ignored -> openTrackPicker());
        binding.playlistEdit.setOnClickListener(ignored -> showEditDialog());
        binding.playlistDelete.setOnClickListener(ignored -> confirmDelete());
        binding.playlistRetry.setOnClickListener(ignored -> loadPlaylist());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            loadPlaylist();
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void loadPlaylist() {
        if (binding == null || !hasText(playlistId)) {
            return;
        }
        binding.playlistDetailContent.setVisibility(View.GONE);
        binding.playlistDetailStatus.setVisibility(View.VISIBLE);
        binding.playlistProgress.setVisibility(View.VISIBLE);
        binding.playlistStatusMessage.setText(R.string.playlist_loading);
        binding.playlistRetry.setVisibility(View.GONE);
        repository.getPlaylist(playlistId, new PlaylistRepository.ResultCallback<PlaylistDetail>() {
            @Override
            public void onSuccess(PlaylistDetail result) {
                if (binding == null) {
                    return;
                }
                if (result == null) {
                    showLoadError(PlaylistRepository.FailureType.SERVER);
                    return;
                }
                playlist = result;
                renderPlaylist(result);
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                showLoadError(failureType);
            }
        });
    }

    private void renderPlaylist(PlaylistDetail result) {
        if (binding == null) {
            return;
        }
        binding.playlistDetailStatus.setVisibility(View.GONE);
        binding.playlistDetailContent.setVisibility(View.VISIBLE);
        binding.playlistName.setText(result.getName());
        String description = result.getDescription();
        binding.playlistDescription.setText(hasText(description) ? description : "");
        binding.playlistTrackCount.setText(
                getString(R.string.playlist_count, result.getTracks().size()));
        binding.playlistTracks.removeAllViews();
        if (result.getTracks().isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.playlist_empty_title);
            empty.setTextColor(requireContext().getColor(R.color.text_secondary));
            empty.setTextSize(15);
            empty.setPadding(dp(4), dp(18), dp(4), dp(8));
            binding.playlistTracks.addView(empty);
            TextView hint = new TextView(requireContext());
            hint.setText(R.string.playlist_empty_message);
            hint.setTextColor(requireContext().getColor(R.color.text_muted));
            hint.setTextSize(13);
            hint.setPadding(dp(4), 0, dp(4), dp(12));
            binding.playlistTracks.addView(hint);
            return;
        }

        for (int index = 0; index < result.getTracks().size(); index++) {
            PlaylistTrack track = result.getTracks().get(index);
            ItemPlaylistTrackBinding row = ItemPlaylistTrackBinding.inflate(
                    LayoutInflater.from(requireContext()), binding.playlistTracks, false);
            row.playlistTrackNumber.setText(String.format(Locale.getDefault(), "%02d", index + 1));
            row.playlistTrackTitle.setText(track.getTitle());
            row.playlistTrackArtist.setText(trackSubtitle(track));
            row.playlistTrackRemove.setOnClickListener(ignored -> removeTrack(track));
            row.getRoot().setOnClickListener(ignored -> showMessage(
                    getString(R.string.playlist_playback_not_ready)));
            binding.playlistTracks.addView(row.getRoot());
        }
    }

    private String trackSubtitle(PlaylistTrack track) {
        String artist = hasText(track.getArtistName()) ? track.getArtistName() : "Nghệ sĩ chưa rõ";
        int seconds = Math.max(0, track.getDurationSeconds());
        return artist + " · " + String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private void openTrackPicker() {
        if (binding == null || !hasText(playlistId)) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("playlistId", playlistId);
        Navigation.findNavController(requireView())
                .navigate(R.id.playlist_track_picker_fragment, args);
    }

    private void removeTrack(PlaylistTrack track) {
        if (track == null || !hasText(track.getId())) {
            return;
        }
        repository.removeTrack(playlistId, track.getId(), new PlaylistRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                if (binding != null) {
                    showMessage(getString(R.string.playlist_track_removed));
                    loadPlaylist();
                }
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding != null) {
                    showMessage(messageFor(failureType));
                }
            }
        });
    }

    private void showEditDialog() {
        if (playlist == null) {
            return;
        }
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(24);
        form.setPadding(horizontalPadding, dp(8), horizontalPadding, 0);

        EditText nameInput = new EditText(requireContext());
        nameInput.setSingleLine(true);
        nameInput.setText(playlist.getName());
        nameInput.setHint(R.string.playlist_name_hint);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        form.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText descriptionInput = new EditText(requireContext());
        descriptionInput.setText(playlist.getDescription());
        descriptionInput.setHint(R.string.playlist_description_hint);
        descriptionInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        descriptionInput.setMinLines(2);
        descriptionInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(8);
        form.addView(descriptionInput, descriptionParams);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_edit)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.playlist_edit, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError(getString(R.string.playlist_name_required));
                nameInput.requestFocus();
                return;
            }
            String description = descriptionInput.getText() == null
                    ? "" : descriptionInput.getText().toString().trim();
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            repository.updatePlaylist(playlistId, name, description,
                    new PlaylistRepository.ResultCallback<PlaylistSummary>() {
                        @Override
                        public void onSuccess(PlaylistSummary updated) {
                            if (binding == null) {
                                return;
                            }
                            dialog.dismiss();
                            showMessage(getString(R.string.playlist_saved));
                            loadPlaylist();
                        }

                        @Override
                        public void onFailure(PlaylistRepository.FailureType failureType) {
                            if (binding == null) {
                                return;
                            }
                            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true);
                            showMessage(messageFor(failureType));
                        }
                    });
        }));
        dialog.show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_delete)
                .setMessage(R.string.playlist_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.playlist_delete, (dialog, which) -> {
                    repository.deletePlaylist(playlistId, new PlaylistRepository.ResultCallback<Void>() {
                        @Override
                        public void onSuccess(Void ignored) {
                            if (!isAdded() || getView() == null) {
                                return;
                            }
                            Navigation.findNavController(requireView()).navigateUp();
                            android.widget.Toast.makeText(requireContext(),
                                    R.string.playlist_deleted, android.widget.Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(PlaylistRepository.FailureType failureType) {
                            showMessage(messageFor(failureType));
                        }
                    });
                })
                .show();
    }

    private void showLoadError(PlaylistRepository.FailureType failureType) {
        if (binding == null) {
            return;
        }
        binding.playlistDetailContent.setVisibility(View.GONE);
        binding.playlistDetailStatus.setVisibility(View.VISIBLE);
        binding.playlistProgress.setVisibility(View.GONE);
        binding.playlistStatusMessage.setText(messageFor(failureType));
        binding.playlistRetry.setVisibility(View.VISIBLE);
    }

    private String messageFor(PlaylistRepository.FailureType failureType) {
        if (failureType == PlaylistRepository.FailureType.AUTHENTICATION_REQUIRED) {
            return getString(R.string.playlist_auth_required);
        }
        if (failureType == PlaylistRepository.FailureType.NETWORK) {
            return getString(R.string.playlist_network_error);
        }
        if (failureType == PlaylistRepository.FailureType.NOT_FOUND) {
            return getString(R.string.playlist_not_found);
        }
        return getString(R.string.playlist_generic_error);
    }

    private void showMessage(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    private boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
