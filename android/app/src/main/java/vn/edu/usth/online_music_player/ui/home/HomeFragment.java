package vn.edu.usth.online_music_player.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import java.time.LocalTime;
import java.util.ArrayList;
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
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.RecentTrack;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track;
import vn.edu.usth.online_music_player.databinding.FragmentHomeBinding;
import vn.edu.usth.online_music_player.databinding.ViewSectionHeaderBinding;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;
import vn.edu.usth.online_music_player.ui.profile.ProfileViewModel;

/** Figma "Home": greeting, Premium banner, Recently played, For you, New releases, Featured artists. */
public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private Call<HomeResponse> request;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header(binding.homeRecentHeader, R.string.home_recently_played);
        header(binding.homeForYouHeader, R.string.home_for_you);
        header(binding.homeReleasesHeader, R.string.home_new_releases);
        header(binding.homeArtistsHeader, R.string.home_featured_artists);
        View.OnClickListener openProfile = ignored ->
                Navigation.findNavController(view).navigate(R.id.navigation_profile);
        binding.homeAvatar.setOnClickListener(openProfile);
        binding.homePremiumBanner.setOnClickListener(openProfile);
        binding.homeState.stateAction.setOnClickListener(ignored -> loadHome());

        new ViewModelProvider(requireActivity()).get(ProfileViewModel.class).state()
                .observe(getViewLifecycleOwner(), this::renderAccount);
        loadHome();
    }

    private void renderAccount(ProfileViewModel.State state) {
        String name = state.profile == null ? null : state.profile.displayName;
        int hour = LocalTime.now().getHour();
        String greeting = getString(hour < 12 ? R.string.home_greeting_morning
                : hour < 18 ? R.string.home_greeting_afternoon : R.string.home_greeting_evening);
        binding.homeGreeting.setText(name == null || name.isEmpty() ? greeting
                : getString(R.string.home_greeting_named, greeting, name.split(" ")[0]));
        binding.homeAvatar.setText(CatalogViews.initials(name));
        binding.homePremiumBanner.setVisibility(
                state.entitlement != null && !state.entitlement.adFree ? View.VISIBLE : View.GONE);
    }

    private void loadHome() {
        if (request != null) request.cancel();
        binding.homeProgress.setVisibility(View.VISIBLE);
        binding.homeState.getRoot().setVisibility(View.GONE);
        request = ApiClient.catalog().getHome();
        request.enqueue(new Callback<HomeResponse>() {
            @Override
            public void onResponse(@NonNull Call<HomeResponse> call, @NonNull Response<HomeResponse> response) {
                if (binding == null) return;
                binding.homeProgress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    showError();
                    return;
                }
                render(response.body());
            }

            @Override
            public void onFailure(@NonNull Call<HomeResponse> call, @NonNull Throwable error) {
                if (binding == null || call.isCanceled()) return;
                binding.homeProgress.setVisibility(View.GONE);
                showError();
            }
        });
    }

    private void render(HomeResponse data) {
        boolean any = renderRecent(data.recentlyPlayed);
        // For you falls back to the popular list when the server has no recommendations.
        List<Track> forYou = data.forYou != null && !data.forYou.isEmpty() ? data.forYou : data.tracks;
        if (data.forYou != null && !data.forYou.isEmpty()) {
            for (Track track : data.forYou) track.playable = true;
        } else {
            binding.homeForYouHeader.sectionTitle.setText(R.string.home_popular);
        }
        any |= renderForYou(forYou);
        any |= renderReleases(data.albums);
        any |= renderArtists(data.artists);
        if (!any) {
            showState(R.drawable.ic_music, R.string.home_empty_title, R.string.home_empty_message, false);
        }
    }

    private boolean renderRecent(@Nullable List<RecentTrack> recent) {
        binding.homeRecentContainer.removeAllViews();
        boolean show = recent != null && !recent.isEmpty();
        binding.homeRecentSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return false;
        List<String> ids = new ArrayList<>();
        for (RecentTrack track : recent) ids.add(track.trackId);
        LinearLayout row = null;
        for (int i = 0; i < Math.min(4, recent.size()); i++) {
            if (i % 2 == 0) {
                if (row != null) CatalogViews.spread(row, 12, 2);
                row = CatalogViews.equalRow(binding.homeRecentContainer, 8);
            }
            RecentTrack track = recent.get(i);
            int start = i;
            CatalogViews.albumCard(row, track.title, track.artistName, track.coverUrl,
                    () -> ((MainActivity) requireActivity()).playTrackIds(ids, start));
        }
        CatalogViews.spread(row, 12, 2);
        return true;
    }

    private boolean renderForYou(@Nullable List<Track> tracks) {
        binding.homeForYouContainer.removeAllViews();
        boolean show = tracks != null && !tracks.isEmpty();
        binding.homeForYouSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return false;
        for (Track track : tracks.subList(0, Math.min(5, tracks.size()))) {
            CatalogViews.songRow(binding.homeForYouContainer, track.title,
                    track.playable ? CatalogViews.trackSubtitle(track.artistName, track.durationSeconds)
                            : getString(R.string.unplayable),
                    track.coverUrl, track.playable ? R.drawable.ic_play : 0,
                    track.playable ? () -> ((MainActivity) requireActivity()).playCatalogTracks(tracks, track) : null);
        }
        return true;
    }

    private boolean renderReleases(@Nullable List<Album> albums) {
        binding.homeReleasesContainer.removeAllViews();
        boolean show = albums != null && !albums.isEmpty();
        binding.homeReleasesSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return false;
        LinearLayout row = null;
        for (int i = 0; i < Math.min(4, albums.size()); i++) {
            if (i % 2 == 0) {
                if (row != null) CatalogViews.spread(row, 10, 2);
                row = CatalogViews.equalRow(binding.homeReleasesContainer, 8);
            }
            Album album = albums.get(i);
            CatalogViews.albumCard(row, album.title, album.artistName, album.coverUrl,
                    () -> CatalogViews.open(binding.getRoot(), R.id.albumDetailFragment, "albumId", album.id));
        }
        CatalogViews.spread(row, 10, 2);
        return true;
    }

    private boolean renderArtists(@Nullable List<Artist> artists) {
        binding.homeArtistsContainer.removeAllViews();
        boolean show = artists != null && !artists.isEmpty();
        binding.homeArtistsSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return false;
        for (Artist artist : artists.subList(0, Math.min(4, artists.size()))) {
            CatalogViews.artistCircle(binding.homeArtistsContainer, artist.name, artist.coverUrl,
                    () -> CatalogViews.open(binding.getRoot(), R.id.artistDetailFragment, "artistId", artist.id));
        }
        return true;
    }

    private void showError() {
        for (View section : new View[] {binding.homeRecentSection, binding.homeForYouSection,
                binding.homeReleasesSection, binding.homeArtistsSection}) {
            section.setVisibility(View.GONE);
        }
        showState(R.drawable.ic_wifi_off, R.string.home_error_title, R.string.home_error_message, true);
    }

    private void showState(int icon, int title, int message, boolean retry) {
        binding.homeState.getRoot().setVisibility(View.VISIBLE);
        binding.homeState.stateIcon.setImageResource(icon);
        binding.homeState.stateTitle.setText(title);
        binding.homeState.stateMessage.setText(message);
        binding.homeState.stateAction.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private static void header(ViewSectionHeaderBinding header, int title) {
        header.sectionTitle.setText(title);
        // No "See all" screens in the MVP; sections already show the full short list.
        header.sectionSeeAll.setVisibility(View.GONE);
    }


    @Override
    public void onDestroyView() {
        if (request != null) request.cancel();
        binding = null;
        super.onDestroyView();
    }
}
