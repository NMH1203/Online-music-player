package vn.edu.usth.online_music_player.ui.search;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.catalog.ApiClient;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Album;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Artist;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.HomeResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.SearchResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track;
import vn.edu.usth.online_music_player.databinding.FragmentSearchBinding;
import vn.edu.usth.online_music_player.databinding.ItemRecentSearchBinding;
import vn.edu.usth.online_music_player.databinding.ItemSongRowBinding;
import vn.edu.usth.online_music_player.databinding.ViewSectionHeaderBinding;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;

/** Figma "Search": recent searches + suggestions when idle, grouped results, no-results and error states. */
public class SearchFragment extends Fragment {
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private FragmentSearchBinding binding;
    private RecentSearches recent;
    @Nullable private Runnable pendingSearch;
    @Nullable private Call<SearchResponse> activeSearch;
    @Nullable private Call<HomeResponse> suggestionsCall;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recent = new RecentSearches(requireContext());
        binding.searchRecentHeader.sectionTitle.setText(R.string.search_recent);
        binding.searchRecentHeader.sectionSeeAll.setText(R.string.search_clear_all);
        binding.searchRecentHeader.sectionSeeAll.setOnClickListener(ignored -> {
            recent.clear();
            renderRecent();
        });
        hideSeeAll(binding.searchSuggestionsHeader, R.string.search_try_new);
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable editable) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                scheduleSearch(text.toString().trim());
            }
        });
        binding.searchInput.setOnEditorActionListener((field, action, event) -> {
            boolean enterKey = event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_SEARCH && !enterKey) return false;
            String query = currentQuery();
            if (!query.isEmpty()) {
                recent.add(query);
                searchNow(query);
            }
            return true;
        });
        renderIdle();
        loadSuggestions();
        // Coming back from an album/artist page, the restored query re-runs through the TextWatcher.
    }

    private void scheduleSearch(String query) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        if (activeSearch != null) activeSearch.cancel();
        if (query.isEmpty()) {
            renderIdle();
            return;
        }
        pendingSearch = () -> searchNow(query);
        // Wait until typing pauses so every character does not hit the API.
        searchHandler.postDelayed(pendingSearch, 400);
    }

    private void searchNow(String query) {
        if (binding == null) return;
        if (activeSearch != null) activeSearch.cancel();
        clearResults();
        binding.searchIdle.setVisibility(View.GONE);
        binding.searchState.getRoot().setVisibility(View.GONE);
        binding.searchProgress.setVisibility(View.VISIBLE);
        Call<SearchResponse> call = ApiClient.catalog().search(query);
        activeSearch = call;
        call.enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SearchResponse> done, @NonNull Response<SearchResponse> response) {
                if (binding == null || done.isCanceled()) return;
                binding.searchProgress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    showError(query);
                    return;
                }
                showResults(query, response.body());
            }

            @Override
            public void onFailure(@NonNull Call<SearchResponse> failed, @NonNull Throwable error) {
                if (binding == null || failed.isCanceled()) return;
                binding.searchProgress.setVisibility(View.GONE);
                showError(query);
            }
        });
    }

    private void showResults(String query, SearchResponse data) {
        List<Track> tracks = data.tracks;
        List<Artist> artists = data.artists;
        List<Album> albums = data.albums;
        if (section(binding.searchTracksHeader, binding.tracksSection, R.string.search_songs, tracks)) {
            for (Track track : tracks) {
                CatalogViews.songRow(binding.tracksContainer, track.title,
                        track.playable ? CatalogViews.trackSubtitle(track.artistName, track.durationSeconds)
                                : getString(R.string.unplayable),
                        track.coverUrl, track.playable ? R.drawable.ic_play : 0,
                        track.playable ? () -> {
                            recent.add(query);
                            ((MainActivity) requireActivity()).playCatalogTracks(tracks, track);
                        } : null);
            }
        }
        if (section(binding.searchArtistsHeader, binding.artistsSection, R.string.search_artists, artists)) {
            for (Artist artist : artists) {
                ItemSongRowBinding row = CatalogViews.songRow(binding.artistsContainer, artist.name,
                        getString(R.string.label_artist), artist.coverUrl, R.drawable.ic_chevron_right, () -> {
                            recent.add(query);
                            CatalogViews.open(binding.getRoot(), R.id.artistDetailFragment, "artistId", artist.id);
                        });
                row.songArtwork.setShapeAppearanceModel(row.songArtwork.getShapeAppearanceModel()
                        .withCornerSize(new com.google.android.material.shape.RelativeCornerSize(0.5f)));
                if (artist.coverUrl == null) row.songArtwork.setImageResource(R.drawable.ic_user);
            }
        }
        if (section(binding.searchAlbumsHeader, binding.albumsSection, R.string.search_albums, albums)) {
            for (Album album : albums) {
                String subtitle = (album.artistName == null ? "" : album.artistName + " · ")
                        + getString(R.string.label_album)
                        + (album.releaseDate == null || album.releaseDate.length() < 4
                                ? "" : " · " + album.releaseDate.substring(0, 4));
                CatalogViews.songRow(binding.albumsContainer, album.title, subtitle, album.coverUrl,
                        R.drawable.ic_chevron_right, () -> {
                            recent.add(query);
                            CatalogViews.open(binding.getRoot(), R.id.albumDetailFragment, "albumId", album.id);
                        });
            }
        }
        boolean none = isEmpty(tracks) && isEmpty(artists) && isEmpty(albums);
        if (none) {
            showState(R.drawable.ic_search, getString(R.string.search_no_results_title),
                    getString(R.string.search_no_results_message, query), R.string.search_clear,
                    () -> binding.searchInput.setText(""));
        }
    }

    private boolean section(ViewSectionHeaderBinding header, View section, int title, @Nullable List<?> items) {
        boolean show = !isEmpty(items);
        section.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            header.sectionTitle.setText(title);
            header.sectionSeeAll.setText(vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), 
                    R.plurals.search_result_count, items.size(), items.size()));
            header.sectionSeeAll.setClickable(false);
            header.sectionSeeAll.setBackground(null);
        }
        return show;
    }

    private void showError(String query) {
        showState(R.drawable.ic_wifi_off, getString(R.string.search_error_title),
                getString(R.string.search_error_message), R.string.now_playing_retry, () -> searchNow(query));
    }

    private void showState(int icon, String title, String message, int action, Runnable onAction) {
        binding.searchState.getRoot().setVisibility(View.VISIBLE);
        binding.searchState.stateIcon.setImageResource(icon);
        binding.searchState.stateTitle.setText(title);
        binding.searchState.stateMessage.setText(message);
        binding.searchState.stateAction.setVisibility(View.VISIBLE);
        binding.searchState.stateAction.setText(action);
        binding.searchState.stateAction.setOnClickListener(ignored -> onAction.run());
    }

    private void renderIdle() {
        if (binding == null) return;
        clearResults();
        binding.searchProgress.setVisibility(View.GONE);
        binding.searchState.getRoot().setVisibility(View.GONE);
        binding.searchIdle.setVisibility(View.VISIBLE);
        renderRecent();
    }

    private void renderRecent() {
        binding.searchRecentContainer.removeAllViews();
        List<String> queries = recent.load();
        binding.searchRecentSection.setVisibility(queries.isEmpty() ? View.GONE : View.VISIBLE);
        for (String query : queries) {
            ItemRecentSearchBinding row = ItemRecentSearchBinding.inflate(
                    getLayoutInflater(), binding.searchRecentContainer, false);
            row.recentQuery.setText(query);
            row.getRoot().setOnClickListener(ignored -> {
                binding.searchInput.setText(query);
                binding.searchInput.setSelection(query.length());
            });
            row.recentRemove.setContentDescription(getString(R.string.search_remove_recent, query));
            row.recentRemove.setOnClickListener(ignored -> {
                recent.remove(query);
                renderRecent();
            });
            binding.searchRecentContainer.addView(row.getRoot());
        }
    }

    private void loadSuggestions() {
        suggestionsCall = ApiClient.catalog().getHome();
        suggestionsCall.enqueue(new Callback<HomeResponse>() {
            @Override
            public void onResponse(@NonNull Call<HomeResponse> call, @NonNull Response<HomeResponse> response) {
                if (binding == null || response.body() == null || isEmpty(response.body().albums)) return;
                binding.searchSuggestionsContainer.removeAllViews();
                List<Album> albums = response.body().albums;
                for (Album album : albums.subList(0, Math.min(3, albums.size()))) {
                    CatalogViews.albumTile(binding.searchSuggestionsContainer, album.title, album.artistName,
                            album.coverUrl, () -> CatalogViews.open(binding.getRoot(),
                                    R.id.albumDetailFragment, "albumId", album.id));
                }
                CatalogViews.spread(binding.searchSuggestionsContainer, 12, 3);
                binding.searchSuggestionsSection.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(@NonNull Call<HomeResponse> call, @NonNull Throwable error) {
                // Suggestions are optional; the search field still works without them.
            }
        });
    }

    private void clearResults() {
        binding.tracksContainer.removeAllViews();
        binding.artistsContainer.removeAllViews();
        binding.albumsContainer.removeAllViews();
        binding.tracksSection.setVisibility(View.GONE);
        binding.artistsSection.setVisibility(View.GONE);
        binding.albumsSection.setVisibility(View.GONE);
    }

    private String currentQuery() {
        return binding.searchInput.getText() == null ? "" : binding.searchInput.getText().toString().trim();
    }

    private static void hideSeeAll(ViewSectionHeaderBinding header, int title) {
        header.sectionTitle.setText(title);
        header.sectionSeeAll.setVisibility(View.GONE);
    }

    private static boolean isEmpty(@Nullable List<?> items) {
        return items == null || items.isEmpty();
    }

    @Override
    public void onDestroyView() {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        if (activeSearch != null) activeSearch.cancel();
        if (suggestionsCall != null) suggestionsCall.cancel();
        binding = null;
        super.onDestroyView();
    }
}
