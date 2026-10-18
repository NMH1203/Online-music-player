-- Run after V8. All writes are rolled back.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
 artist_id uuid := gen_random_uuid();
 published_track_id uuid := gen_random_uuid();
 other_track_id uuid := gen_random_uuid();
 missing_track_id uuid := gen_random_uuid();
 violated_constraint text;
BEGIN
 INSERT INTO artists(id, name, slug)
 VALUES(artist_id, 'Published audio check', 'published-audio-check-' || artist_id);

 -- A published row without metadata must fail at the transaction boundary.
 BEGIN
  INSERT INTO tracks(id, primary_artist_id, title, duration_seconds,
   audio_object_key, file_size_bytes, status, published_at)
  VALUES(missing_track_id, artist_id, 'Missing audio', 1,
   'checks/' || missing_track_id || '.mp3', 100, 'published', now());
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE EXCEPTION 'Published track without metadata was accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_published_track_audio_metadata' THEN RAISE; END IF;
 END;

 -- The importer may write the published track first and metadata second.
 INSERT INTO tracks(id, primary_artist_id, title, duration_seconds,
  audio_object_key, file_size_bytes, status, published_at)
 VALUES(published_track_id, artist_id, 'Verified audio', 1,
  'checks/' || published_track_id || '.mp3', 100, 'published', now());
 INSERT INTO track_audio_metadata(track_id, bucket_name, sha256, etag,
  license_code, license_url, source_url, attribution_text, verified_at)
 VALUES(published_track_id, 'music-audio', repeat('a', 64), 'test-etag', 'CC0-1.0',
  'https://creativecommons.org/publicdomain/zero/1.0/',
  'https://example.invalid/audio', 'Test attribution', now());
 SET CONSTRAINTS ALL IMMEDIATE;
 SET CONSTRAINTS ALL DEFERRED;

 BEGIN
  DELETE FROM track_audio_metadata WHERE track_id = published_track_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE EXCEPTION 'Deleting published audio metadata was accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_published_track_audio_metadata' THEN RAISE; END IF;
 END;

 INSERT INTO tracks(id, primary_artist_id, title, duration_seconds,
  audio_object_key, file_size_bytes)
 VALUES(other_track_id, artist_id, 'Other draft', 1,
  'checks/' || other_track_id || '.mp3', 100);
 BEGIN
  UPDATE tracks SET status = 'published', published_at = now()
  WHERE id = other_track_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE EXCEPTION 'Publishing a draft without metadata was accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_published_track_audio_metadata' THEN RAISE; END IF;
 END;
 BEGIN
  UPDATE track_audio_metadata SET track_id = other_track_id
  WHERE track_id = published_track_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE EXCEPTION 'Moving published audio metadata was accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_published_track_audio_metadata' THEN RAISE; END IF;
 END;

 -- Replacement in a single transaction is valid.
 DELETE FROM track_audio_metadata WHERE track_id = published_track_id;
 INSERT INTO track_audio_metadata(track_id, bucket_name, sha256, etag,
  license_code, license_url, source_url, attribution_text, verified_at)
 VALUES(published_track_id, 'music-audio', repeat('a', 64), 'replacement-etag', 'CC0-1.0',
  'https://creativecommons.org/publicdomain/zero/1.0/',
  'https://example.invalid/audio', 'Test attribution', now());
 SET CONSTRAINTS ALL IMMEDIATE;
 SET CONSTRAINTS ALL DEFERRED;

 DELETE FROM tracks WHERE id = published_track_id;
 SET CONSTRAINTS ALL IMMEDIATE;
 RAISE NOTICE 'PASS: published audio metadata required; deletion, movement and replacement checked';
END;
$$;
ROLLBACK;
