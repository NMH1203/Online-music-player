-- 1. Bảng lưu bài hát yêu thích của User
CREATE TABLE user_favorite_songs (
    user_id INT NOT NULL,
    song_id INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, song_id)
);

-- 2. Bảng lưu Album yêu thích của User
CREATE TABLE user_favorite_albums (
    user_id INT NOT NULL,
    album_id INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, album_id)
);

-- 3. Bảng danh sách Playlist cá nhân của User
CREATE TABLE user_playlists (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_private BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Bảng trung gian liên kết Bài hát nằm trong Playlist nào
CREATE TABLE playlist_songs (
    playlist_id INT NOT NULL REFERENCES user_playlists(id) ON DELETE CASCADE,
    song_id INT NOT NULL,
    position INT NOT NULL, -- Thứ tự sắp xếp bài hát trong playlist
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (playlist_id, song_id)
);
