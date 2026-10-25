package vn.edu.usth.online_music_player.ui.artist;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.ArtistDetailResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Album;
import vn.edu.usth.online_music_player.databinding.FragmentArtistDetailBinding;

public class ArtistDetailFragment extends Fragment {

    private FragmentArtistDetailBinding binding;
    private Call<ArtistDetailResponse> request;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentArtistDetailBinding.inflate(inflater, container, false);

        String artistId = requireArguments().getString("artistId");

        if (artistId == null || artistId.isEmpty()) {
            showMessage("Missing artist ID.");
        } else {
            loadArtist(artistId);
        }

        return binding.getRoot();
    }

    private void loadArtist(String artistId) {
        binding.artistDetailProgress.setVisibility(View.VISIBLE);
        binding.artistDetailMessage.setVisibility(View.GONE);

        request = ApiClient.catalog().getArtist(artistId);

        request.enqueue(new Callback<>() {
            @Override
            public void onResponse(
                    @NonNull Call<ArtistDetailResponse> call,
                    @NonNull Response<ArtistDetailResponse> response
            ) {
                if (binding == null) return;

                binding.artistDetailProgress.setVisibility(View.GONE);

                if (!response.isSuccessful() || response.body() == null) {
                    showMessage("Could not load artist. HTTP " + response.code());
                    return;
                }

                ArtistDetailResponse data = response.body();

                if (data.artist != null) {
                    binding.artistName.setText(data.artist.name);
                    binding.artistBio.setText(
                            data.artist.bio == null || data.artist.bio.isEmpty()
                                    ? "No biography available."
                                    : data.artist.bio
                    );
                }

                addTracks(data.tracks);
                addAlbums(data.albums);
            }

            @Override
            public void onFailure(
                    @NonNull Call<ArtistDetailResponse> call,
                    @NonNull Throwable error
            ) {
                if (binding == null || call.isCanceled()) return;

                binding.artistDetailProgress.setVisibility(View.GONE);
                showMessage("Could not connect to the backend.");
            }
        });
    }

    private void addTracks(List<Track> tracks) {
        if (tracks == null) return;

        for (Track track : tracks) {
            String subtitle = track.artistName == null ? "" : track.artistName;
            if (!track.playable) {
                subtitle += " · Unplayable";
            } else {
                subtitle += " · " + formatDuration(track.durationSeconds);
            }

            addRow(binding.artistTracksContainer, track.title, subtitle);
        }
    }

    private void addAlbums(List<Album> albums) {
        if (albums == null) return;

        for (Album album : albums) {
            String subtitle = album.releaseDate == null ? "" : album.releaseDate;
            addRow(binding.artistAlbumsContainer, album.title, subtitle);
        }
    }

    private void addRow(LinearLayout container, String title, String subtitle) {
        TextView row = new TextView(requireContext());
        row.setText((title == null ? "Untitled" : title) + "\n" + subtitle);
        row.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        row.setTextSize(14);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(requireContext(), R.color.surface_primary));
        background.setCornerRadius(dp(12));
        row.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        container.addView(row, params);
    }

    private void showMessage(String message) {
        if (binding == null) return;
        binding.artistDetailMessage.setText(message);
        binding.artistDetailMessage.setVisibility(View.VISIBLE);
    }

    private String formatDuration(int seconds) {
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(
                value * requireContext().getResources().getDisplayMetrics().density
        );
    }

    @Override
    public void onDestroyView() {
        if (request != null) request.cancel();
        binding = null;
        super.onDestroyView();
    }
}