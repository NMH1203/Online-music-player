package vn.edu.usth.online_music_player.ui.playlist;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.playlist.CatalogTrack;
import vn.edu.usth.online_music_player.data.playlist.PlaylistDetail;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.data.playlist.PlaylistTrack;
import vn.edu.usth.online_music_player.databinding.FragmentPlaylistTrackPickerBinding;
import vn.edu.usth.online_music_player.databinding.ItemSongRowBinding;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;

/** Figma "Add songs": search the catalog and add a song with one tap; Done returns to the playlist. */
public class PlaylistTrackPickerFragment extends Fragment {
    private final PlaylistRepository repository = new PlaylistRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> inPlaylist = new HashSet<>();
    private FragmentPlaylistTrackPickerBinding binding;
    private String playlistId;
    @Nullable private Runnable pendingSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaylistTrackPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        playlistId = requireArguments().getString("playlistId");
        String name = requireArguments().getString("playlistName", "");
        binding.pickerTopBar.topBarTitle.setText(R.string.add_songs_title);
        binding.pickerTopBar.topBarBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.pickerDone.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.pickerSubtitle.setText(getString(R.string.add_songs_to, name));
        binding.pickerBanner.getRoot().setVisibility(View.GONE);
        binding.pickerBanner.infoBannerMessage.setText(R.string.add_songs_banner_message);
        binding.pickerQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable text) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                String query = text.toString().trim();
                pendingSearch = () -> loadTracks(query);
                handler.postDelayed(pendingSearch, 400);
            }
        });
        loadExistingThenTracks();
    }

    /** Marks songs already in the playlist with a check before showing the catalog. */
    private void loadExistingThenTracks() {
        repository.getPlaylist(playlistId, new PlaylistRepository.ResultCallback<PlaylistDetail>() {
            @Override
            public void onSuccess(PlaylistDetail result) {
                if (result != null) for (PlaylistTrack track : result.getTracks()) inPlaylist.add(track.getId());
                loadTracks("");
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                loadTracks("");
            }
        });
    }

    private void loadTracks(String query) {
        if (binding == null) return;
        binding.pickerSection.setText(query.isEmpty() ? R.string.add_songs_suggested : R.string.add_songs_results);
        binding.pickerProgress.setVisibility(View.VISIBLE);
        binding.pickerStatus.setVisibility(View.GONE);
        repository.getPublishedTracks(query, new PlaylistRepository.ResultCallback<List<CatalogTrack>>() {
            @Override
            public void onSuccess(List<CatalogTrack> tracks) {
                if (binding == null) return;
                binding.pickerProgress.setVisibility(View.GONE);
                render(tracks == null ? Collections.emptyList() : tracks, query);
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding == null) return;
                binding.pickerProgress.setVisibility(View.GONE);
                binding.pickerTracks.removeAllViews();
                status(getString(failureType == PlaylistRepository.FailureType.NETWORK
                        ? R.string.playlist_network_error : R.string.playlist_generic_error));
            }
        });
    }

    private void render(List<CatalogTrack> tracks, String query) {
        binding.pickerTracks.removeAllViews();
        if (tracks.isEmpty()) {
            status(getString(R.string.search_no_results_message, query));
            return;
        }
        for (CatalogTrack track : tracks) {
            boolean added = inPlaylist.contains(track.getId());
            ItemSongRowBinding row = CatalogViews.songRow(binding.pickerTracks, track.getTitle(),
                    added ? getString(R.string.add_songs_added)
                            : CatalogViews.trackSubtitle(track.getArtistName(), track.getDurationSeconds()),
                    null, added ? R.drawable.ic_check : R.drawable.ic_plus, null);
            row.getRoot().setEnabled(!added);
            if (added) {
                markAdded(row);
            } else {
                row.getRoot().setContentDescription(getString(R.string.add_songs_add, track.getTitle()));
                row.getRoot().setOnClickListener(ignored -> add(track, row));
            }
        }
    }

    private void add(CatalogTrack track, ItemSongRowBinding row) {
        row.getRoot().setEnabled(false);
        repository.addTracks(playlistId, Collections.singletonList(track.getId()),
                new PlaylistRepository.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (binding == null) return;
                        inPlaylist.add(track.getId());
                        row.songAction.setImageResource(R.drawable.ic_check);
                        row.songSubtitle.setText(R.string.add_songs_added);
                        markAdded(row);
                        binding.pickerBanner.getRoot().setVisibility(View.VISIBLE);
                        binding.pickerBanner.infoBannerTitle.setText(
                                getString(R.string.add_songs_banner_title, track.getTitle()));
                    }

                    @Override
                    public void onFailure(PlaylistRepository.FailureType failureType) {
                        if (binding == null) return;
                        row.getRoot().setEnabled(true);
                        status(getString(failureType == PlaylistRepository.FailureType.NETWORK
                                ? R.string.playlist_network_error : R.string.add_to_playlist_failed));
                    }
                });
    }

    private void markAdded(ItemSongRowBinding row) {
        ImageViewCompat.setImageTintList(row.songAction,
                ContextCompat.getColorStateList(requireContext(), R.color.accent_primary));
    }

    private void status(String message) {
        binding.pickerStatus.setText(message);
        binding.pickerStatus.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        binding = null;
        super.onDestroyView();
    }
}
