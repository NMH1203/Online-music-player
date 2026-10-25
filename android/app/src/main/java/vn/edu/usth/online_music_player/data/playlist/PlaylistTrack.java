package vn.edu.usth.online_music_player.data.playlist;

public final class PlaylistTrack {
    private String id;
    private String title;
    private int durationSeconds;
    private String artistName;
    private int position;

    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getArtistName() { return artistName; }
    public int getPosition() { return position; }
}
