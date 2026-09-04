-- Sales intelligence pass: quotations already track sentAt (V-init), view_count/
-- last_viewed_at (V15), and a combined respondedAt+status (V-init). This adds the
-- fields the analytics layer needs to distinguish "opened" from "re-opened" and to
-- compute win rate / sales-cycle duration without re-deriving them from status alone:
--
--   first_viewed_at — set once, on the client's first open. last_viewed_at (V15)
--                     keeps updating on every subsequent open; first_viewed_at never
--                     changes after it's set. Together they give "time to first view"
--                     and "time between opens" without adding a raw event log.
--   accepted_at      — set only when status transitions SENT -> ACCEPTED.
--   rejected_at      — set only when status transitions SENT -> REJECTED.
--
-- responded_at (existing) is kept as-is for backward compatibility with anything
-- already reading it; accepted_at/rejected_at are the query-friendly split of the
-- same event, letting "average time to accept" and "average time to reject" be
-- computed separately instead of both blending into one respondedAt column.
--
-- Backfilled from existing data so historical quotations aren't blank:
--   - first_viewed_at backfills from last_viewed_at for any quotation with
--     view_count > 0 (we don't know the *true* first view, but last_viewed_at is a
--     safe upper bound and better than NULL for a quotation that's clearly been seen).
--   - accepted_at/rejected_at backfill from responded_at, split by current status.

ALTER TABLE quotations ADD COLUMN first_viewed_at TIMESTAMP;
ALTER TABLE quotations ADD COLUMN accepted_at TIMESTAMP;
ALTER TABLE quotations ADD COLUMN rejected_at TIMESTAMP;

UPDATE quotations SET first_viewed_at = last_viewed_at WHERE view_count > 0 AND first_viewed_at IS NULL;
UPDATE quotations SET accepted_at = responded_at WHERE status = 'ACCEPTED' AND responded_at IS NOT NULL;
UPDATE quotations SET rejected_at = responded_at WHERE status = 'REJECTED' AND responded_at IS NOT NULL;

-- Analytics/follow-up queries filter on these constantly (win-rate joins, "unviewed
-- for N days" reminders, "viewed but unanswered" alerts).
CREATE INDEX idx_quotations_status_sent_at ON quotations (status, sent_at);
CREATE INDEX idx_quotations_status_last_viewed_at ON quotations (status, last_viewed_at);
