-- Client Workspace Section 14: turns scope creep into a documented, quotable revenue item
-- instead of an unrecorded ask. A client submits a change request; staff attach an estimated
-- cost/timeline impact (the "quote"); the client then accepts or declines it, same client-only
-- decision pattern as milestone_approval.
CREATE TABLE change_request (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id           UUID NOT NULL REFERENCES engagements(id) ON DELETE CASCADE,
    requested_by            UUID NOT NULL REFERENCES users(id),
    title                   VARCHAR(200) NOT NULL,
    description             TEXT NOT NULL,
    priority                VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    status                  VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    estimated_cost          NUMERIC(12, 2),
    estimated_cost_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    estimated_timeline_days INTEGER,
    attachment_file_id      UUID REFERENCES project_files(id),
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_change_request_engagement ON change_request (engagement_id, created_at DESC);
