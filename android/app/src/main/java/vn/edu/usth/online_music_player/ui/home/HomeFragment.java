package vn.edu.usth.online_music_player.ui.home;

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

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.catalog.ApiClient;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Album;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Artist;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.HomeResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track;
import vn.edu.usth.online_music_player.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    )
    {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        loadHome();
        return binding.getRoot();
    }

    private void loadHome() {
        binding.homeProgress.setVisibility(View.VISIBLE);
        binding.homeMessage.setVisibility(View.GONE);

        ApiClient.catalog().getHome().enqueue(new Callback<>() {
            @Override
            public void onResponse(
                    @NonNull Call<HomeResponse> call,
                    @NonNull Response<HomeResponse> response
            ) {
                if (binding == null) return;

                binding.homeProgress.setVisibility(View.GONE);

                if (!response.isSuccessful() || response.body() == null) {
                    showMessage("Unable to download catalog. HTTP" + response.code());
                    return;
                }

                HomeResponse data = response.body();

                if (data.tracks != null) {
                    for (Track track : data.tracks) {
                        addTrackRow(track);
                    }
                }

                if (data.albums != null) {
                    for (Album album : data.albums) {
                        addCatalogCard(
                                binding.albumsContainer,
                                album.title,
                                album.artistName,
                                false
                        );
                    }
                }

                if (data.artists != null) {
                    for (Artist artist : data.artists) {
                        addCatalogCard(
                                binding.artistsContainer,
                                artist.name,
                                "Artist",
                                true
                        );
                    }
                }

                if (binding.tracksContainer.getChildCount() == 0
                        && binding.albumsContainer.getChildCount() == 0
                        && binding.artistsContainer.getChildCount() == 0) {
                    showMessage("The catalog is currently empty.");
                }
            }

            @Override
            public void onFailure(
                    @NonNull Call<HomeResponse> call,
                    @NonNull Throwable error
            ) {
                if (binding == null) return;

                binding.homeProgress.setVisibility(View.GONE);
                showMessage("Failed to connect to the backend: " + error.getMessage());
            }
        });
    }

    private void addTrackRow(Track track) {
        String artist = track.artistName == null
                ? "Unknown artist"
                : track.artistName;

        String status = track.playable
                ? formatDuration(track.durationSeconds)
                : "Unplayable";

        TextView row = new TextView(requireContext());


        row.setText(track.title + "\n" + artist + " . " + status);
        row.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        row.setTextSize(15);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(makeRoundedBackground(R.color.surface_primary));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        );
        params.topMargin = dp(8);

        binding.tracksContainer.addView(row, params);
    }

    private void addCatalogCard(
            LinearLayout container,
            String title,
            String subtitle,
            boolean artistCard
    ) {
        TextView card = new TextView(requireContext());

        String label = artistCard ? "♪\n" : "♫\n";
        card.setText(label + (title == null ? "Untitled" : title)
                + "\n" + (subtitle == null ? "" : subtitle));

        card.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        card.setTextSize(14);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12),dp(12));
        card.setBackground(makeRoundedBackground(R.color.surface_primary));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(148),
                dp(112)
        );
        params.rightMargin = dp(10);

        container.addView(card,params);
    }

    private GradientDrawable makeRoundedBackground(int colorResource) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(
                ContextCompat.getColor(requireContext(), colorResource)
        );
        background.setCornerRadius(dp(12));
        return background;
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
                value * requireContext().getResources().getDisplayMetrics().density
        );
    }

    private void showMessage(String message) {
        binding.homeMessage.setText(message);
        binding.homeMessage.setVisibility(View.VISIBLE);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
