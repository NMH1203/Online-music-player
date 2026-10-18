-- Run after V7 on a local/test DB. All test writes roll back.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
 artist_id uuid := gen_random_uuid();
 audio_track_id uuid := gen_random_uuid();
 violated_constraint text;
BEGIN
 INSERT INTO artists(id,name,slug)
 VALUES(artist_id,'Audio metadata check','audio-check-' || artist_id);
 INSERT INTO tracks(id,primary_artist_id,title,duration_seconds,audio_object_key,file_size_bytes)
 VALUES(audio_track_id,artist_id,'Audio metadata check',1,'checks/' || audio_track_id || '.wav',44);
 INSERT INTO track_audio_metadata(
  track_id,bucket_name,sha256,etag,license_code,license_url,source_url,attribution_text,verified_at
 ) VALUES (
  audio_track_id,'music-audio',repeat('a',64),'test-etag','CC0-1.0',
  'https://creativecommons.org/publicdomain/zero/1.0/',
  'https://example.invalid/source','Test-only source attribution',now()
 );
 IF NOT EXISTS (
  SELECT 1 FROM tracks t JOIN track_audio_metadata m ON m.track_id=t.id
  WHERE t.id=audio_track_id AND m.bucket_name='music-audio'
   AND t.audio_object_key='checks/' || audio_track_id || '.wav'
 ) THEN RAISE EXCEPTION 'Audio object mapping failed'; END IF;

 BEGIN
  UPDATE track_audio_metadata SET sha256='not-a-sha256' WHERE track_id=audio_track_id;
  RAISE EXCEPTION 'Invalid SHA-256 accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_track_audio_sha256' THEN RAISE; END IF;
 END;
 BEGIN
  UPDATE track_audio_metadata SET bucket_name='http://localhost:9000/music' WHERE track_id=audio_track_id;
  RAISE EXCEPTION 'Endpoint accepted as bucket name';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_track_audio_bucket' THEN RAISE; END IF;
 END;
 BEGIN
  UPDATE track_audio_metadata SET attribution_text=' ' WHERE track_id=audio_track_id;
  RAISE EXCEPTION 'Empty rights attribution accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_track_audio_attribution' THEN RAISE; END IF;
 END;
 BEGIN
  UPDATE track_audio_metadata SET license_url='file:///license.txt' WHERE track_id=audio_track_id;
  RAISE EXCEPTION 'Non-web license URL accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_track_audio_license_url' THEN RAISE; END IF;
 END;
 BEGIN
  INSERT INTO track_audio_metadata SELECT * FROM track_audio_metadata WHERE track_id=audio_track_id;
  RAISE EXCEPTION 'Duplicate metadata record accepted';
 EXCEPTION WHEN unique_violation THEN NULL;
 END;
 BEGIN
  UPDATE track_audio_metadata SET track_id=gen_random_uuid() WHERE track_id=audio_track_id;
  RAISE EXCEPTION 'Orphan metadata record accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 DELETE FROM tracks WHERE id=audio_track_id;
 IF EXISTS(SELECT 1 FROM track_audio_metadata WHERE track_id=audio_track_id)
 THEN RAISE EXCEPTION 'Track deletion left orphan audio metadata'; END IF;
 RAISE NOTICE 'PASS: storage mapping, SHA-256, bucket, rights evidence, one-to-one metadata and cascade';
END;
$$;
ROLLBACK;
