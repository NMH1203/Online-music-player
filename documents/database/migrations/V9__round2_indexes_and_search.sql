-- T25 migration round 2: indexes for the demo queries (catalog, search,
-- history, recommendation, expiry jobs). No table or column is changed, so the
-- migration is safe to apply on a database that already holds data.
-- Plans were checked with EXPLAIN against ~120k history rows; see
-- database/checks/round2_consistency.sql for the matching data audit.

-- Search: CatalogController uses ILIKE '%term%' on tracks.title, albums.title and
-- artists.name. A btree cannot serve a leading wildcard; trigram GIN can.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS ix_tracks_title_trgm  ON tracks  USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_albums_title_trgm  ON albums  USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_artists_name_trgm  ON artists USING gin (name  gin_trgm_ops);

-- Home / recommendation cold start: published tracks by popularity. Partial
-- index matches ORDER BY popularity_score DESC, published_at DESC, id exactly.
CREATE INDEX IF NOT EXISTS ix_tracks_published_popularity
  ON tracks (popularity_score DESC, published_at DESC, id)
  WHERE status = 'published';

-- Album detail: published tracks of an album in release order.
CREATE INDEX IF NOT EXISTS ix_tracks_album_published
  ON tracks (album_id, published_at, id)
  WHERE status = 'published';

-- History (T18/T19): only events counted as a play are listed. Covering index
-- gives index-only scans for "last 20 plays" and the Free 7-day window.
CREATE INDEX IF NOT EXISTS ix_history_user_played_recent
  ON listening_history (user_id, started_at DESC) INCLUDE (track_id)
  WHERE counted_as_play;

-- Recommendation v1: most played tracks in a recent window, across users.
CREATE INDEX IF NOT EXISTS ix_history_played_recent
  ON listening_history (started_at DESC, track_id)
  WHERE counted_as_play;

-- Session cleanup / refresh checks only touch sessions that are still live.
CREATE INDEX IF NOT EXISTS ix_sessions_live_expiry
  ON user_sessions (expires_at)
  WHERE revoked_at IS NULL;

-- Premium expiry / demo reset (T23): active subscriptions that can expire.
CREATE INDEX IF NOT EXISTS ix_subscriptions_active_expiry
  ON subscriptions (expires_at)
  WHERE status = 'active' AND expires_at IS NOT NULL;
