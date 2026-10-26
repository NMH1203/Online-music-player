-- T25 audit: every check must return 0 rows. Runs in a rolled-back transaction.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
  rec record; bad int := 0;
BEGIN
  FOR rec IN
    SELECT * FROM (VALUES
      ('user without profile',
       'SELECT count(*) FROM users u WHERE NOT EXISTS (SELECT 1 FROM user_profiles p WHERE p.user_id=u.id)'),
      ('user without exactly one active subscription',
       'SELECT count(*) FROM users u WHERE (SELECT count(*) FROM subscriptions s WHERE s.user_id=u.id AND s.status=''active'') <> 1'),
      ('active subscription already past expires_at',
       'SELECT count(*) FROM subscriptions WHERE status=''active'' AND expires_at < now()'),
      ('published track without audio metadata',
       'SELECT count(*) FROM tracks t WHERE t.status=''published'' AND NOT EXISTS (SELECT 1 FROM track_audio_metadata m WHERE m.track_id=t.id)'),
      ('published track without genre',
       'SELECT count(*) FROM tracks t WHERE t.status=''published'' AND NOT EXISTS (SELECT 1 FROM track_genres g WHERE g.track_id=t.id)'),
      ('published track in archived album',
       'SELECT count(*) FROM tracks t JOIN albums a ON a.id=t.album_id WHERE t.status=''published'' AND a.status=''archived'''),
      ('favorite track not published',
       'SELECT count(*) FROM library_tracks l JOIN tracks t ON t.id=l.track_id WHERE t.status<>''published'''),
      ('playlist track not published',
       'SELECT count(*) FROM playlist_tracks pt JOIN tracks t ON t.id=pt.track_id WHERE t.status<>''published'''),
      ('playlist positions not contiguous from 0',
       'SELECT count(*) FROM (SELECT playlist_id FROM playlist_tracks GROUP BY playlist_id HAVING min(position)<>0 OR max(position)<>count(*)-1) x'),
      ('Free user above playlist quota',
       'SELECT count(*) FROM (SELECT p.user_id FROM playlists p JOIN subscriptions s ON s.user_id=p.user_id AND s.status=''active'' JOIN plans pl ON pl.id=s.plan_id WHERE pl.max_playlists IS NOT NULL GROUP BY p.user_id, pl.max_playlists HAVING count(*)>pl.max_playlists) x'),
      ('history event ended before it started',
       'SELECT count(*) FROM listening_history WHERE ended_at < started_at'),
      ('duplicate played event (same user, track, started_at)',
       'SELECT count(*) FROM (SELECT 1 FROM listening_history WHERE counted_as_play GROUP BY user_id, track_id, started_at HAVING count(*)>1) x'),
      ('session replaced but not revoked',
       'SELECT count(*) FROM user_sessions WHERE replaced_by_session_id IS NOT NULL AND revoked_at IS NULL'),
      ('payment event amount not zero',
       'SELECT count(*) FROM payment_events WHERE amount <> 0')
    ) AS c(name, q)
  LOOP
    EXECUTE rec.q INTO bad;
    IF bad > 0 THEN RAISE WARNING 'FAIL % : % row(s)', rec.name, bad; ELSE RAISE NOTICE 'PASS %', rec.name; END IF;
  END LOOP;
END $$;
ROLLBACK;
