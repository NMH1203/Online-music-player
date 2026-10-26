package vn.edu.usth.online_music_player.data.playback;

/** Public playback metadata returned by GET /tracks/{id}/playback. */
public final class TrackPlayback {
    private String trackId;
    private String title;
    private String artistName;
    private int durationSeconds;
    private String mimeType;
    private long fileSizeBytes;
    private String streamUrl;
    private String attributionText;
    private String artistId;
    private String albumId;
    private String albumTitle;
    private String coverUrl;

    public String getTrackId() {
        return trackId;
    }

    public String getTitle() {
        return title;
    }

    public String getArtistName() {
        return artistName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public String getAttributionText() {
        return attributionText;
    }

    public String getArtistId() {
        return artistId;
    }

    public String getAlbumId() {
        return albumId;
    }

    public String getAlbumTitle() {
        return albumTitle;
    }

    /** Server-relative image path such as "/albums/{id}/cover", or null when there is no artwork. */
    public String getCoverUrl() {
        return coverUrl;
    }
}
