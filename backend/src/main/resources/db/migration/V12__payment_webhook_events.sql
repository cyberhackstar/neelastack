-- Persists every Razorpay webhook delivery so retried/duplicate deliveries (Razorpay
-- explicitly retries on non-2xx, and can also just double-send) are recognized and
-- skipped instead of being reprocessed, and so every delivery is auditable after the fact.
CREATE TABLE payment_webhook_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    razorpay_event_id   VARCHAR(100) NOT NULL UNIQUE,
    event_type          VARCHAR(60) NOT NULL,
    razorpay_payment_id VARCHAR(100),
    razorpay_order_id   VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    raw_payload         TEXT NOT NULL,
    received_at         TIMESTAMP NOT NULL DEFAULT now(),
    processed_at        TIMESTAMP
);

CREATE INDEX idx_payment_webhook_events_order ON payment_webhook_events (razorpay_order_id);
