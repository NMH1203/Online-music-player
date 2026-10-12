-- Optional metadata fixture: NOT playable audio. Keep draft until legal audio
-- is uploaded to MinIO and duration/file size/MIME type are verified.
BEGIN;
INSERT INTO artists(name, slug, bio)
SELECT 'Demo Artist ' || n, 'demo-artist-' || n, 'Synthetic metadata for integration tests'
FROM generate_series(1, 10) n ON CONFLICT (slug) DO NOTHING;
INSERT INTO albums(primary_artist_id, title, slug, release_date)
SELECT id, 'Demo Album ' || split_part(slug, '-', 3), 'demo-album-' || split_part(slug, '-', 3), DATE '2026-10-01'
FROM artists WHERE slug ~ '^demo-artist-([1-9]|10)$'
ON CONFLICT (primary_artist_id, slug) DO NOTHING;
INSERT INTO tracks(primary_artist_id, album_id, title, duration_seconds, audio_object_key,
 file_size_bytes, bitrate_kbps, status, popularity_score)
SELECT a.primary_artist_id, a.id, 'Demo Track ' || n, 180,
 'demo/audio/track-' || n || '.mp3', 2880000, 128, 'draft', (n * 17) % 100
FROM generate_series(1, 50) n
JOIN albums a ON a.slug = 'demo-album-' || (((n - 1) / 5) + 1)
JOIN artists ar ON ar.id = a.primary_artist_id AND ar.slug = 'demo-artist-' || (((n - 1) / 5) + 1)
ON CONFLICT (audio_object_key) DO NOTHING;
INSERT INTO track_genres(track_id, genre_id)
SELECT t.id, g.id FROM tracks t
JOIN genres g ON g.code = (ARRAY['pop','rock','jazz','classical','electronic'])[
 ((split_part(split_part(t.audio_object_key, 'track-', 2), '.', 1)::integer - 1) % 5) + 1]
WHERE t.audio_object_key ~ '^demo/audio/track-[0-9]+\.mp3$'
ON CONFLICT DO NOTHING;
COMMIT;
