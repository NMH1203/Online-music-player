package vn.edu.usth.online_music_player.ui.album;

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
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.AlbumDetailResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track;
import vn.edu.usth.online_music_player.databinding.FragmentAlbumDetailBinding;

public class AlbumDetailFragment extends Fragment {

    private FragmentAlbumDetailBinding binding;
    private Call<AlbumDetailResponse> request;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentAlbumDetailBinding.inflate(inflater, container, false);

        String albumId = requireArguments().getString("albumId");

        if (albumId == null || albumId.isEmpty()) {
            showMessage("Missing album ID.");
        } else {
            loadAlbum(albumId);
        }

        return binding.getRoot();
    }

    private void loadAlbum(String albumId) {
        binding.albumDetailProgress.setVisibility(View.VISIBLE);
        binding.albumDetailMessage.setVisibility(View.GONE);

        request = ApiClient.catalog().getAlbum(albumId);

        request.enqueue(new Callback<>() {
            @Override
            public void onResponse(
                    @NonNull Call<AlbumDetailResponse> call,
                    @NonNull Response<AlbumDetailResponse> response
            ) {
                if (binding == null) return;

                binding.albumDetailProgress.setVisibility(View.GONE);

                if (!response.isSuccessful() || response.body() == null) {
                    showMessage("Could not load album. HTTP " + response.code());
                    return;
                }

                AlbumDetailResponse data = response.body();

                if (data.album != null) {
                    binding.albumTitle.setText(data.album.title);
                    binding.albumArtist.setText(
                            data.album.artistName == null
                                    ? "Unknown artist"
                                    : data.album.artistName
                    );
                    binding.albumReleaseDate.setText(
                            data.album.releaseDate == null
                                    ? ""
                                    : data.album.releaseDate
                    );
                }

                addTracks(data.tracks);
            }

            @Override
            public void onFailure(
                    @NonNull Call<AlbumDetailResponse> call,
                    @NonNull Throwable error
            ) {
                if (binding == null || call.isCanceled()) return;

                binding.albumDetailProgress.setVisibility(View.GONE);
                showMessage("Could not connect to the backend.");
            }
        });
    }

    private void addTracks(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            showMessage("This album has no tracks.");
            return;
        }

        for (Track track : tracks) {
            String subtitle = track.playable
                    ? formatDuration(track.durationSeconds)
                    : "Unplayable";

            addRow(binding.albumTracksContainer, track.title, subtitle);
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
        binding.albumDetailMessage.setText(message);
        binding.albumDetailMessage.setVisibility(View.VISIBLE);
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