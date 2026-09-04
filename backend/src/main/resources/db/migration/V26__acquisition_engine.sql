-- Client Acquisition & High-Ticket Conversion Engine
--
-- Adds the schema needed for four features, all additive/nullable — no existing
-- row, query, or feature is changed by this migration:
--   1. Instant Architecture Risk Score preview / gated diagnostic (no schema of its
--      own: it reuses `inquiries`, tagged via existing `source`/`intent` columns).
--   2. Tier-1 instant-booking redirect (no schema: purely a response-shape/config
--      concern, computed from the existing `inquiries.lead_tier` column).
--   3. Contextual case-study injection on proposals — needs a way to (a) tag a
--      quotation with the service category it was priced for, and (b) tag a
--      published case study with the categories/metrics it can back up.
--   4. Post-invoice testimonial loop — needs a table to track one-time, tokenized
--      testimonial requests, and a link from an engagement to the public case
--      study it eventually became (if any).

-- --- (3) Quotation -> case-study matching -------------------------------------

ALTER TABLE quotations ADD COLUMN IF NOT EXISTS service_category VARCHAR(60);

COMMENT ON COLUMN quotations.service_category IS
    'Admin-set (or auto-inferred from the source inquiry''s project type) service '
    'key used to pick a relevant published case study to show alongside this '
    'quotation. Null is valid and common -- it just means no case study is shown.';

-- --- (3) Case study tagging: real, admin-entered data only --------------------
-- Mirrors the existing `project_tech_stack` element-collection pattern. Both
-- tables start empty for every existing project -- an admin opts a case study
-- into proposal-matching by tagging it, and into having a "why this matters"
-- metric shown by writing one. Nothing here is backfilled or inferred, in
-- keeping with this codebase's house rule (see SchemaBuilderService /
-- ExecutiveReportPdfService) that unverifiable figures are never manufactured.

CREATE TABLE IF NOT EXISTS project_service_categories (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    category   VARCHAR(60) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_project_service_categories_category
    ON project_service_categories (category);

CREATE INDEX IF NOT EXISTS idx_project_service_categories_project
    ON project_service_categories (project_id);

CREATE TABLE IF NOT EXISTS project_key_metrics (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    metric     VARCHAR(200) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_project_key_metrics_project
    ON project_key_metrics (project_id);

-- --- (4) Engagement -> published case study (optional, set once a client's ----
--         project is written up) ----------------------------------------------

ALTER TABLE engagements ADD COLUMN IF NOT EXISTS project_id UUID REFERENCES projects(id);

COMMENT ON COLUMN engagements.project_id IS
    'Set by an admin once this engagement has been written up as a public case '
    'study. Used only to route a post-payment testimonial request to the right '
    'project''s reviews; null means "no public case study yet", not an error.';

-- --- (4) Reviews: allow client-submitted testimonials not yet linked to a --------
--         published case study, and record how each review arrived -------------

ALTER TABLE reviews ALTER COLUMN project_id DROP NOT NULL;

ALTER TABLE reviews ADD COLUMN IF NOT EXISTS video_url VARCHAR(300);

ALTER TABLE reviews ADD COLUMN IF NOT EXISTS submitted_via VARCHAR(20) NOT NULL DEFAULT 'ADMIN';

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS chk_reviews_submitted_via;
ALTER TABLE reviews ADD CONSTRAINT chk_reviews_submitted_via
    CHECK (submitted_via IN ('ADMIN', 'CLIENT_TESTIMONIAL'));

COMMENT ON COLUMN reviews.project_id IS
    'Nullable: a client-submitted testimonial (submitted_via = CLIENT_TESTIMONIAL) '
    'may arrive before the engagement has a published case study to attach to. An '
    'admin assigns a project (or leaves it unattached) during moderation; either '
    'way the review stays unpublished until an admin explicitly publishes it.';

-- --- (4) Testimonial requests ---------------------------------------------------

CREATE TABLE IF NOT EXISTS testimonial_requests (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id     UUID NOT NULL REFERENCES invoices(id),
    engagement_id  UUID NOT NULL REFERENCES engagements(id),
    project_id     UUID REFERENCES projects(id),
    client_email   VARCHAR(180) NOT NULL,
    client_name    VARCHAR(120) NOT NULL,
    token          VARCHAR(36) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at   TIMESTAMP NOT NULL DEFAULT now(),
    responded_at   TIMESTAMP,
    review_id      UUID REFERENCES reviews(id),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_testimonial_request_status
        CHECK (status IN ('PENDING', 'SUBMITTED', 'DECLINED', 'EXPIRED'))
);

-- One active request per invoice — the PAID-transition hook is idempotent and
-- must never queue a second request for the same payment event.
CREATE UNIQUE INDEX IF NOT EXISTS idx_testimonial_requests_invoice
    ON testimonial_requests (invoice_id);

-- Token lookups happen on every public GET/POST to the testimonial link, and
-- token consumption (PENDING -> SUBMITTED) must be atomic -- see
-- TestimonialService#submit, which does a conditional UPDATE ... WHERE status =
-- 'PENDING' rather than a read-then-write, so a resubmitted/replayed link can
-- never create two reviews from one request.
CREATE UNIQUE INDEX IF NOT EXISTS idx_testimonial_requests_token
    ON testimonial_requests (token);
