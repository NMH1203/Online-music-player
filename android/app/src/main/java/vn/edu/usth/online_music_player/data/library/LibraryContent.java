package vn.edu.usth.online_music_player.data.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LibraryContent {
    private final List<FavoriteTrack> favoriteTracks;
    private final List<FavoriteAlbum> favoriteAlbums;
    private final List<PlaylistSummary> playlists;

    public LibraryContent(
            List<FavoriteTrack> favoriteTracks,
            List<FavoriteAlbum> favoriteAlbums,
            List<PlaylistSummary> playlists
    ) {
        this.favoriteTracks = immutableCopy(favoriteTracks);
        this.favoriteAlbums = immutableCopy(favoriteAlbums);
        this.playlists = immutableCopy(playlists);
    }

    public List<FavoriteTrack> getFavoriteTracks() {
        return favoriteTracks;
    }

    public List<FavoriteAlbum> getFavoriteAlbums() {
        return favoriteAlbums;
    }

    public List<PlaylistSummary> getPlaylists() {
        return playlists;
    }

    public LibraryContent withoutFavoriteTrack(String trackId) {
        List<FavoriteTrack> remaining = new ArrayList<>();
        for (FavoriteTrack track : favoriteTracks) {
            if (!trackId.equals(track.getId())) {
                remaining.add(track);
            }
        }
        return new LibraryContent(remaining, favoriteAlbums, playlists);
    }

    public LibraryContent withoutFavoriteAlbum(String albumId) {
        List<FavoriteAlbum> remaining = new ArrayList<>();
        for (FavoriteAlbum album : favoriteAlbums) {
            if (!albumId.equals(album.getId())) {
                remaining.add(album);
            }
        }
        return new LibraryContent(favoriteTracks, remaining, playlists);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        List<T> copy = values == null ? new ArrayList<>() : new ArrayList<>(values);
        return Collections.unmodifiableList(copy);
    }
}
