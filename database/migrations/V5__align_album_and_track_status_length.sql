-- Use the same status length for albums and tracks.
-- Existing CHECK constraints keep every allowed value within 30 characters.
ALTER TABLE albums ALTER COLUMN status TYPE varchar(30);
ALTER TABLE tracks ALTER COLUMN status TYPE varchar(30);
