package vn.edu.usth.online_music_player.ui.library;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import vn.edu.usth.online_music_player.data.library.LibraryContent;
import vn.edu.usth.online_music_player.data.library.LibraryRepository;

public final class LibraryViewModel extends ViewModel {
    public interface ActionCallback {
        void onCompleted(boolean succeeded, LibraryRepository.FailureType failureType);
    }

    private final LibraryRepository repository = new LibraryRepository();
    private final MutableLiveData<LibraryUiState> state =
            new MutableLiveData<>(LibraryUiState.loading(null));
    private int requestVersion;

    public LiveData<LibraryUiState> getState() {
        return state;
    }

    public void refresh() {
        LibraryUiState current = state.getValue();
        LibraryContent previousContent = current == null ? null : current.getContent();
        int requestId = ++requestVersion;
        state.setValue(LibraryUiState.loading(previousContent));
        repository.load(new LibraryRepository.LoadCallback() {
            @Override
            public void onLoaded(LibraryContent content) {
                if (requestId == requestVersion) {
                    state.setValue(LibraryUiState.ready(content));
                }
            }

            @Override
            public void onFailed(LibraryRepository.FailureType failureType) {
                if (requestId != requestVersion) {
                    return;
                }
                if (previousContent != null) {
                    state.setValue(LibraryUiState.ready(previousContent));
                } else if (failureType == LibraryRepository.FailureType.AUTHENTICATION_REQUIRED) {
                    state.setValue(LibraryUiState.authenticationRequired());
                } else {
                    state.setValue(LibraryUiState.error());
                }
            }
        });
    }

    public void removeFavoriteTrack(@NonNull String trackId, ActionCallback callback) {
        removeFavorite(trackId, true, callback);
    }

    public void removeFavoriteAlbum(@NonNull String albumId, ActionCallback callback) {
        removeFavorite(albumId, false, callback);
    }

    private void removeFavorite(String id, boolean track, ActionCallback callback) {
        LibraryUiState current = state.getValue();
        LibraryContent previous = current == null ? null : current.getContent();
        if (previous == null) {
            callback.onCompleted(false, LibraryRepository.FailureType.AUTHENTICATION_REQUIRED);
            return;
        }

        LibraryContent updated = track
                ? previous.withoutFavoriteTrack(id)
                : previous.withoutFavoriteAlbum(id);
        state.setValue(LibraryUiState.ready(updated));

        LibraryRepository.ActionCallback repositoryCallback = (succeeded, failureType) -> {
            if (succeeded) {
                callback.onCompleted(true, null);
                refresh();
            } else {
                state.setValue(LibraryUiState.ready(previous));
                callback.onCompleted(false, failureType);
            }
        };
        if (track) {
            repository.removeFavoriteTrack(id, repositoryCallback);
        } else {
            repository.removeFavoriteAlbum(id, repositoryCallback);
        }
    }

    @Override
    protected void onCleared() {
        repository.cancelAll();
    }
}
