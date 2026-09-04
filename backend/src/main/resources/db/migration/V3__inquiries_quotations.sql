-- Neelastack platform - lead capture and quotation workflow

CREATE TABLE inquiries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(180) NOT NULL,
    phone           VARCHAR(20),
    company         VARCHAR(120),
    project_type    VARCHAR(80),
    budget_range    VARCHAR(60),
    message         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW',
    source          VARCHAR(60),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_inquiries_status ON inquiries (status);
CREATE INDEX idx_inquiries_created_at ON inquiries (created_at DESC);

CREATE TABLE quotations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inquiry_id      UUID NOT NULL REFERENCES inquiries(id) ON DELETE CASCADE,
    title           VARCHAR(160) NOT NULL,
    scope_summary   TEXT,
    total_amount    NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(8) NOT NULL DEFAULT 'INR',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    valid_until     DATE,
    notes           TEXT,
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_quotations_inquiry ON quotations (inquiry_id);

CREATE TABLE quotation_line_items (
    quotation_id    UUID NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
    description     VARCHAR(300) NOT NULL,
    amount          NUMERIC(12,2) NOT NULL
);
