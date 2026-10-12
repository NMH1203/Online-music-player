-- Run on disposable/local test DB. All test writes roll back.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE u uuid := gen_random_uuid(); t uuid := gen_random_uuid(); a uuid := gen_random_uuid();
 p uuid := gen_random_uuid(); al uuid := gen_random_uuid();
 free_plan uuid; evt uuid := gen_random_uuid(); sub uuid; violated_constraint text;
BEGIN
 IF (SELECT count(*) FROM plans WHERE code IN ('FREE','PREMIUM')) <> 2 THEN RAISE EXCEPTION 'Missing plans'; END IF;
 IF NOT EXISTS(SELECT 1 FROM plans WHERE code='FREE' AND NOT ad_free AND max_playlists=3 AND history_days=7)
 OR NOT EXISTS(SELECT 1 FROM plans WHERE code='PREMIUM' AND ad_free AND max_playlists IS NULL AND history_days IS NULL)
 THEN RAISE EXCEPTION 'Wrong entitlements'; END IF;
 SELECT id INTO free_plan FROM plans WHERE code='FREE';
 INSERT INTO users(id,email,password_hash) VALUES(u,'sql-check@example.invalid','test-only-not-a-login-hash');
 BEGIN
  INSERT INTO users(email,password_hash) VALUES('SQL-CHECK@example.invalid','test');
  RAISE EXCEPTION 'Email uniqueness failed';
 EXCEPTION WHEN unique_violation THEN NULL; END;
 INSERT INTO user_profiles(user_id,display_name) VALUES(u,'SQL check');
 INSERT INTO subscriptions(user_id,plan_id,source) VALUES(u,free_plan,'registration') RETURNING id INTO sub;
 BEGIN
  INSERT INTO subscriptions(user_id,plan_id,source) VALUES(u,free_plan,'registration');
  RAISE EXCEPTION 'Duplicate active subscription accepted';
 EXCEPTION WHEN unique_violation THEN NULL; END;
 INSERT INTO payment_events(idempotency_key,user_id,plan_id,subscription_id,event_type,status)
 VALUES(evt,u,free_plan,sub,'mock_reset','success');
 INSERT INTO payment_events(idempotency_key,user_id,plan_id,subscription_id,event_type,status)
 VALUES(evt,u,free_plan,sub,'mock_reset','success') ON CONFLICT(idempotency_key) DO NOTHING;
 IF (SELECT count(*) FROM payment_events WHERE idempotency_key=evt) <> 1 THEN RAISE EXCEPTION 'Payment retry duplicated'; END IF;
 INSERT INTO artists(id,name,slug) VALUES(a,'SQL check','sql-check-artist');
 INSERT INTO albums(id,primary_artist_id,title,slug) VALUES(al,a,'SQL check','sql-check-album');
 IF NOT EXISTS(SELECT 1 FROM albums WHERE id=al AND status='active') THEN RAISE EXCEPTION 'New album is not active'; END IF;
 INSERT INTO tracks(id,primary_artist_id,album_id,title,duration_seconds,audio_object_key,file_size_bytes)
 VALUES(t,a,al,'SQL check',60,'checks/audio.mp3',1);
 INSERT INTO library_albums(user_id,album_id) VALUES(u,al);
 UPDATE albums SET status='archived' WHERE id=al;
 IF NOT EXISTS(SELECT 1 FROM albums WHERE id=al AND status='archived')
 OR NOT EXISTS(SELECT 1 FROM tracks WHERE id=t AND album_id=al)
 OR NOT EXISTS(SELECT 1 FROM library_albums WHERE user_id=u AND album_id=al)
 THEN RAISE EXCEPTION 'Album archival lost the album or its references'; END IF;
 UPDATE albums SET status='active' WHERE id=al;
 BEGIN
  UPDATE albums SET status='invalid' WHERE id=al;
  RAISE EXCEPTION 'Invalid album status was accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_albums_status' THEN RAISE; END IF;
 END;
 BEGIN
  UPDATE albums SET status=NULL WHERE id=al;
  RAISE EXCEPTION 'NULL album status was accepted';
 EXCEPTION WHEN not_null_violation THEN NULL; END;
 DELETE FROM albums WHERE id=al;
 IF EXISTS(SELECT 1 FROM library_albums WHERE album_id=al)
 OR NOT EXISTS(SELECT 1 FROM tracks WHERE id=t AND album_id IS NULL)
 THEN RAISE EXCEPTION 'Album hard-delete policy changed'; END IF;
 INSERT INTO playlists(id,user_id,name) VALUES(p,u,'SQL check');
 INSERT INTO playlist_tracks(playlist_id,track_id,position) VALUES(p,t,0);
 INSERT INTO library_tracks(user_id,track_id) VALUES(u,t) ON CONFLICT DO NOTHING;
 INSERT INTO library_tracks(user_id,track_id) VALUES(u,t) ON CONFLICT DO NOTHING;
 IF (SELECT count(*) FROM library_tracks WHERE user_id=u) <> 1 THEN RAISE EXCEPTION 'Favorite retry duplicated'; END IF;
 INSERT INTO listening_history(client_event_id,user_id,track_id,source_playlist_id,source_context,started_at,listened_seconds,counted_as_play)
 VALUES(evt,u,t,p,'playlist',now(),30,true) ON CONFLICT(client_event_id) DO NOTHING;
 INSERT INTO listening_history(client_event_id,user_id,track_id,source_context,started_at,listened_seconds,counted_as_play)
 VALUES(evt,u,t,'playlist',now(),30,true) ON CONFLICT(client_event_id) DO NOTHING;
 IF (SELECT count(*) FROM listening_history WHERE client_event_id=evt) <> 1 THEN RAISE EXCEPTION 'History retry duplicated'; END IF;
 DELETE FROM playlists WHERE id=p;
 IF EXISTS(SELECT 1 FROM playlist_tracks WHERE playlist_id=p) THEN RAISE EXCEPTION 'Playlist cascade failed'; END IF;
 IF NOT EXISTS(SELECT 1 FROM listening_history WHERE client_event_id=evt AND source_playlist_id IS NULL) THEN RAISE EXCEPTION 'History source must survive playlist deletion'; END IF;
 BEGIN
  DELETE FROM tracks WHERE id=t;
  RAISE EXCEPTION 'Track with history was deleted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 UPDATE subscriptions SET status='cancelled', ended_at=now() WHERE id=sub;
 INSERT INTO subscriptions(user_id,plan_id,source) VALUES(u,free_plan,'mock_reset');
 SET CONSTRAINTS ALL IMMEDIATE;
 RAISE NOTICE 'PASS: entitlements, case-insensitive email, active subscription, idempotency, album archival, deletion policies';
END;
$$;
ROLLBACK;
