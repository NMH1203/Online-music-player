package vn.edu.usth.online_music_player.ui.album;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.edu.usth.online_music_player.MainActivity;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.catalog.ApiClient;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.AlbumDetailResponse;
import vn.edu.usth.online_music_player.data.catalog.CatalogModels.Track;
import vn.edu.usth.online_music_player.data.library.FavoriteAlbum;
import vn.edu.usth.online_music_player.data.library.LibraryContent;
import vn.edu.usth.online_music_player.data.library.LibraryRepository;
import vn.edu.usth.online_music_player.databinding.FragmentAlbumDetailBinding;
import vn.edu.usth.online_music_player.databinding.ItemTrackNumberBinding;
import vn.edu.usth.online_music_player.ui.common.Artwork;
import vn.edu.usth.online_music_player.ui.common.CatalogViews;

/** Figma "Album detail": artwork, artist link, like/play/shuffle and the numbered track list. */
public class AlbumDetailFragment extends Fragment {
    private final LibraryRepository library = new LibraryRepository();
    private FragmentAlbumDetailBinding binding;
    private Call<AlbumDetailResponse> request;
    private String albumId;
    private boolean liked;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAlbumDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.albumTopBar.topBarTitle.setText(R.string.album_title_bar);
        binding.albumTopBar.topBarBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.albumState.stateAction.setOnClickListener(ignored -> loadAlbum());
        binding.albumLike.setOnClickListener(ignored -> toggleLike());
        binding.albumLike.setEnabled(false);
        albumId = requireArguments().getString("albumId");
        loadAlbum();
        loadLike();
    }

    private void loadAlbum() {
        if (albumId == null || albumId.isEmpty()) {
            showError();
            return;
        }
        binding.albumDetailProgress.setVisibility(View.VISIBLE);
        binding.albumState.getRoot().setVisibility(View.GONE);
        binding.albumContent.setVisibility(View.GONE);
        request = ApiClient.catalog().getAlbum(albumId);
        request.enqueue(new Callback<AlbumDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<AlbumDetailResponse> call,
                    @NonNull Response<AlbumDetailResponse> response) {
                if (binding == null) return;
                binding.albumDetailProgress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null || response.body().album == null) {
                    showError();
                    return;
                }
                render(response.body());
            }

            @Override
            public void onFailure(@NonNull Call<AlbumDetailResponse> call, @NonNull Throwable error) {
                if (binding == null || call.isCanceled()) return;
                binding.albumDetailProgress.setVisibility(View.GONE);
                showError();
            }
        });
    }

    private void render(AlbumDetailResponse data) {
        binding.albumContent.setVisibility(View.VISIBLE);
        binding.albumTitle.setText(data.album.title);
        CatalogViews.cover(binding.albumArtwork, data.album.coverUrl);
        binding.albumArtist.setText(data.album.artistName == null ? "" : data.album.artistName);
        binding.albumArtist.setOnClickListener(ignored -> CatalogViews.open(binding.getRoot(),
                R.id.artistDetailFragment, "artistId", data.album.artistId));

        List<Track> tracks = data.tracks == null ? java.util.Collections.emptyList() : data.tracks;
        int totalSeconds = 0;
        for (Track track : tracks) totalSeconds += Math.max(0, track.durationSeconds);
        String count = vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.queue_song_count, tracks.size(), tracks.size());
        StringBuilder meta = new StringBuilder(getString(R.string.label_album));
        if (data.album.releaseDate != null && data.album.releaseDate.length() >= 4) {
            meta.append(" · ").append(data.album.releaseDate, 0, 4);
        }
        meta.append(" · ").append(count).append(" · ").append(Math.round(totalSeconds / 60f)).append(" min");
        binding.albumReleaseDate.setText(meta);

        binding.albumSongsHeader.sectionTitle.setText(R.string.album_songs);
        binding.albumSongsHeader.sectionSeeAll.setText(count);
        binding.albumSongsHeader.sectionSeeAll.setClickable(false);
        binding.albumSongsHeader.sectionSeeAll.setBackground(null);

        MainActivity activity = (MainActivity) requireActivity();
        boolean anyPlayable = !CatalogViews.playableIds(tracks, false).isEmpty();
        binding.albumPlay.setEnabled(anyPlayable);
        binding.albumShuffle.setEnabled(anyPlayable);
        binding.albumPlay.setOnClickListener(ignored -> activity.playTrackIds(CatalogViews.playableIds(tracks, false), 0));
        binding.albumShuffle.setOnClickListener(ignored -> activity.playTrackIds(CatalogViews.playableIds(tracks, true), 0));

        binding.albumTracksContainer.removeAllViews();
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            ItemTrackNumberBinding row = ItemTrackNumberBinding.inflate(
                    getLayoutInflater(), binding.albumTracksContainer, false);
            row.trackIndex.setText(String.valueOf(i + 1));
            row.trackTitle.setText(track.title);
            row.trackDuration.setText(track.playable
                    ? Artwork.time(track.durationSeconds * 1000L) : getString(R.string.unplayable));
            if (track.playable) row.getRoot().setOnClickListener(ignored -> activity.playCatalogTracks(tracks, track));
            else row.getRoot().setEnabled(false);
            binding.albumTracksContainer.addView(row.getRoot());
        }
    }

    private void loadLike() {
        library.load(new LibraryRepository.LoadCallback() {
            @Override
            public void onLoaded(LibraryContent content) {
                for (FavoriteAlbum album : content.getFavoriteAlbums()) {
                    if (album.getId().equals(albumId)) liked = true;
                }
                renderLike();
            }

            @Override
            public void onFailed(LibraryRepository.FailureType failureType) {
                renderLike();
            }
        });
    }

    private void renderLike() {
        if (binding == null) return;
        binding.albumLike.setEnabled(albumId != null);
        ImageViewCompat.setImageTintList(binding.albumLike, ContextCompat.getColorStateList(
                requireContext(), liked ? R.color.accent_primary : R.color.icon_primary));
        binding.albumLike.setContentDescription(getString(liked ? R.string.album_unlike : R.string.album_like));
    }

    private void toggleLike() {
        boolean like = !liked;
        liked = like;
        renderLike();
        LibraryRepository.ActionCallback callback = (succeeded, failure) -> {
            if (succeeded || binding == null) return;
            liked = !like;
            renderLike();
            Toast.makeText(requireContext(), R.string.now_playing_like_failed, Toast.LENGTH_SHORT).show();
        };
        if (like) library.addFavoriteAlbum(albumId, callback);
        else library.removeFavoriteAlbum(albumId, callback);
    }

    private void showError() {
        binding.albumState.getRoot().setVisibility(View.VISIBLE);
        binding.albumState.stateIcon.setImageResource(R.drawable.ic_wifi_off);
        binding.albumState.stateTitle.setText(R.string.detail_error_title);
        binding.albumState.stateMessage.setText(R.string.detail_error_message);
        binding.albumState.stateAction.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        if (request != null) request.cancel();
        library.cancelAll();
        binding = null;
        super.onDestroyView();
    }
}
