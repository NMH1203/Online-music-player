-- Together with the partial unique index, require exactly one active
-- subscription per user at transaction end. Registration and plan changes
-- must insert the replacement subscription in the same transaction.
-- Validate existing data rather than silently invent subscriptions.
DO $$
BEGIN
 IF EXISTS (
  SELECT 1 FROM users u WHERE NOT EXISTS (
   SELECT 1 FROM subscriptions s WHERE s.user_id = u.id AND s.status = 'active'
  )
 ) THEN
  RAISE EXCEPTION 'Existing users must have an active subscription before applying V6';
 END IF;
END;
$$;

CREATE FUNCTION require_active_subscription() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
 affected_users uuid[];
 affected_user uuid;
BEGIN
 IF TG_TABLE_NAME = 'users' THEN
  affected_users := ARRAY[NEW.id];
 ELSIF TG_OP = 'INSERT' THEN
  affected_users := ARRAY[NEW.user_id];
 ELSIF TG_OP = 'DELETE' THEN
  affected_users := ARRAY[OLD.user_id];
 ELSE
  affected_users := ARRAY[OLD.user_id, NEW.user_id];
 END IF;

 -- Lock affected users in a consistent order. A deleted user needs no plan.
 FOR affected_user IN SELECT DISTINCT unnest(affected_users) ORDER BY 1 LOOP
  PERFORM 1 FROM users WHERE id = affected_user FOR UPDATE;
  IF FOUND AND NOT EXISTS (
   SELECT 1 FROM subscriptions WHERE user_id = affected_user AND status = 'active'
  ) THEN
   RAISE EXCEPTION 'User % must have one active subscription', affected_user
    USING ERRCODE = '23514', CONSTRAINT = 'ck_users_required_active_subscription';
  END IF;
 END LOOP;
 RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ck_users_required_active_subscription
AFTER INSERT OR UPDATE ON users
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_active_subscription();

CREATE CONSTRAINT TRIGGER ck_subscriptions_required_active_subscription
AFTER INSERT OR UPDATE OR DELETE ON subscriptions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_active_subscription();
