package vn.edu.usth.online_music_player.ui.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import java.util.List;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;
import vn.edu.usth.online_music_player.data.playlist.PlaylistQuota;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.databinding.FragmentMyPlaylistsBinding;
import vn.edu.usth.online_music_player.ui.library.LibraryFragment;

/** Figma "My playlists": every playlist, Create playlist and the Free quota card. */
public class MyPlaylistsFragment extends Fragment {
    private final PlaylistRepository repository = new PlaylistRepository();
    private FragmentMyPlaylistsBinding binding;
    @Nullable private PlaylistQuota quota;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentMyPlaylistsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.myPlaylistsTopBar.topBarTitle.setText(R.string.lib_my_playlists);
        binding.myPlaylistsTopBar.topBarBack.setOnClickListener(ignored -> Navigation.findNavController(view).navigateUp());
        binding.myPlaylistsCreate.setOnClickListener(ignored -> PlaylistLimitSheet.startCreate(this, quota));
        binding.myPlaylistsPremium.setOnClickListener(ignored ->
                Navigation.findNavController(view).navigate(R.id.navigation_profile));
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        binding.myPlaylistsProgress.setVisibility(View.VISIBLE);
        binding.myPlaylistsMessage.setVisibility(View.GONE);
        repository.getPlaylists(new PlaylistRepository.ResultCallback<List<PlaylistSummary>>() {
            @Override
            public void onSuccess(List<PlaylistSummary> result) {
                if (binding == null) return;
                binding.myPlaylistsProgress.setVisibility(View.GONE);
                binding.myPlaylistsList.removeAllViews();
                int count = result == null ? 0 : result.size();
                binding.myPlaylistsSubtitle.setText(getString(R.string.my_playlists_subtitle,
                        vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), R.plurals.lib_playlist_count, count, count)));
                if (count == 0) {
                    binding.myPlaylistsMessage.setText(R.string.add_to_playlist_empty);
                    binding.myPlaylistsMessage.setVisibility(View.VISIBLE);
                    return;
                }
                for (PlaylistSummary playlist : result) LibraryFragment.playlistRow(binding.myPlaylistsList, playlist);
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding == null) return;
                binding.myPlaylistsProgress.setVisibility(View.GONE);
                binding.myPlaylistsMessage.setText(R.string.add_to_playlist_load_failed);
                binding.myPlaylistsMessage.setVisibility(View.VISIBLE);
            }
        });
        repository.getPlaylistQuota(new PlaylistRepository.ResultCallback<PlaylistQuota>() {
            @Override
            public void onSuccess(PlaylistQuota result) {
                quota = result;
                if (binding == null || result == null) return;
                Integer max = result.getMaxPlaylists();
                binding.myPlaylistsPlan.setVisibility(max == null ? View.GONE : View.VISIBLE);
                if (max != null) {
                    binding.myPlaylistsUsed.setText(getString(R.string.free_plan_used, (int) result.getPlaylistCount(), max));
                }
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                quota = null;
            }
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
