-- Tracks how many times a webhook event has been (re)claimed for processing. Not used for
-- correctness — status + the pessimistic lock held across PaymentWebhookEventService's
-- claim+process+mark sequence already prevent double-processing — but gives the admin replay
-- view (item #3 of the review) something to show besides "FAILED" with no history, e.g.
-- "FAILED, 3 attempts".
ALTER TABLE payment_webhook_events ADD COLUMN attempt_count INT NOT NULL DEFAULT 1;
