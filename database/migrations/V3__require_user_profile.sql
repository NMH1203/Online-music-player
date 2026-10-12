-- Match USERS ||--|| USER_PROFILES in database-design.md.
-- The existing profile PK/FK permits at most one profile per user.
-- This reverse FK requires a profile for every user at transaction end,
-- allowing registration to insert the user before its profile (section 8.1).
-- Existing users without profiles cause validation to fail; no data is invented.
ALTER TABLE users
 ADD CONSTRAINT fk_users_required_profile
 FOREIGN KEY (id) REFERENCES user_profiles(user_id)
 DEFERRABLE INITIALLY DEFERRED;
