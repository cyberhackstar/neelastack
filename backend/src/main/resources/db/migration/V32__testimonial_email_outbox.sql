-- Previously: queueRequestForInvoice fired the testimonial email @Async and swallowed any send
-- failure (EmailService's private send() helper logs and moves on). Since the row was already
-- created as PENDING and TestimonialService#queueRequestForInvoice's existsByInvoiceId guard
-- prevents a second row for the same invoice, a transient SMTP failure meant that client would
-- simply never receive the email and nothing would ever retry it. These columns turn the flow
-- into a proper outbox: TestimonialService now attempts a synchronous send when the row is
-- created, and a scheduled worker retries anything not yet confirmed sent, with backoff, up to
-- a bounded attempt count.
ALTER TABLE testimonial_requests ADD COLUMN IF NOT EXISTS email_sent_at TIMESTAMP;
ALTER TABLE testimonial_requests ADD COLUMN IF NOT EXISTS email_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE testimonial_requests ADD COLUMN IF NOT EXISTS last_email_error VARCHAR(500);
ALTER TABLE testimonial_requests ADD COLUMN IF NOT EXISTS next_email_attempt_at TIMESTAMP NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_testimonial_requests_retry
    ON testimonial_requests (next_email_attempt_at)
    WHERE email_sent_at IS NULL;

COMMENT ON COLUMN testimonial_requests.email_sent_at IS
    'Set once the testimonial-invite email is confirmed sent (not just queued). NULL means still pending/retrying.';
COMMENT ON COLUMN testimonial_requests.email_attempts IS
    'Number of send attempts made so far -- the retry worker stops after a bounded max (see TestimonialService).';
COMMENT ON COLUMN testimonial_requests.next_email_attempt_at IS
    'Earliest time the retry worker should attempt this row again -- exponential backoff on failure.';
