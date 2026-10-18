-- Run after V6. All writes roll back.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
 u uuid := gen_random_uuid();
 v uuid := gen_random_uuid();
 free_plan uuid;
 original_subscription uuid;
 violated_constraint text;
BEGIN
 SELECT id INTO STRICT free_plan FROM plans WHERE code = 'FREE';
 INSERT INTO users(id, email, password_hash)
 VALUES(u, u::text || '@subscription-check.invalid', 'test-only');
 INSERT INTO user_profiles(user_id, display_name) VALUES(u, 'Subscription check');
 BEGIN
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE EXCEPTION 'User without active subscription was accepted';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
  IF violated_constraint <> 'ck_users_required_active_subscription' THEN RAISE; END IF;
 END;
 INSERT INTO subscriptions(user_id, plan_id, source)
 VALUES(u, free_plan, 'registration') RETURNING id INTO original_subscription;
 SET CONSTRAINTS ALL IMMEDIATE;
 SET CONSTRAINTS ALL DEFERRED;

 INSERT INTO users(id, email, password_hash)
 VALUES(v, v::text || '@subscription-check.invalid', 'test-only');
 INSERT INTO user_profiles(user_id, display_name) VALUES(v, 'Other user');
 INSERT INTO subscriptions(user_id, plan_id, source) VALUES(v, free_plan, 'registration');
 SET CONSTRAINTS ALL IMMEDIATE;
 SET CONSTRAINTS ALL DEFERRED;

 BEGIN
  DELETE FROM subscriptions WHERE id = original_subscription;
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE EXCEPTION 'Deleting the only active subscription was accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
  UPDATE subscriptions SET status = 'cancelled', ended_at = now()
  WHERE id = original_subscription;
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE EXCEPTION 'Cancelling without replacement was accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
  UPDATE subscriptions SET user_id = v WHERE id = original_subscription;
  RAISE EXCEPTION 'Two active subscriptions were accepted';
 EXCEPTION WHEN unique_violation THEN NULL; END;
 BEGIN
  UPDATE subscriptions SET status = 'cancelled', ended_at = now() WHERE user_id = v;
  UPDATE subscriptions SET user_id = v WHERE id = original_subscription;
  SET CONSTRAINTS ALL IMMEDIATE;
  RAISE EXCEPTION 'Moving a subscription left its original user without a plan';
 EXCEPTION WHEN check_violation THEN NULL; END;

 UPDATE subscriptions SET status = 'cancelled', ended_at = now()
 WHERE id = original_subscription;
 INSERT INTO subscriptions(user_id, plan_id, source) VALUES(u, free_plan, 'mock_reset');
 SET CONSTRAINTS ALL IMMEDIATE;
 SET CONSTRAINTS ALL DEFERRED;
 -- Removing a user and all of its references in one transaction is valid.
 DELETE FROM subscriptions WHERE user_id = v;
 DELETE FROM users WHERE id = v;
 SET CONSTRAINTS ALL IMMEDIATE;
 RAISE NOTICE 'PASS: required active subscription, deletion, cancellation, reassignment and replacement';
END;
$$;
ROLLBACK;
