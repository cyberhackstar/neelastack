-- Neelastack platform - client dashboard: engagements, milestones, files

CREATE TABLE engagements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID NOT NULL REFERENCES users(id),
    inquiry_id      UUID REFERENCES inquiries(id),
    title           VARCHAR(160) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ONBOARDING',
    start_date      DATE,
    target_end_date DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_engagements_client ON engagements (client_id);

CREATE TABLE milestones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id   UUID NOT NULL REFERENCES engagements(id) ON DELETE CASCADE,
    title           VARCHAR(160) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_date        DATE,
    display_order   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_milestones_engagement ON milestones (engagement_id, display_order);

CREATE TABLE project_files (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id           UUID NOT NULL REFERENCES engagements(id) ON DELETE CASCADE,
    uploaded_by             UUID NOT NULL REFERENCES users(id),
    file_name               VARCHAR(255) NOT NULL,
    file_url                VARCHAR(500) NOT NULL,
    cloudinary_public_id    VARCHAR(255) NOT NULL,
    file_type               VARCHAR(100),
    file_size_bytes         BIGINT,
    created_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_files_engagement ON project_files (engagement_id);
