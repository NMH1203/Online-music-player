package vn.edu.usth.online_music_player.dto;

public class FavoriteRequest {
    private Long targetId; // Có thể là trackId hoặc albumId
    private String type;   // Quy ước truyền lên: "TRACK" hoặc "ALBUM"

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
