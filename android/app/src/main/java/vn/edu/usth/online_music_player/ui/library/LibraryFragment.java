package vn.edu.usth.online_music_player.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.library.FavoriteAlbum;
import vn.edu.usth.online_music_player.data.library.FavoriteTrack;
import vn.edu.usth.online_music_player.data.library.LibraryContent;
import vn.edu.usth.online_music_player.data.library.LibraryRepository;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;
import vn.edu.usth.online_music_player.data.playlist.PlaylistQuota;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.databinding.FragmentLibraryBinding;
import vn.edu.usth.online_music_player.databinding.ItemLibrarySummaryBinding;
import vn.edu.usth.online_music_player.databinding.ItemSongRowBinding;
import vn.edu.usth.online_music_player.databinding.ViewSectionHeaderBinding;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;
import vn.edu.usth.online_music_player.ui.playlist.PlaylistLimitSheet;

/** Figma "Your library": liked songs, liked albums and my playlists, or the empty state. */
public class LibraryFragment extends Fragment {
    private static final int PLAYLIST_PREVIEW = 3;
    private final PlaylistRepository playlistRepository = new PlaylistRepository();
    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;
    @Nullable private PlaylistQuota playlistQuota;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        binding.libraryRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent_primary));
        binding.libraryRefresh.setProgressBackgroundColorSchemeResource(R.color.surface_secondary);
        binding.libraryRefresh.setOnRefreshListener(viewModel::refresh);
        binding.libraryRetry.setOnClickListener(ignored -> viewModel.refresh());
        binding.createPlaylistButton.setOnClickListener(ignored -> PlaylistLimitSheet.startCreate(this, playlistQuota));
        summary(binding.summarySongs, R.drawable.ic_heart, R.string.lib_liked_songs);
        summary(binding.summaryAlbums, R.drawable.ic_album, R.string.lib_liked_albums);
        summary(binding.summaryPlaylists, R.drawable.ic_queue, R.string.lib_my_playlists);
        binding.libraryEmptyState.stateIcon.setImageResource(R.drawable.ic_headphones);
        binding.libraryEmptyState.stateTitle.setText(R.string.lib_empty_title);
        binding.libraryEmptyState.stateMessage.setText(R.string.lib_empty_message);
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh();
        loadPlaylistQuota();
    }

    private void render(LibraryUiState state) {
        if (binding == null) return;
        LibraryContent content = state.getContent();
        if (content != null) {
            binding.libraryStatusPanel.setVisibility(View.GONE);
            binding.librarySections.setVisibility(View.VISIBLE);
            binding.libraryRefresh.setRefreshing(state.getStatus() == LibraryUiState.Status.LOADING);
            renderContent(content);
            return;
        }
        binding.libraryRefresh.setRefreshing(false);
        binding.librarySections.setVisibility(View.GONE);
        binding.libraryStatusPanel.setVisibility(View.VISIBLE);
        boolean loading = state.getStatus() == LibraryUiState.Status.LOADING;
        boolean error = !loading && state.getStatus() != LibraryUiState.Status.AUTHENTICATION_REQUIRED;
        binding.libraryProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.libraryRetry.setVisibility(error ? View.VISIBLE : View.GONE);
        binding.libraryStatusTitle.setText(loading ? R.string.lib_loading
                : error ? R.string.lib_error_title : R.string.lib_auth_title);
        binding.libraryStatusMessage.setText(error ? getString(R.string.lib_error_message) : "");
    }

    private void renderContent(LibraryContent content) {
        List<FavoriteTrack> tracks = content.getFavoriteTracks();
        List<FavoriteAlbum> albums = content.getFavoriteAlbums();
        List<PlaylistSummary> playlists = content.getPlaylists();
        boolean empty = tracks.isEmpty() && albums.isEmpty() && playlists.isEmpty();

        binding.libraryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.libraryEmptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        // Figma: a filled primary button on the empty page, an outlined one under the lists.
        binding.createPlaylistButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(),
                empty ? R.color.text_primary : android.R.color.transparent));
        binding.createPlaylistButton.setTextColor(ContextCompat.getColor(requireContext(),
                empty ? R.color.background_primary : R.color.text_primary));
        binding.createPlaylistButton.setIconTint(ContextCompat.getColorStateList(requireContext(),
                empty ? R.color.background_primary : R.color.text_primary));
        if (empty) {
            binding.summarySongs.summaryCount.setText(vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.queue_song_count, 0, 0));
            binding.summaryAlbums.summaryCount.setText(vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.lib_album_count, 0, 0));
            binding.summaryPlaylists.summaryCount.setText(vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.lib_playlist_count, 0, 0));
        }

        binding.tracksSection.setVisibility(tracks.isEmpty() ? View.GONE : View.VISIBLE);
        header(binding.tracksHeader, R.string.lib_liked_songs,
                vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.queue_song_count, tracks.size(), tracks.size()), null);
        binding.tracksList.removeAllViews();
        List<String> trackIds = new ArrayList<>();
        for (FavoriteTrack track : tracks) trackIds.add(track.getId());
        for (int i = 0; i < tracks.size(); i++) {
            FavoriteTrack track = tracks.get(i);
            int start = i;
            ItemSongRowBinding row = CatalogViews.songRow(binding.tracksList, track.getTitle(),
                    CatalogViews.trackSubtitle(track.getArtistName(), track.getDurationSeconds()), null,
                    R.drawable.ic_heart, () -> ((MainActivity) requireActivity()).playTrackIds(trackIds, start));
            ImageViewCompat.setImageTintList(row.songAction,
                    ContextCompat.getColorStateList(requireContext(), R.color.accent_primary));
            row.songAction.setClickable(true);
            row.songAction.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.songAction.setContentDescription(getString(R.string.lib_unlike_song, track.getTitle()));
            row.songAction.setOnClickListener(ignored -> removeTrack(track));
        }

        binding.albumsSection.setVisibility(albums.isEmpty() ? View.GONE : View.VISIBLE);
        header(binding.albumsHeader, R.string.lib_liked_albums,
                vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.lib_album_count, albums.size(), albums.size()), null);
        binding.albumsList.removeAllViews();
        for (FavoriteAlbum album : albums) {
            View tile = CatalogViews.albumTile(binding.albumsList, album.getTitle(), album.getArtistName(), null,
                    () -> CatalogViews.open(binding.getRoot(), R.id.albumDetailFragment, "albumId", album.getId()))
                    .getRoot();
            tile.setOnLongClickListener(ignored -> {
                removeAlbum(album);
                return true;
            });
            ((LinearLayout.LayoutParams) tile.getLayoutParams()).setMarginEnd(CatalogViews.dp(tile, 16));
        }

        binding.playlistsSection.setVisibility(playlists.isEmpty() ? View.GONE : View.VISIBLE);
        String playlistCount = vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.lib_playlist_count, playlists.size(), playlists.size());
        header(binding.playlistsHeader, R.string.lib_my_playlists, playlists.size() > PLAYLIST_PREVIEW
                ? getString(R.string.lib_count_see_all, playlistCount) : playlistCount,
                playlists.size() > PLAYLIST_PREVIEW ? () -> Navigation.findNavController(binding.getRoot())
                        .navigate(R.id.my_playlists_fragment) : null);
        binding.playlistsList.removeAllViews();
        for (PlaylistSummary playlist : playlists.subList(0, Math.min(PLAYLIST_PREVIEW, playlists.size()))) {
            playlistRow(binding.playlistsList, playlist);
        }
    }

    /** Shared with My playlists: artwork placeholder, name, song count and a chevron. */
    public static void playlistRow(ViewGroup parent, PlaylistSummary playlist) {
        ItemSongRowBinding row = CatalogViews.songRow(parent, playlist.getName(),
                vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(parent.getResources(), R.plurals.queue_song_count,
                        (int) playlist.getTrackCount(), (int) playlist.getTrackCount()),
                null, R.drawable.ic_chevron_right,
                () -> CatalogViews.open(parent, R.id.playlist_detail_fragment, "playlistId", playlist.getId()));
        row.songArtwork.setImageResource(R.drawable.ic_queue);
    }

    private static void summary(ItemLibrarySummaryBinding row, int icon, int title) {
        row.summaryIcon.setImageResource(icon);
        row.summaryTitle.setText(title);
    }

    private static void header(ViewSectionHeaderBinding header, int title, String meta, @Nullable Runnable onSeeAll) {
        header.sectionTitle.setText(title);
        header.sectionSeeAll.setText(meta);
        header.sectionSeeAll.setClickable(onSeeAll != null);
        header.sectionSeeAll.setOnClickListener(onSeeAll == null ? null : ignored -> onSeeAll.run());
    }

    private void loadPlaylistQuota() {
        playlistRepository.getPlaylistQuota(new PlaylistRepository.ResultCallback<PlaylistQuota>() {
            @Override
            public void onSuccess(PlaylistQuota result) {
                playlistQuota = result;
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                playlistQuota = null;
            }
        });
    }

    private void removeTrack(FavoriteTrack track) {
        if (track.getId() == null) return;
        viewModel.removeFavoriteTrack(track.getId(), (succeeded, failureType) ->
                notifyRemoval(succeeded, failureType, R.string.library_removed_track, track.getTitle()));
    }

    private void removeAlbum(FavoriteAlbum album) {
        if (album.getId() == null) return;
        viewModel.removeFavoriteAlbum(album.getId(), (succeeded, failureType) ->
                notifyRemoval(succeeded, failureType, R.string.library_removed_album, album.getTitle()));
    }

    private void notifyRemoval(boolean succeeded, @Nullable LibraryRepository.FailureType failureType,
            int successMessage, @Nullable String title) {
        if (binding == null) return;
        String text = succeeded ? getString(successMessage, title == null ? "" : title)
                : getString(failureType == LibraryRepository.FailureType.AUTHENTICATION_REQUIRED
                        ? R.string.library_sign_in_again : R.string.library_action_failed);
        Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
