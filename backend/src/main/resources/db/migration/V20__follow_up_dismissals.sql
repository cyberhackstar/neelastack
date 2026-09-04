-- Admin Sales Command Center (Section 1): the Follow-Up Panel's "mark as done" /
-- "snooze" actions need somewhere to persist that a human has decided to defer a
-- follow-up. followUpTasks() (AnalyticsService) is deliberately fully derived at read
-- time from quotation lifecycle timestamps -- that's a good design for the read side
-- and stays unchanged. A dismissal is genuinely new mutable state (it doesn't change
-- quotation status, it just means "don't surface this again until <dismissed_until>"),
-- so it gets its own small table rather than shadowing quotation state.
--
-- One open dismissal per quotation is enough -- a new dismiss/snooze call replaces the
-- previous one rather than accumulating history, since only the *current* deferral
-- matters for filtering the live follow-up list.
CREATE TABLE follow_up_dismissals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quotation_id UUID NOT NULL REFERENCES quotations (id) ON DELETE CASCADE,
    dismissed_by VARCHAR(255) NOT NULL,
    -- NULL means "done" (dismissed indefinitely, until quotation status itself changes).
    -- A non-null value means "snoozed" -- filtered out of followUpTasks() only until
    -- this timestamp, then it reappears on its own.
    dismissed_until TIMESTAMP,
    reason VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_follow_up_dismissals_quotation ON follow_up_dismissals (quotation_id);
