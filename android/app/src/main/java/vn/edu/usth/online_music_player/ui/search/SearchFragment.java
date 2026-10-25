package vn.edu.usth.online_music_player.ui.search;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.catalog.ApiClient;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Album;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Artist;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.SearchResponse;
import vn.edu.usth.online_music_player.databinding.FragmentSearchBinding;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private Call<SearchResponse> activeSearch;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    )
    {
        binding = FragmentSearchBinding.inflate(inflater, container, false);

        binding.searchMessage.setText("Search for songs, artists, or albums");
        setupSearchInput();

        return binding.getRoot();
    }

    private void setupSearchInput() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text,
                    int start,
                    int count,
                    int after
            ) {
                // No need to solve before text changed
            }

            @Override
            public void onTextChanged(
                    CharSequence text,
                    int start,
                    int before,
                    int count
            ) {
                String query = text.toString().trim();

                binding.clearButton.setVisibility(
                        query.isEmpty() ? View.GONE : View.VISIBLE
                );

                // Cancel the previous search wait to avoid calling the API for each character
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                }

                // Cancel old request if users inserted new query
                if (activeSearch != null) {
                    activeSearch.cancel();
                }

                if (query.isEmpty()) {
                    clearResults();
                    binding.searchProgress.setVisibility(View.GONE);
                    binding.searchMessage.setText(
                            "Search for songs, artists, or albums"
                    );
                    binding.searchMessage.setVisibility(View.VISIBLE);
                    return;
                }

                pendingSearch = () -> searchCatalog(query);

                // Wait for user to stop typing 400ms to sent request
                searchHandler.postDelayed(pendingSearch, 400);
            }


            @Override
            public void afterTextChanged(Editable editable) {
                // No need to solve after text changed
            }
        } );

        binding.clearButton.setOnClickListener(view ->
                binding.searchInput.setText("")
        );
    }

    private void searchCatalog(String query) {
        if (binding == null) return;

        clearResults();
        binding.searchMessage.setVisibility(View.GONE);
        binding.searchProgress.setVisibility(View.VISIBLE);

        activeSearch = ApiClient.catalog().search(query);

        activeSearch.enqueue(new Callback<>() {
            @Override
            public void onResponse(
                    @NonNull Call<SearchResponse> call,
                    @NonNull Response<SearchResponse> response
            ) {
                if (binding == null || call.isCanceled()) return;

                binding.searchProgress.setVisibility(View.GONE);

                if (!response.isSuccessful() || response.body() == null) {
                    showMessage("Search failed. HTTP " + response.code());
                    return;
                }

                showResult(response.body());
            }

            @Override
            public void onFailure(
                    @NonNull Call<SearchResponse> call,
                    @NonNull Throwable error
            ) {
                if (binding == null || call.isCanceled()) return;

                binding.searchProgress.setVisibility(View.GONE);
                showMessage("Could not connect to the backend.");
            }
        });
    }

    private void showResult(SearchResponse data){
        List<Track> tracks = data.tracks;
        List<Artist> artists = data.artists;
        List<Album> albums = data.albums;

        if (tracks != null && !tracks.isEmpty()) {
            binding.tracksSection.setVisibility(View.VISIBLE);
            binding.tracksContainer.setVisibility(View.VISIBLE);

            for (Track track : tracks) {
                addResultRow(
                        binding.tracksContainer,
                        track.title,
                        safeText(track.artistName, "Unknown artist")
                        + " . " + (track.playable ? formatDuration(track.durationSeconds) : "Unplayable")
                );
            }
        }

        if (artists != null && !artists.isEmpty()) {
            binding.artistsSection.setVisibility(View.VISIBLE);
            binding.artistsContainer.setVisibility(View.VISIBLE);

            for (Artist artist : artists) {
                addResultRow(
                        binding.artistsContainer,
                        artist.name,
                        "Artist"
                );
            }
        }

        if (albums != null && !albums.isEmpty()) {
            binding.albumsSection.setVisibility(View.VISIBLE);
            binding.albumsContainer.setVisibility(View.VISIBLE);

            for (Album album : albums){
                addResultRow(
                        binding.albumsContainer,
                        album.title,
                        safeText(album.artistName, "Unknown artist")
                );
            }
        }

        boolean noResults =
                (tracks == null || tracks.isEmpty())
                && (artists == null || artists.isEmpty())
                && (albums == null || albums.isEmpty());

        if (noResults) {
            showMessage("No results found.");
        }
    }

    private void addResultRow(
            LinearLayout container,
            String title,
            String subtitle
    ) {
        TextView row = new TextView(requireContext());

        row.setText(safeText(title, "Untitled") + "\n" + subtitle);
        row.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_primary)
        );
        row.setTextSize(14);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(makeRowBackground());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);

        container.addView(row, params);
    }

    private GradientDrawable makeRowBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(
                ContextCompat.getColor(requireContext(), R.color.surface_primary)
        );
        background.setCornerRadius(dp(12));
        return background;
    }

    private void clearResults() {
        if (binding == null) return;

        binding.tracksContainer.removeAllViews();
        binding.artistsContainer.removeAllViews();
        binding.albumsContainer.removeAllViews();

        binding.tracksSection.setVisibility(View.GONE);
        binding.tracksContainer.setVisibility(View.GONE);
        binding.artistsSection.setVisibility(View.GONE);
        binding.artistsContainer.setVisibility(View.GONE);
        binding.albumsSection.setVisibility(View.GONE);
        binding.albumsContainer.setVisibility(View.GONE);
    }

    private void showMessage(String message) {
        if (binding == null) return;

        binding.searchMessage.setText(message);
        binding.searchMessage.setVisibility(View.VISIBLE);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String formatDuration(int seconds) {
        return String.format(
                Locale.getDefault(),
                "%d:%02d",
                seconds / 60,
                seconds % 60
        );
    }

    private int dp(int value) {
        return Math.round(
                value * requireContext()
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
    @Override
    public void onDestroyView() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }

        if (activeSearch != null) {
            activeSearch.cancel();
        }

        binding = null;
        super.onDestroyView();
    }
}
