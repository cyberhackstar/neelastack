-- Enterprise security hardening (Section 2): audit trail. Immutability is enforced at
-- the database level, not just via Spring Security annotations on the controller --
-- a REVOKE here holds even if a future authorization misconfiguration would otherwise
-- let ROLE_ADMIN write to this table. The application's DB role is read from
-- current_user at migration time so this works in any environment without hardcoding
-- the role name.
--
-- actor_email/actor_role are a snapshot at the time of the action (not a live FK to
-- users), since a user could later be renamed or deleted and the audit trail must still
-- say who did what, as recorded at the time.

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    actor_email VARCHAR(180) NOT NULL,
    actor_role VARCHAR(20),
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id VARCHAR(80),
    request_id VARCHAR(60),
    ip VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id, created_at);
CREATE INDEX idx_audit_logs_action ON audit_logs (action, created_at);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);

-- No admin role -- including ROLE_ADMIN -- gets UPDATE or DELETE on this table, at the
-- database level, from any application code path. Only INSERT (writes new entries) and
-- SELECT (the admin audit-log viewer) are possible.
REVOKE UPDATE, DELETE ON audit_logs FROM PUBLIC;
DO $$
BEGIN
    EXECUTE format('REVOKE UPDATE, DELETE ON audit_logs FROM %I', current_user);
EXCEPTION WHEN OTHERS THEN
    -- Some managed Postgres providers don't allow a role to revoke its own default
    -- privileges this way; the trigger below is the real backstop either way.
    NULL;
END $$;

CREATE OR REPLACE FUNCTION reject_audit_log_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_no_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();

CREATE TRIGGER trg_audit_logs_no_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();
