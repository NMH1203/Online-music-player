-- One verified storage/rights record per audio file. The object key, MIME,
-- size, duration and bitrate already live on tracks; do not duplicate them.
-- Existing catalog drafts remain valid until their audio is imported.
CREATE TABLE track_audio_metadata (
 track_id uuid PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
 bucket_name varchar(63) NOT NULL,
 sha256 varchar(64) NOT NULL,
 etag varchar(128) NOT NULL,
 license_code varchar(64) NOT NULL,
 license_url text NOT NULL,
 source_url text NOT NULL,
 attribution_text text NOT NULL,
 verified_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT ck_track_audio_bucket CHECK (
  bucket_name ~ '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$'
  AND position('..' IN bucket_name) = 0
  AND position('.-' IN bucket_name) = 0
  AND position('-.' IN bucket_name) = 0
  AND bucket_name !~ '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'
 ),
 CONSTRAINT ck_track_audio_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
 CONSTRAINT ck_track_audio_etag CHECK (length(btrim(etag)) > 0),
 CONSTRAINT ck_track_audio_license_code CHECK (length(btrim(license_code)) > 0),
 CONSTRAINT ck_track_audio_license_url CHECK (license_url ~ '^https?://[^[:space:]]+$'),
 CONSTRAINT ck_track_audio_source_url CHECK (source_url ~ '^https?://[^[:space:]]+$'),
 CONSTRAINT ck_track_audio_attribution CHECK (length(btrim(attribution_text)) > 0)
);

CREATE TRIGGER trg_updated_at BEFORE UPDATE ON track_audio_metadata
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE track_audio_metadata IS
 'Audio object integrity and rights provenance. Imports verify bytes and source licenses before publishing; these columns do not themselves prove permission.';
COMMENT ON COLUMN track_audio_metadata.bucket_name IS
 'Resolve with tracks.audio_object_key using the configured S3 endpoint. Never store credentials or expiring presigned URLs.';
COMMENT ON COLUMN track_audio_metadata.sha256 IS
 'Lowercase SHA-256 of the uploaded bytes. S3 ETag is separate and is not assumed to be a file hash.';
COMMENT ON COLUMN track_audio_metadata.license_code IS
 'License identifier; the demo importer restricts its accepted licenses independently of this extensible catalog field.';
COMMENT ON COLUMN track_audio_metadata.verified_at IS
 'Time the importer last verified the object and the recorded source/license evidence.';
