-- Adds project-estimator intake fields, lead-scoring columns, and lightweight UTM
-- attribution to inquiries (master prompt sections 21/47/49).
--
-- Backward compatible: every new column is nullable or carries a default, so existing
-- rows (submitted through the plain /contact form before this migration) remain valid.
-- Existing rows get intent='GENERAL', lead_score=0, lead_tier='NURTURE' by default —
-- their score is NOT retroactively computed, since that would require re-deriving
-- scoring inputs (budget/timeline/existing-system/etc.) that were never captured for them.

ALTER TABLE inquiries ADD COLUMN intent VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE inquiries ADD COLUMN existing_system TEXT;
ALTER TABLE inquiries ADD COLUMN scope_details TEXT;
ALTER TABLE inquiries ADD COLUMN users_scale VARCHAR(60);
ALTER TABLE inquiries ADD COLUMN integrations TEXT;
ALTER TABLE inquiries ADD COLUMN timeline VARCHAR(60);
ALTER TABLE inquiries ADD COLUMN urgency VARCHAR(40);
ALTER TABLE inquiries ADD COLUMN estimate_low NUMERIC(12,2);
ALTER TABLE inquiries ADD COLUMN estimate_high NUMERIC(12,2);
ALTER TABLE inquiries ADD COLUMN estimate_currency VARCHAR(8) NOT NULL DEFAULT 'INR';
ALTER TABLE inquiries ADD COLUMN lead_score INT NOT NULL DEFAULT 0;
ALTER TABLE inquiries ADD COLUMN lead_tier VARCHAR(10) NOT NULL DEFAULT 'NURTURE';
ALTER TABLE inquiries ADD COLUMN utm_source VARCHAR(120);
ALTER TABLE inquiries ADD COLUMN utm_medium VARCHAR(120);
ALTER TABLE inquiries ADD COLUMN utm_campaign VARCHAR(120);
ALTER TABLE inquiries ADD COLUMN referrer VARCHAR(300);
ALTER TABLE inquiries ADD COLUMN landing_page VARCHAR(300);

-- Admin pipeline views will filter/sort by these constantly.
CREATE INDEX idx_inquiries_lead_tier ON inquiries (lead_tier);
CREATE INDEX idx_inquiries_intent ON inquiries (intent);
