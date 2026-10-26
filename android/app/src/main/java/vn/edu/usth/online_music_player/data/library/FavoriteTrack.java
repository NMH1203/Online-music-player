package vn.edu.usth.online_music_player.data.library;

public final class FavoriteTrack {
    private String id;
    private String title;
    private int durationSeconds;
    private String artistName;
    private String albumTitle;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getAlbumTitle() {
        return albumTitle;
    }
}
