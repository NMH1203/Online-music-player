-- Support album archival from database-design.md section 9.
-- Existing albums stay active; archiving preserves tracks and favorites.
-- Catalog queries must filter albums.status = 'active' to hide archived albums.
ALTER TABLE albums
 ADD COLUMN status varchar(32) NOT NULL DEFAULT 'active',
 ADD CONSTRAINT ck_albums_status CHECK (status IN ('active', 'archived'));
