-- A published track must resolve to a storage/rights record at transaction end.
-- Keep this deferred so importers can insert the track before its metadata.
CREATE FUNCTION require_published_track_audio_metadata() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
 affected_track_id uuid;
 track_status varchar(30);
BEGIN
 IF TG_TABLE_NAME = 'tracks' THEN
  affected_track_id := NEW.id;
 ELSE
  -- Moving or deleting metadata can leave its former track without audio.
  affected_track_id := OLD.track_id;
 END IF;

 -- Serialize publication and metadata removal for this track. Without the
 -- parent-row lock, concurrent transactions could each see the other's old
 -- state and commit a published track with no metadata.
 SELECT status INTO track_status FROM tracks
 WHERE id = affected_track_id FOR UPDATE;
 IF track_status = 'published' AND NOT EXISTS (
  SELECT 1 FROM track_audio_metadata m WHERE m.track_id = affected_track_id
 ) THEN
  RAISE EXCEPTION 'Published track % requires audio metadata', affected_track_id
   USING ERRCODE = '23514', CONSTRAINT = 'ck_published_track_audio_metadata';
 END IF;
 RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ck_tracks_published_audio_metadata
AFTER INSERT OR UPDATE ON tracks
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_published_track_audio_metadata();

CREATE CONSTRAINT TRIGGER ck_metadata_published_track
AFTER UPDATE OR DELETE ON track_audio_metadata
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_published_track_audio_metadata();

-- Reject legacy published rows without metadata instead of inventing object data.
DO $$
BEGIN
 IF EXISTS (
  SELECT 1 FROM tracks t
  WHERE t.status = 'published'
   AND NOT EXISTS (SELECT 1 FROM track_audio_metadata m WHERE m.track_id = t.id)
 ) THEN
  RAISE EXCEPTION 'Published tracks without audio metadata must be repaired before V8';
 END IF;
END;
$$;
