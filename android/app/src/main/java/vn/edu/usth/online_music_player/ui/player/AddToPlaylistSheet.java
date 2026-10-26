package vn.edu.usth.online_music_player.ui.player;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.MediaItem;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.databinding.ItemSongRowBinding;
import vn.edu.usth.online_music_player.databinding.SheetAddToPlaylistBinding;
import vn.edu.usth.online_music_player.ui.common.Artwork;

/** Figma "Add to playlist – chooser": adds the playing track to one of the user's playlists. */
public class AddToPlaylistSheet extends BottomSheetDialogFragment {
    private static final String ARG_TRACK_ID = "trackId";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SUBTITLE = "subtitle";
    private static final String ARG_ARTWORK = "artwork";

    private final PlaylistRepository playlists = new PlaylistRepository();
    private SheetAddToPlaylistBinding binding;

    static void show(FragmentManager manager, MediaItem item) {
        Bundle args = new Bundle();
        args.putString(ARG_TRACK_ID, item.mediaId);
        args.putCharSequence(ARG_TITLE, item.mediaMetadata.title);
        CharSequence artist = item.mediaMetadata.artist;
        CharSequence album = item.mediaMetadata.albumTitle;
        args.putString(ARG_SUBTITLE, (artist == null ? "" : artist)
                + (album == null ? "" : " · " + album));
        args.putParcelable(ARG_ARTWORK, item.mediaMetadata.artworkUri);
        AddToPlaylistSheet sheet = new AddToPlaylistSheet();
        sheet.setArguments(args);
        sheet.show(manager, "add_to_playlist");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = SheetAddToPlaylistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        ItemSongRowBinding track = binding.sheetTrack;
        track.songTitle.setText(args.getCharSequence(ARG_TITLE));
        track.songSubtitle.setText(args.getString(ARG_SUBTITLE));
        track.songAction.setImageResource(R.drawable.ic_music);
        track.songAction.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        track.songAction.setClickable(false);
        Artwork.load(track.songArtwork, args.getParcelable(ARG_ARTWORK));
        binding.sheetClose.setOnClickListener(ignored -> dismiss());
        binding.sheetCreate.setOnClickListener(ignored -> askNameAndCreate());
        load();
    }

    private void load() {
        binding.sheetProgress.setVisibility(View.VISIBLE);
        binding.sheetMessage.setVisibility(View.GONE);
        playlists.getPlaylists(new PlaylistRepository.ResultCallback<List<PlaylistSummary>>() {
            @Override
            public void onSuccess(List<PlaylistSummary> result) {
                if (binding == null) return;
                binding.sheetProgress.setVisibility(View.GONE);
                binding.sheetPlaylists.removeAllViews();
                if (result == null || result.isEmpty()) {
                    showMessage(getString(R.string.add_to_playlist_empty));
                    return;
                }
                for (PlaylistSummary playlist : result) addRow(playlist);
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding == null) return;
                binding.sheetProgress.setVisibility(View.GONE);
                showMessage(getString(R.string.add_to_playlist_load_failed));
            }
        });
    }

    private void addRow(PlaylistSummary playlist) {
        ItemSongRowBinding row = ItemSongRowBinding.inflate(
                getLayoutInflater(), binding.sheetPlaylists, false);
        row.songTitle.setText(playlist.getName());
        row.songSubtitle.setText(vn.edu.usth.online_music_player.ui.common.CatalogViews.plural(getResources(), 
                R.plurals.queue_song_count, (int) playlist.getTrackCount(), (int) playlist.getTrackCount()));
        Artwork.load(row.songArtwork, null);
        row.songArtwork.setImageResource(R.drawable.ic_queue);
        row.songAction.setImageResource(R.drawable.ic_plus);
        row.songAction.setContentDescription(
                getString(R.string.now_playing_add_to_playlist) + ": " + playlist.getName());
        View.OnClickListener add = ignored -> addTo(playlist.getId(), playlist.getName(), row);
        row.getRoot().setOnClickListener(add);
        row.songAction.setOnClickListener(add);
        binding.sheetPlaylists.addView(row.getRoot());
    }

    private void addTo(String playlistId, String name, @Nullable ItemSongRowBinding row) {
        String trackId = requireArguments().getString(ARG_TRACK_ID);
        if (row != null) row.getRoot().setEnabled(false);
        playlists.addTracks(playlistId, java.util.Collections.singletonList(trackId),new PlaylistRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                if (binding == null) return;
                if (row == null) load(); // new playlist: refresh so it shows with its song count
                if (row != null) {
                    row.songAction.setImageResource(R.drawable.ic_check);
                    ImageViewCompat.setImageTintList(row.songAction,
                            ContextCompat.getColorStateList(requireContext(), R.color.accent_primary));
                    row.songSubtitle.setText(R.string.add_to_playlist_already_added);
                }
                Toast.makeText(requireContext(), getString(R.string.add_to_playlist_added, name),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding == null) return;
                if (row != null) row.getRoot().setEnabled(true);
                Toast.makeText(requireContext(), R.string.add_to_playlist_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void askNameAndCreate() {
        EditText name = new EditText(requireContext());
        name.setHint(R.string.add_to_playlist_name_hint);
        name.setSingleLine(true);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(200)});
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        box.setPadding(pad, 0, pad, 0);
        box.addView(name);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_to_playlist_create)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.add_to_playlist_create, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) {
                        name.setError(getString(R.string.playlist_name_required));
                        return;
                    }
                    dialog.dismiss();
                    create(value);
                }));
        dialog.show();
    }

    private void create(String name) {
        playlists.createPlaylist(name, null, new PlaylistRepository.ResultCallback<PlaylistSummary>() {
            @Override
            public void onSuccess(PlaylistSummary created) {
                if (binding == null || created == null) return;
                addTo(created.getId(), created.getName(), null);
            }

            @Override
            public void onFailure(PlaylistRepository.FailureType failureType) {
                if (binding == null) return;
                Toast.makeText(requireContext(),
                        failureType == PlaylistRepository.FailureType.PLAYLIST_LIMIT_REACHED
                                ? R.string.add_to_playlist_limit : R.string.add_to_playlist_failed,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showMessage(String message) {
        binding.sheetMessage.setText(message);
        binding.sheetMessage.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
