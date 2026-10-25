package vn.edu.usth.online_music_player.ui.library;

import androidx.annotation.Nullable;
import vn.edu.usth.online_music_player.data.library.LibraryContent;

public final class LibraryUiState {
    public enum Status {
        LOADING,
        READY,
        AUTHENTICATION_REQUIRED,
        ERROR
    }

    private final Status status;
    @Nullable private final LibraryContent content;

    private LibraryUiState(Status status, @Nullable LibraryContent content) {
        this.status = status;
        this.content = content;
    }

    public static LibraryUiState loading(@Nullable LibraryContent content) {
        return new LibraryUiState(Status.LOADING, content);
    }

    public static LibraryUiState ready(LibraryContent content) {
        return new LibraryUiState(Status.READY, content);
    }

    public static LibraryUiState authenticationRequired() {
        return new LibraryUiState(Status.AUTHENTICATION_REQUIRED, null);
    }

    public static LibraryUiState error() {
        return new LibraryUiState(Status.ERROR, null);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public LibraryContent getContent() {
        return content;
    }
}
