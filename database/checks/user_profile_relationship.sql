-- Verify the mandatory one-to-one relationship from the ER diagram.
-- Run after V3. All test writes roll back.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
 u uuid := gen_random_uuid();
 other_user uuid := gen_random_uuid();
 violated_constraint text;
BEGIN
 -- User-first registration succeeds when both records share a transaction.
 INSERT INTO users(id, email, password_hash)
 VALUES(u, u::text || '@profile-check.invalid', 'test-only-not-a-login-hash');
 INSERT INTO user_profiles(user_id, display_name) VALUES(u, 'Profile check');
 INSERT INTO subscriptions(user_id, plan_id, source)
 SELECT u, id, 'registration' FROM plans WHERE code = 'FREE';
 SET CONSTRAINTS fk_users_required_profile IMMEDIATE;
 SET CONSTRAINTS fk_users_required_profile DEFERRED;

 -- Force the transaction-end check: an unpaired user cannot be committed.
 BEGIN
  INSERT INTO users(id, email, password_hash)
  VALUES(other_user, other_user::text || '@profile-check.invalid', 'test-only');
  SET CONSTRAINTS fk_users_required_profile IMMEDIATE;
  RAISE EXCEPTION 'User without profile was accepted';
 EXCEPTION WHEN foreign_key_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'fk_users_required_profile' THEN RAISE; END IF;
 END;

 -- Deleting or moving a profile must not leave its original user unpaired.
 BEGIN
  DELETE FROM user_profiles WHERE user_id = u;
  SET CONSTRAINTS fk_users_required_profile IMMEDIATE;
  RAISE EXCEPTION 'Deleting a required profile was accepted';
 EXCEPTION WHEN foreign_key_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'fk_users_required_profile' THEN RAISE; END IF;
 END;
 BEGIN
  INSERT INTO users(id, email, password_hash)
  VALUES(other_user, other_user::text || '@profile-check.invalid', 'test-only');
  UPDATE user_profiles SET user_id = other_user WHERE user_id = u;
  SET CONSTRAINTS fk_users_required_profile IMMEDIATE;
  RAISE EXCEPTION 'Moving a required profile was accepted';
 EXCEPTION WHEN foreign_key_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'fk_users_required_profile' THEN RAISE; END IF;
 END;

 -- Preserve the original constraints in the opposite direction.
 BEGIN
  INSERT INTO user_profiles(user_id, display_name) VALUES(u, 'Duplicate');
  RAISE EXCEPTION 'Duplicate profile was accepted';
 EXCEPTION WHEN unique_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'user_profiles_pkey' THEN RAISE; END IF;
 END;
 BEGIN
  INSERT INTO user_profiles(user_id, display_name) VALUES(other_user, 'Orphan');
  RAISE EXCEPTION 'Profile without user was accepted';
 EXCEPTION WHEN foreign_key_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'user_profiles_user_id_fkey' THEN RAISE; END IF;
 END;

 -- A profile may be replaced within one transaction.
 DELETE FROM user_profiles WHERE user_id = u;
 INSERT INTO user_profiles(user_id, display_name) VALUES(u, 'Replacement');
 SET CONSTRAINTS fk_users_required_profile IMMEDIATE;

 -- Deleting an otherwise unreferenced user still cascades to its profile.
 DELETE FROM subscriptions WHERE user_id = u;
 DELETE FROM users WHERE id = u;
 IF EXISTS(SELECT 1 FROM user_profiles WHERE user_id = u) THEN
  RAISE EXCEPTION 'User deletion did not cascade to profile';
 END IF;
 SET CONSTRAINTS ALL IMMEDIATE;
 RAISE NOTICE 'PASS: mandatory user/profile pair, uniqueness, replacement and cascade deletion';
END;
$$;
ROLLBACK;
