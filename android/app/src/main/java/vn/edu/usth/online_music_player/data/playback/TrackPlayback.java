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
}
