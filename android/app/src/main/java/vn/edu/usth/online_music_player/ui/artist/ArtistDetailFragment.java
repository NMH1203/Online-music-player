package vn.edu.usth.online_music_player.ui.artist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import java.util.Collections;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.catalog.ApiClient;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Album;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.ArtistDetailResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track;
import vn.edu.usth.online_music_player.databinding.FragmentArtistDetailBinding;
import vn.edu.usth.online_music_player.databinding.ItemAlbumTileBinding;
import vn.edu.usth.online_music_player.databinding.ViewSectionHeaderBinding;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;

/** Figma "Artist detail": portrait, play/shuffle, popular songs and releases. */
public class ArtistDetailFragment extends Fragment {
    private FragmentArtistDetailBinding binding;
    private Call<ArtistDetailResponse> request;
    private String artistId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentArtistDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.artistTopBar.topBarTitle.setText(R.string.artist_title_bar);
        binding.artistTopBar.topBarBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.artistState.stateAction.setOnClickListener(ignored -> loadArtist());
        artistId = requireArguments().getString("artistId");
        loadArtist();
    }

    private void loadArtist() {
        if (artistId == null || artistId.isEmpty()) {
            showError();
            return;
        }
        binding.artistDetailProgress.setVisibility(View.VISIBLE);
        binding.artistState.getRoot().setVisibility(View.GONE);
        binding.artistContent.setVisibility(View.GONE);
        request = ApiClient.catalog().getArtist(artistId);
        request.enqueue(new Callback<ArtistDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<ArtistDetailResponse> call,
                    @NonNull Response<ArtistDetailResponse> response) {
                if (binding == null) return;
                binding.artistDetailProgress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null || response.body().artist == null) {
                    showError();
                    return;
                }
                render(response.body());
            }

            @Override
            public void onFailure(@NonNull Call<ArtistDetailResponse> call, @NonNull Throwable error) {
                if (binding == null || call.isCanceled()) return;
                binding.artistDetailProgress.setVisibility(View.GONE);
                showError();
            }
        });
    }

    private void render(ArtistDetailResponse data) {
        binding.artistContent.setVisibility(View.VISIBLE);
        binding.artistName.setText(data.artist.name);
        CatalogViews.cover(binding.artistPortrait, data.artist.coverUrl);
        if (data.artist.coverUrl == null) binding.artistPortrait.setImageResource(R.drawable.ic_user);
        boolean hasBio = data.artist.bio != null && !data.artist.bio.trim().isEmpty();
        binding.artistBio.setVisibility(hasBio ? View.VISIBLE : View.GONE);
        binding.artistBio.setText(hasBio ? data.artist.bio : "");

        List<Track> tracks = data.tracks == null ? Collections.emptyList() : data.tracks;
        MainActivity activity = (MainActivity) requireActivity();
        boolean anyPlayable = !CatalogViews.playableIds(tracks, false).isEmpty();
        binding.artistPlay.setEnabled(anyPlayable);
        binding.artistShuffle.setEnabled(anyPlayable);
        binding.artistPlay.setOnClickListener(ignored -> activity.playTrackIds(CatalogViews.playableIds(tracks, false), 0));
        binding.artistShuffle.setOnClickListener(ignored -> activity.playTrackIds(CatalogViews.playableIds(tracks, true), 0));

        header(binding.artistPopularHeader, R.string.artist_popular, !tracks.isEmpty());
        binding.artistTracksContainer.removeAllViews();
        for (Track track : tracks) {
            CatalogViews.songRow(binding.artistTracksContainer, track.title,
                    track.playable ? CatalogViews.trackSubtitle(
                            track.artistName == null ? data.artist.name : track.artistName, track.durationSeconds)
                            : getString(R.string.unplayable),
                    track.coverUrl, track.playable ? R.drawable.ic_play : 0,
                    track.playable ? () -> activity.playCatalogTracks(tracks, track) : null);
        }

        List<Album> albums = data.albums == null ? Collections.emptyList() : data.albums;
        header(binding.artistReleasesHeader, R.string.artist_releases, !albums.isEmpty());
        binding.artistAlbumsContainer.removeAllViews();
        for (Album album : albums) {
            String year = album.releaseDate == null || album.releaseDate.length() < 4
                    ? "" : album.releaseDate.substring(0, 4) + " · ";
            ItemAlbumTileBinding tile = CatalogViews.albumTile(binding.artistAlbumsContainer, album.title,
                    year + getString(R.string.label_album), album.coverUrl,
                    () -> CatalogViews.open(binding.getRoot(), R.id.albumDetailFragment, "albumId", album.id));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tile.getRoot().getLayoutParams();
            params.setMarginEnd(CatalogViews.dp(binding.getRoot(), 16));
        }
    }

    private static void header(ViewSectionHeaderBinding header, int title, boolean visible) {
        header.getRoot().setVisibility(visible ? View.VISIBLE : View.GONE);
        header.sectionTitle.setText(title);
        header.sectionSeeAll.setVisibility(View.GONE);
    }

    private void showError() {
        binding.artistState.getRoot().setVisibility(View.VISIBLE);
        binding.artistState.stateIcon.setImageResource(R.drawable.ic_wifi_off);
        binding.artistState.stateTitle.setText(R.string.detail_error_title);
        binding.artistState.stateMessage.setText(R.string.detail_error_message);
        binding.artistState.stateAction.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        if (request != null) request.cancel();
        binding = null;
        super.onDestroyView();
    }
}
