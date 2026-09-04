-- Proposal analytics (master prompt section 51): track how many times a client has
-- opened their quotation link, and when they last did. Both nullable/defaulted, so
-- existing quotations just start at zero views rather than needing backfill.

ALTER TABLE quotations ADD COLUMN view_count INT NOT NULL DEFAULT 0;
ALTER TABLE quotations ADD COLUMN last_viewed_at TIMESTAMP;
