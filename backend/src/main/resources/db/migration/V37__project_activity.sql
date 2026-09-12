-- Client Workspace: a client-safe project activity feed (Section 9 of the review), kept
-- deliberately separate from `audit_logs`. audit_logs is an internal, append-only security
-- record covering every high-risk mutation across the whole app (see AuditLogService); this
-- table is a much smaller, human-readable feed of project milestones worth showing a client
-- ("Client uploaded requirements.pdf", "Milestone 2 marked complete") and is written to
-- explicitly by the handful of call sites that represent real project progress, not by
-- everything AuditLogService already covers.
CREATE TABLE project_activity (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id   UUID NOT NULL REFERENCES engagements(id) ON DELETE CASCADE,
    -- Nullable: some entries (e.g. a webhook-confirmed payment) have no human actor in the
    -- request. ON DELETE SET NULL rather than CASCADE so the timeline entry itself -- and its
    -- own actor_name/actor_role snapshot below -- survives the acting user being removed later.
    actor_id        UUID REFERENCES users(id) ON DELETE SET NULL,
    -- Snapshot of the actor's display name/role at the time of the event, same rationale as
    -- AuditLog.actorEmail/actorRole: the feed should still read correctly even if the user's
    -- name changes or the account is later deleted.
    actor_name      VARCHAR(120),
    actor_role      VARCHAR(20),
    activity_type   VARCHAR(40) NOT NULL,
    summary         TEXT NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_activity_engagement ON project_activity (engagement_id, created_at DESC);
