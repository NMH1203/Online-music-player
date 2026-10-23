package vn.edu.usth.online_music_player.ui.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.playlist.CatalogTrack;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.databinding.FragmentPlaylistTrackPickerBinding;
import vn.edu.usth.online_music_player.databinding.ItemTrackChoiceBinding;

public class PlaylistTrackPickerFragment extends Fragment {
    private FragmentPlaylistTrackPickerBinding binding;
    private final PlaylistRepository repository = new PlaylistRepository();
    private final Set<String> selectedTrackIds = new LinkedHashSet<>();
    private String playlistId;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaylistTrackPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        playlistId = args == null ? null : args.getString("playlistId");
        binding.pickerBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.pickerSearch.setOnClickListener(ignored -> search());
        binding.pickerQuery.setOnEditorActionListener((text, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        binding.pickerAddSelected.setOnClickListener(ignored -> addSelectedTracks());
        updateSelectionStatus();
        loadTracks("");
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void search() {
        if (binding == null) {
            return;
        }
        Object service = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager) {
            ((InputMethodManager) service).hideSoftInputFromWindow(
                    binding.pickerQuery.getWindowToken(), 0);
        }
        loadTracks(binding.pickerQuery.getText() == null
                ? "" : binding.pickerQuery.getText().toString());
    }

    private void loadTracks(String query) {
        if (binding == null) {
            return;
        }
        binding.pickerProgress.setVisibility(View.VISIBLE);
        binding.pickerTracks.removeAllViews();
        binding.pickerStatus.setText(R.string.playlist_loading);
        repository.getPublishedTracks(query, new PlaylistRepository.ResultCallback<List<CatalogTrack>>() {
            @Override
            public void onSuccess(List<CatalogTrack> tracks) {
                if (binding == null) {
                    return;
                }
                binding.pickerProgress.setVisibility(View.GONE);
                renderTracks(tracks == null ? new ArrayList<>() : tracks);
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding == null) {
                    return;
                }
                binding.pickerProgress.setVisibility(View.GONE);
                binding.pickerStatus.setText(messageFor(failureType));
                binding.pickerTracks.removeAllViews();
            }
        });
    }

    private void renderTracks(List<CatalogTrack> tracks) {
        if (binding == null) {
            return;
        }
        binding.pickerTracks.removeAllViews();
        if (tracks.isEmpty()) {
            binding.pickerStatus.setText(R.string.playlist_no_tracks);
            updateSelectionStatus();
            return;
        }

        binding.pickerStatus.setText(getString(R.string.playlist_selected_count, selectedTrackIds.size()));
        for (CatalogTrack track : tracks) {
            ItemTrackChoiceBinding row = ItemTrackChoiceBinding.inflate(
                    LayoutInflater.from(requireContext()), binding.pickerTracks, false);
            row.trackChoiceCheckbox.setText(trackLabel(track));
            row.trackChoiceCheckbox.setChecked(track.getId() != null
                    && selectedTrackIds.contains(track.getId()));
            row.trackChoiceCheckbox.setOnCheckedChangeListener((button, checked) -> {
                if (track.getId() != null) {
                    if (checked) {
                        selectedTrackIds.add(track.getId());
                    } else {
                        selectedTrackIds.remove(track.getId());
                    }
                }
                updateSelectionStatus();
            });
            row.getRoot().setOnClickListener(ignored ->
                    row.trackChoiceCheckbox.setChecked(!row.trackChoiceCheckbox.isChecked()));
            binding.pickerTracks.addView(row.getRoot());
        }
        updateSelectionStatus();
    }

    private String trackLabel(CatalogTrack track) {
        String title = hasText(track.getTitle()) ? track.getTitle() : "Bài hát không tên";
        String artist = hasText(track.getArtistName()) ? track.getArtistName() : "Nghệ sĩ chưa rõ";
        int seconds = Math.max(0, track.getDurationSeconds());
        String subtitle = artist + " · " + String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
        if (hasText(track.getAlbumTitle())) {
            subtitle += " · " + track.getAlbumTitle();
        }
        return title + "\n" + subtitle;
    }

    private void updateSelectionStatus() {
        if (binding == null) {
            return;
        }
        binding.pickerAddSelected.setEnabled(!selectedTrackIds.isEmpty());
        binding.pickerStatus.setText(getString(R.string.playlist_selected_count, selectedTrackIds.size()));
    }

    private void addSelectedTracks() {
        if (binding == null || !hasText(playlistId) || selectedTrackIds.isEmpty()) {
            return;
        }
        List<String> selected = new ArrayList<>(selectedTrackIds);
        binding.pickerAddSelected.setEnabled(false);
        binding.pickerAddSelected.setText(R.string.playlist_tracks_adding);
        repository.addTracks(playlistId, selected, new PlaylistRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                if (binding == null || !isAdded()) {
                    return;
                }
                Snackbar.make(binding.getRoot(), R.string.playlist_tracks_added, Snackbar.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).navigateUp();
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding == null) {
                    return;
                }
                binding.pickerAddSelected.setEnabled(true);
                binding.pickerAddSelected.setText(R.string.playlist_add_selected);
                Snackbar.make(binding.getRoot(), messageFor(failureType), Snackbar.LENGTH_LONG).show();
            }
        });
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

    private boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }
}
