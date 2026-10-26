package vn.edu.usth.online_music_player.data.catalog;

import java.util.List;

public class CatalogModels {
    public static class HomeResponse {
        public List<Track> tracks;
        public List<Album> albums;
        public List<Artist> artists;
        /** Empty when signed out or nothing has been played yet. */
        public List<RecentTrack> recentlyPlayed;
        /** Recommendations; always published, so playable even though the server omits the flag. */
        public List<Track> forYou;
    }

    public static class RecentTrack {
        public String trackId;
        public String title;
        public String artistName;
        public String albumId;
        public String albumTitle;
        public String coverUrl;
        public String playedAt;
    }

    public static class SearchResponse {
        public List<Track> tracks;
        public List<Album> albums;
        public List<Artist> artists;
    }

    public static class Track {
        public String id;
        public String title;
        public String artistId;
        public String artistName;
        public String albumId;
        public String albumTitle;
        public int durationSeconds;
        public boolean playable;
        public String coverUrl;
    }

    public static class Album {
        public String id;
        public String title;
        public String artistId;
        public String artistName;
        public String releaseDate;
        public String coverUrl;
    }

    public static class Artist {
        public String id;
        public String name;
        public String bio;
        public String coverUrl;
    }

    public static class ArtistDetailResponse {
        public Artist artist;
        public List<Album> albums;
        public List<Track> tracks;
    }

    public static class AlbumDetailResponse {
        public Album album;
        public List<Track> tracks;
    }


}
