package vn.edu.usth.online_music_player.ui.playlist;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;
import vn.edu.usth.online_music_player.data.playlist.PlaylistDetail;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.data.playlist.PlaylistTrack;
import vn.edu.usth.online_music_player.databinding.FragmentPlaylistDetailBinding;
import vn.edu.usth.online_music_player.databinding.ItemSongRowBinding;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;
import vn.edu.usth.online_music_player.ui.profile.ProfileViewModel;

/** Figma "Playlist detail": header, Play / Add songs, songs with remove, rename/delete in the menu. */
public class PlaylistDetailFragment extends Fragment {
    private final PlaylistRepository repository = new PlaylistRepository();
    private FragmentPlaylistDetailBinding binding;
    private String playlistId;
    @Nullable private PlaylistDetail playlist;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        playlistId = requireArguments().getString("playlistId");
        binding.playlistBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.playlistAddTracks.setOnClickListener(ignored -> openTrackPicker());
        binding.playlistRetry.setOnClickListener(ignored -> loadPlaylist());
        binding.playlistPlay.setOnClickListener(ignored -> play(0));
        binding.playlistMore.setOnClickListener(anchor -> {
            PopupMenu menu = new PopupMenu(requireContext(), anchor);
            menu.getMenu().add(0, 1, 0, R.string.playlist_edit);
            menu.getMenu().add(0, 2, 1, R.string.playlist_delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) showEditDialog(); else confirmDelete();
                return true;
            });
            menu.show();
        });
        binding.playlistEmpty.stateIcon.setVisibility(View.GONE);
        binding.playlistEmpty.stateTitle.setText(R.string.playlist_first_track_title);
        binding.playlistEmpty.stateMessage.setText(R.string.playlist_first_track_message);
        binding.playlistEmpty.stateAction.setVisibility(View.VISIBLE);
        binding.playlistEmpty.stateAction.setText(R.string.add_songs_title);
        binding.playlistEmpty.stateAction.setOnClickListener(ignored -> openTrackPicker());
        binding.playlistCreatedBanner.infoBannerTitle.setText(R.string.playlist_created_title);
        new ViewModelProvider(requireActivity()).get(ProfileViewModel.class).state().observe(
                getViewLifecycleOwner(), state -> binding.playlistOwner.setText(state.profile == null ? ""
                        : getString(R.string.playlist_by, state.profile.displayName)));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPlaylist();
    }

    private void loadPlaylist() {
        if (binding == null || playlistId == null || playlistId.isEmpty()) return;
        if (playlist == null) {
            binding.playlistDetailContent.setVisibility(View.GONE);
            binding.playlistDetailStatus.setVisibility(View.VISIBLE);
            binding.playlistProgress.setVisibility(View.VISIBLE);
            binding.playlistStatusMessage.setText(R.string.playlist_loading);
            binding.playlistRetry.setVisibility(View.GONE);
        }
        repository.getPlaylist(playlistId, new PlaylistRepository.ResultCallback<PlaylistDetail>() {
            @Override
            public void onSuccess(PlaylistDetail result) {
                if (binding == null) return;
                if (result == null) {
                    showLoadError(PlaylistRepository.FailureType.SERVER);
                    return;
                }
                playlist = result;
                render(result);
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                showLoadError(failureType);
            }
        });
    }

    private void render(PlaylistDetail result) {
        binding.playlistDetailStatus.setVisibility(View.GONE);
        binding.playlistDetailContent.setVisibility(View.VISIBLE);
        binding.playlistName.setText(result.getName());
        String description = result.getDescription();
        boolean hasDescription = description != null && !description.trim().isEmpty();
        binding.playlistDescription.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
        binding.playlistDescription.setText(hasDescription ? description : "");

        List<PlaylistTrack> tracks = result.getTracks();
        int seconds = 0;
        for (PlaylistTrack track : tracks) seconds += Math.max(0, track.getDurationSeconds());
        String count = vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.queue_song_count, tracks.size(), tracks.size());
        binding.playlistTrackCount.setText(getString(R.string.playlist_meta, count, Math.round(seconds / 60f)));

        boolean empty = tracks.isEmpty();
        boolean justCreated = requireArguments().getBoolean("justCreated", false);
        binding.playlistCreatedBanner.getRoot().setVisibility(empty && justCreated ? View.VISIBLE : View.GONE);
        binding.playlistCreatedBanner.infoBannerMessage.setText(
                getString(R.string.playlist_created_message, result.getName()));
        binding.playlistPlay.setEnabled(!empty);
        binding.playlistAddTracks.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.playlistEmpty.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.playlistSongsHeader.getRoot().setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.playlistSongsHeader.sectionTitle.setText(R.string.album_songs);
        binding.playlistSongsHeader.sectionSeeAll.setText(count);
        binding.playlistSongsHeader.sectionSeeAll.setClickable(false);

        binding.playlistTracks.removeAllViews();
        for (int index = 0; index < tracks.size(); index++) {
            PlaylistTrack track = tracks.get(index);
            int start = index;
            ItemSongRowBinding row = CatalogViews.songRow(binding.playlistTracks, track.getTitle(),
                    CatalogViews.trackSubtitle(track.getArtistName(), track.getDurationSeconds()),
                    null, R.drawable.ic_more, () -> play(start));
            row.songAction.setClickable(true);
            row.songAction.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.songAction.setContentDescription(getString(R.string.playlist_song_more, track.getTitle()));
            row.songAction.setOnClickListener(anchor -> {
                PopupMenu menu = new PopupMenu(requireContext(), anchor);
                menu.getMenu().add(R.string.playlist_remove_track);
                menu.setOnMenuItemClickListener(item -> {
                    removeTrack(track);
                    return true;
                });
                menu.show();
            });
        }
    }

    private void play(int startIndex) {
        if (playlist == null || playlist.getTracks().isEmpty()) return;
        List<String> ids = new ArrayList<>();
        for (PlaylistTrack track : playlist.getTracks()) ids.add(track.getId());
        ((MainActivity) requireActivity()).playTrackIds(ids, startIndex);
    }

    private void openTrackPicker() {
        if (playlist == null) return;
        Bundle args = new Bundle();
        args.putString("playlistId", playlistId);
        args.putString("playlistName", playlist.getName());
        Navigation.findNavController(requireView()).navigate(R.id.playlist_track_picker_fragment, args);
    }

    private void removeTrack(PlaylistTrack track) {
        repository.removeTrack(playlistId, track.getId(), new PlaylistRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                if (binding == null) return;
                showMessage(getString(R.string.playlist_track_removed));
                loadPlaylist();
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                showMessage(messageFor(failureType));
            }
        });
    }

    private void showEditDialog() {
        if (playlist == null) return;
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = CatalogViews.dp(binding.getRoot(), 24);
        form.setPadding(pad, CatalogViews.dp(binding.getRoot(), 8), pad, 0);
        EditText nameInput = new EditText(requireContext());
        nameInput.setSingleLine(true);
        nameInput.setText(playlist.getName());
        nameInput.setHint(R.string.playlist_name_hint);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(CreatePlaylistFragment.MAX_NAME)});
        form.addView(nameInput);
        EditText descriptionInput = new EditText(requireContext());
        descriptionInput.setText(playlist.getDescription());
        descriptionInput.setHint(R.string.playlist_description_hint);
        descriptionInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        descriptionInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2000)});
        form.addView(descriptionInput);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_edit)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.playlist_edit, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameInput.setError(getString(R.string.playlist_name_required));
                        return;
                    }
                    button.setEnabled(false);
                    repository.updatePlaylist(playlistId, name, descriptionInput.getText().toString().trim(),
                            new PlaylistRepository.ResultCallback<PlaylistSummary>() {
                                @Override
                                public void onSuccess(PlaylistSummary updated) {
                                    dialog.dismiss();
                                    if (binding == null) return;
                                    showMessage(getString(R.string.playlist_saved));
                                    loadPlaylist();
                                }

                                @Override
                                public void onFailure(PlaylistRepository.FailureType failureType) {
                                    button.setEnabled(true);
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
                .setPositiveButton(R.string.playlist_delete, (dialog, which) ->
                        repository.deletePlaylist(playlistId, new PlaylistRepository.ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void ignored) {
                                if (!isAdded() || getView() == null) return;
                                android.widget.Toast.makeText(requireContext(), R.string.playlist_deleted,
                                        android.widget.Toast.LENGTH_SHORT).show();
                                Navigation.findNavController(requireView()).navigateUp();
                            }

                            @Override
                            public void onFailure(PlaylistRepository.FailureType failureType) {
                                showMessage(messageFor(failureType));
                            }
                        }))
                .show();
    }

    private void showLoadError(PlaylistRepository.FailureType failureType) {
        if (binding == null) return;
        binding.playlistDetailContent.setVisibility(View.GONE);
        binding.playlistDetailStatus.setVisibility(View.VISIBLE);
        binding.playlistProgress.setVisibility(View.GONE);
        binding.playlistStatusMessage.setText(messageFor(failureType));
        binding.playlistRetry.setVisibility(View.VISIBLE);
    }

    private String messageFor(PlaylistRepository.FailureType failureType) {
        switch (failureType) {
            case AUTHENTICATION_REQUIRED: return getString(R.string.playlist_auth_required);
            case PLAYLIST_LIMIT_REACHED: return getString(R.string.playlist_quota_message);
            case NETWORK: return getString(R.string.playlist_network_error);
            case NOT_FOUND: return getString(R.string.playlist_not_found);
            default: return getString(R.string.playlist_generic_error);
        }
    }

    private void showMessage(String message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
