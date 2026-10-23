package vn.edu.usth.online_music_player.ui.library;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;
import java.util.Locale;
import vn.edu.usth.online_music_player.R;
import vn.edu.usth.online_music_player.data.library.FavoriteAlbum;
import vn.edu.usth.online_music_player.data.library.FavoriteTrack;
import vn.edu.usth.online_music_player.data.library.LibraryContent;
import vn.edu.usth.online_music_player.data.library.LibraryRepository;
import vn.edu.usth.online_music_player.data.library.PlaylistSummary;
import vn.edu.usth.online_music_player.data.playlist.PlaylistRepository;
import vn.edu.usth.online_music_player.databinding.FragmentLibraryBinding;
import vn.edu.usth.online_music_player.databinding.ItemLibraryEntryBinding;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        binding.libraryRefresh.setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.accent_primary));
        binding.libraryRefresh.setOnRefreshListener(viewModel::refresh);
        binding.libraryRetry.setOnClickListener(ignored -> viewModel.refresh());
        binding.createPlaylistButton.setOnClickListener(ignored -> showCreatePlaylistDialog());
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refresh();
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(LibraryUiState state) {
        if (binding == null) {
            return;
        }

        LibraryContent content = state.getContent();
        if (content != null) {
            binding.libraryStatusPanel.setVisibility(View.GONE);
            binding.librarySections.setVisibility(View.VISIBLE);
            binding.libraryRefresh.setRefreshing(state.getStatus() == LibraryUiState.Status.LOADING);
            renderContent(content);
            return;
        }

        binding.libraryRefresh.setRefreshing(false);
        binding.librarySections.setVisibility(View.GONE);
        binding.libraryStatusPanel.setVisibility(View.VISIBLE);

        switch (state.getStatus()) {
            case LOADING:
                binding.libraryProgress.setVisibility(View.VISIBLE);
                binding.libraryStatusTitle.setText(R.string.library_loading_title);
                binding.libraryStatusMessage.setText(R.string.library_loading_message);
                binding.libraryRetry.setVisibility(View.GONE);
                break;
            case AUTHENTICATION_REQUIRED:
                binding.libraryProgress.setVisibility(View.GONE);
                binding.libraryStatusTitle.setText(R.string.library_auth_title);
                binding.libraryStatusMessage.setText(R.string.library_auth_message);
                binding.libraryRetry.setVisibility(View.GONE);
                break;
            case ERROR:
            default:
                binding.libraryProgress.setVisibility(View.GONE);
                binding.libraryStatusTitle.setText(R.string.library_error_title);
                binding.libraryStatusMessage.setText(R.string.library_error_message);
                binding.libraryRetry.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void renderContent(LibraryContent content) {
        List<FavoriteTrack> tracks = content.getFavoriteTracks();
        binding.tracksSectionTitle.setText(
                getString(R.string.library_tracks_title, tracks.size()));
        binding.tracksList.removeAllViews();
        if (tracks.isEmpty()) {
            addEmptyState(binding.tracksList,
                    R.string.library_tracks_empty_title,
                    R.string.library_tracks_empty_message);
        } else {
            for (FavoriteTrack track : tracks) {
                String subtitle = nonEmpty(track.getArtistName(),
                        getString(R.string.library_unknown_artist));
                if (hasText(track.getAlbumTitle())) {
                    subtitle = subtitle + " · " + track.getAlbumTitle();
                }
                View row = createEntry(track.getTitle(), subtitle, track.getId(), true,
                        () -> removeTrack(track));
                binding.tracksList.addView(row);
            }
        }

        List<FavoriteAlbum> albums = content.getFavoriteAlbums();
        binding.albumsSectionTitle.setText(
                getString(R.string.library_albums_title, albums.size()));
        binding.albumsList.removeAllViews();
        if (albums.isEmpty()) {
            addEmptyState(binding.albumsList,
                    R.string.library_albums_empty_title,
                    R.string.library_albums_empty_message);
        } else {
            for (FavoriteAlbum album : albums) {
                String subtitle = nonEmpty(album.getArtistName(),
                        getString(R.string.library_unknown_artist));
                View row = createEntry(album.getTitle(), subtitle, album.getId(), true,
                        () -> removeAlbum(album));
                binding.albumsList.addView(row);
            }
        }

        List<PlaylistSummary> playlists = content.getPlaylists();
        binding.playlistsSectionTitle.setText(
                getString(R.string.library_playlists_title, playlists.size()));
        binding.playlistsList.removeAllViews();
        if (playlists.isEmpty()) {
            addEmptyState(binding.playlistsList,
                    R.string.library_playlists_empty_title,
                    R.string.library_playlists_empty_message);
        } else {
            for (PlaylistSummary playlist : playlists) {
                String subtitle = hasText(playlist.getDescription())
                        ? playlist.getDescription()
                        : getString(R.string.library_playlist_track_count, playlist.getTrackCount());
                View row = createEntry(playlist.getName(), subtitle, null, false, null);
                row.setClickable(true);
                row.setFocusable(true);
                row.setOnClickListener(ignored -> openPlaylist(playlist));
                binding.playlistsList.addView(row);
            }
        }
    }

    private void showCreatePlaylistDialog() {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(24);
        form.setPadding(horizontalPadding, dp(8), horizontalPadding, 0);

        EditText nameInput = new EditText(requireContext());
        nameInput.setSingleLine(true);
        nameInput.setHint(R.string.playlist_name_hint);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        form.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText descriptionInput = new EditText(requireContext());
        descriptionInput.setHint(R.string.playlist_description_hint);
        descriptionInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        descriptionInput.setMinLines(2);
        descriptionInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(8);
        form.addView(descriptionInput, descriptionParams);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_create_title)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.playlist_create, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
            String description = descriptionInput.getText() == null
                    ? "" : descriptionInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError(getString(R.string.playlist_name_required));
                nameInput.requestFocus();
                return;
            }

            PlaylistRepository repository = new PlaylistRepository();
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            repository.createPlaylist(name, description, new PlaylistRepository.ResultCallback<PlaylistSummary>() {
                @Override
                public void onSuccess(PlaylistSummary created) {
                    dialog.dismiss();
                    if (!isAdded() || binding == null || getView() == null) {
                        return;
                    }
                    if (created == null || !hasText(created.getId())) {
                        showMessage(getString(R.string.playlist_generic_error));
                        return;
                    }
                    Bundle args = new Bundle();
                    args.putString("playlistId", created.getId());
                    Navigation.findNavController(requireView())
                            .navigate(R.id.playlist_detail_fragment, args);
                }

                @Override
                public void onFailure(PlaylistRepository.FailureType failureType) {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                            .setEnabled(true);
                    showMessage(messageFor(failureType));
                }
            });
        }));
        dialog.show();
    }

    private void openPlaylist(PlaylistSummary playlist) {
        if (playlist == null || !hasText(playlist.getId()) || getView() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("playlistId", playlist.getId());
        Navigation.findNavController(requireView()).navigate(R.id.playlist_detail_fragment, args);
    }

    private String messageFor(PlaylistRepository.FailureType failureType) {
        if (failureType == PlaylistRepository.FailureType.AUTHENTICATION_REQUIRED) {
            return getString(R.string.playlist_auth_required);
        }
        if (failureType == PlaylistRepository.FailureType.NETWORK) {
            return getString(R.string.playlist_network_error);
        }
        if (failureType == PlaylistRepository.FailureType.NOT_FOUND) {
            return getString(R.string.playlist_not_found);
        }
        return getString(R.string.playlist_generic_error);
    }

    private void showMessage(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    private View createEntry(
            String title,
            String subtitle,
            @Nullable String favoriteId,
            boolean favorite,
            @Nullable Runnable removeAction
    ) {
        ItemLibraryEntryBinding row = ItemLibraryEntryBinding.inflate(
                LayoutInflater.from(requireContext()), null, false);
        row.entryArtwork.setText(firstGlyph(title));
        row.entryTitle.setText(nonEmpty(title, getString(R.string.library_untitled_item)));
        row.entrySubtitle.setText(subtitle);
        if (favorite && favoriteId != null && removeAction != null) {
            row.entryAction.setVisibility(View.VISIBLE);
            row.entryAction.setOnClickListener(ignored -> removeAction.run());
        } else {
            row.entryAction.setVisibility(View.GONE);
        }
        return row.getRoot();
    }

    private void addEmptyState(LinearLayout container, int titleResource, int messageResource) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackgroundResource(R.drawable.bg_library_empty);

        TextView title = new TextView(requireContext());
        title.setText(titleResource);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView message = new TextView(requireContext());
        message.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams messageParams = (LinearLayout.LayoutParams) message.getLayoutParams();
        messageParams.topMargin = dp(5);
        message.setLayoutParams(messageParams);
        message.setText(messageResource);
        message.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        message.setTextSize(13);

        card.addView(title);
        card.addView(message);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        container.addView(card, cardParams);
    }

    private void removeTrack(FavoriteTrack track) {
        if (track.getId() == null) {
            return;
        }
        viewModel.removeFavoriteTrack(track.getId(), (succeeded, failureType) -> {
            if (binding == null) {
                return;
            }
            int message = succeeded
                    ? R.string.library_removed_track
                    : failureType == LibraryRepository.FailureType.AUTHENTICATION_REQUIRED
                            ? R.string.library_sign_in_again
                            : R.string.library_action_failed;
            String text = succeeded
                    ? getString(message, nonEmpty(track.getTitle(), getString(R.string.library_untitled_item)))
                    : getString(message);
            Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_SHORT).show();
        });
    }

    private void removeAlbum(FavoriteAlbum album) {
        if (album.getId() == null) {
            return;
        }
        viewModel.removeFavoriteAlbum(album.getId(), (succeeded, failureType) -> {
            if (binding == null) {
                return;
            }
            int message = succeeded
                    ? R.string.library_removed_album
                    : failureType == LibraryRepository.FailureType.AUTHENTICATION_REQUIRED
                            ? R.string.library_sign_in_again
                            : R.string.library_action_failed;
            String text = succeeded
                    ? getString(message, nonEmpty(album.getTitle(), getString(R.string.library_untitled_item)))
                    : getString(message);
            Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_SHORT).show();
        });
    }

    private String firstGlyph(@Nullable String title) {
        if (!hasText(title)) {
            return "♫";
        }
        int firstCodePoint = title.trim().codePointAt(0);
        return new String(Character.toChars(firstCodePoint)).toUpperCase(Locale.getDefault());
    }

    private String nonEmpty(@Nullable String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
