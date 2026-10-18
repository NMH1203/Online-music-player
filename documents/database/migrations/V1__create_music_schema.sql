-- PostgreSQL 16. Source: database-design.md, GitHub main e8f76c3.
-- Business rules (quota, expiration, play threshold) are enforced by Spring Boot.
CREATE TABLE users (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 email varchar(320) NOT NULL,
 password_hash varchar(255) NOT NULL,
 role varchar(32) NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'admin')),
 status varchar(32) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled', 'deleted')),
 last_login_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
CREATE TABLE genres (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), code varchar(64) NOT NULL UNIQUE, name varchar(100) NOT NULL UNIQUE
);
CREATE TABLE plans (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), code varchar(32) NOT NULL UNIQUE,
 name varchar(100) NOT NULL, ad_free boolean NOT NULL,
 max_playlists integer CHECK (max_playlists > 0), history_days integer CHECK (history_days > 0),
 active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE user_profiles (
 user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 display_name varchar(100) NOT NULL, bio text,
 favorite_genre_id uuid REFERENCES genres(id) ON DELETE SET NULL,
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE user_sessions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_family_id uuid NOT NULL, refresh_token_hash varchar(255) NOT NULL UNIQUE,
 device_name varchar(200), replaced_by_session_id uuid REFERENCES user_sessions(id),
 expires_at timestamptz NOT NULL, revoked_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK (expires_at > created_at),
 CHECK (replaced_by_session_id IS NULL OR (revoked_at IS NOT NULL AND replaced_by_session_id <> id))
);
CREATE INDEX ix_sessions_family ON user_sessions(token_family_id, revoked_at);
CREATE INDEX ix_sessions_user ON user_sessions(user_id);
CREATE TABLE artists (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name varchar(200) NOT NULL,
 slug varchar(200) NOT NULL UNIQUE, bio text, image_object_key varchar(1024),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE albums (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), primary_artist_id uuid NOT NULL REFERENCES artists(id),
 title varchar(255) NOT NULL, slug varchar(255) NOT NULL, release_date date, cover_object_key varchar(1024),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(primary_artist_id, slug)
);
CREATE TABLE tracks (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), primary_artist_id uuid NOT NULL REFERENCES artists(id),
 album_id uuid REFERENCES albums(id) ON DELETE SET NULL,
 title varchar(255) NOT NULL, duration_seconds integer NOT NULL CHECK (duration_seconds > 0),
 audio_object_key varchar(1024) NOT NULL UNIQUE, cover_object_key varchar(1024),
 mime_type varchar(100) NOT NULL DEFAULT 'audio/mpeg',
 file_size_bytes bigint NOT NULL CHECK (file_size_bytes > 0), bitrate_kbps integer CHECK (bitrate_kbps > 0),
 status varchar(32) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'archived')),
 popularity_score numeric(12,4) NOT NULL DEFAULT 0 CHECK (popularity_score >= 0), published_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK (status <> 'published' OR published_at IS NOT NULL)
);
CREATE INDEX ix_tracks_status_published ON tracks(status, published_at DESC);
CREATE INDEX ix_tracks_artist ON tracks(primary_artist_id);
CREATE INDEX ix_tracks_album ON tracks(album_id, published_at);
CREATE TABLE track_genres (
 track_id uuid NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
 genre_id uuid NOT NULL REFERENCES genres(id), PRIMARY KEY(track_id, genre_id)
);
CREATE INDEX ix_track_genres_genre ON track_genres(genre_id, track_id);
CREATE TABLE library_tracks (
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 track_id uuid NOT NULL REFERENCES tracks(id), added_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id, track_id)
);
CREATE INDEX ix_library_tracks_recent ON library_tracks(user_id, added_at DESC);
CREATE INDEX ix_library_tracks_track ON library_tracks(track_id);
CREATE TABLE library_albums (
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 album_id uuid NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
 added_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id, album_id)
);
CREATE INDEX ix_library_albums_recent ON library_albums(user_id, added_at DESC);
CREATE INDEX ix_library_albums_album ON library_albums(album_id);
CREATE TABLE playlists (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 name varchar(200) NOT NULL, description text, cover_object_key varchar(1024),
 visibility varchar(32) NOT NULL DEFAULT 'private' CHECK (visibility = 'private'),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_playlists_user_recent ON playlists(user_id, updated_at DESC);
CREATE TABLE playlist_tracks (
 playlist_id uuid NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
 track_id uuid NOT NULL REFERENCES tracks(id), position integer NOT NULL CHECK (position >= 0),
 added_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(playlist_id, track_id),
 UNIQUE(playlist_id, position) DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX ix_playlist_tracks_track ON playlist_tracks(track_id);
CREATE TABLE listening_history (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, client_event_id uuid NOT NULL UNIQUE,
 user_id uuid NOT NULL REFERENCES users(id), track_id uuid NOT NULL REFERENCES tracks(id),
 source_playlist_id uuid REFERENCES playlists(id) ON DELETE SET NULL,
 source_context varchar(64) NOT NULL, started_at timestamptz NOT NULL, ended_at timestamptz,
 listened_seconds integer NOT NULL CHECK (listened_seconds >= 0), counted_as_play boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now(), CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX ix_history_user_recent ON listening_history(user_id, started_at DESC);
CREATE INDEX ix_history_track_recent ON listening_history(track_id, started_at DESC);
CREATE INDEX ix_history_source_playlist ON listening_history(source_playlist_id);
CREATE TABLE subscriptions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id),
 plan_id uuid NOT NULL REFERENCES plans(id),
 status varchar(32) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'cancelled')),
 source varchar(64) NOT NULL, starts_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz,
 ended_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
 CHECK (expires_at IS NULL OR expires_at > starts_at),
 CHECK (ended_at IS NULL OR ended_at >= starts_at),
 CHECK ((status = 'active' AND ended_at IS NULL) OR (status <> 'active' AND ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_subscriptions_one_active_per_user ON subscriptions(user_id) WHERE status = 'active';
CREATE INDEX ix_subscriptions_user_status ON subscriptions(user_id, status);
CREATE INDEX ix_subscriptions_plan ON subscriptions(plan_id);
CREATE TABLE payment_events (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), idempotency_key uuid NOT NULL UNIQUE,
 user_id uuid NOT NULL REFERENCES users(id), plan_id uuid NOT NULL REFERENCES plans(id),
 subscription_id uuid REFERENCES subscriptions(id), event_type varchar(64) NOT NULL,
 provider varchar(32) NOT NULL DEFAULT 'mock' CHECK (provider = 'mock'),
 status varchar(32) NOT NULL CHECK (status IN ('pending', 'success', 'failed')),
 amount numeric(12,2) NOT NULL DEFAULT 0 CHECK (amount = 0), currency varchar(3) NOT NULL DEFAULT 'VND',
 metadata jsonb NOT NULL DEFAULT '{}'::jsonb, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_events_user_recent ON payment_events(user_id, created_at DESC);
CREATE INDEX ix_payment_events_plan ON payment_events(plan_id);
CREATE INDEX ix_payment_events_subscription ON payment_events(subscription_id);
-- Keep updated_at correct for both API writes and manual SQL edits.
CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$;
DO $$
DECLARE table_name text;
BEGIN
 FOREACH table_name IN ARRAY ARRAY['users','user_profiles','artists','albums','tracks','playlists','plans'] LOOP
  EXECUTE format('CREATE TRIGGER trg_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()', table_name);
 END LOOP;
END;
$$;
