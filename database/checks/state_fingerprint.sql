-- T31: deterministic fingerprint of the database. Run it before a backup and
-- after the restore (or on two machines): the outputs must be identical.
-- Per table: row count + order-independent md5 of every row.
SELECT 'flyway' AS item,
       CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 'not managed by flyway'
            ELSE (xpath('/row/v/text()', query_to_xml(
              'SELECT string_agg(version || '':'' || success::text, '','' ORDER BY installed_rank) AS v
               FROM flyway_schema_history WHERE version IS NOT NULL', false, true, '')))[1]::text
       END AS value
UNION ALL
SELECT t.table_name,
       (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', t.table_name), false, true, '')))[1]::text
       || ' rows / ' ||
       (xpath('/row/h/text()', query_to_xml(format(
         'SELECT coalesce(md5(string_agg(md5(x::text), '''' ORDER BY md5(x::text))), ''empty'') AS h FROM %I x',
         t.table_name), false, true, '')))[1]::text
FROM information_schema.tables t
WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE' AND t.table_name <> 'flyway_schema_history'
ORDER BY 1;
