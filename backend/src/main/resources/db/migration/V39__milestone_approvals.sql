-- Client Workspace Section 13: a documented approval trail ("Approve milestone" /
-- "Request changes") instead of an unrecorded "looks good" over WhatsApp. A milestone's
-- `status` only ever holds its current state; this table is the append-only history of
-- every approve/request-changes decision made against it, including the client's comment
-- when they ask for changes.
CREATE TABLE milestone_approval (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id    UUID NOT NULL REFERENCES milestones(id) ON DELETE CASCADE,
    action          VARCHAR(20) NOT NULL,
    comment         TEXT,
    actor_id        UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_milestone_approval_milestone ON milestone_approval (milestone_id, created_at DESC);
