-- P0 hardening pass: secure admin bootstrap, session invalidation, payment concurrency.

-- 1) Purge the seeded default admin (admin@neelastack.com / ChangeMe@123). It was a known,
--    published credential sitting in version control -- an environment where it was never
--    rotated is compromised by definition. AdminBootstrapRunner (see backend config) now
--    provisions the first admin from ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD env vars
--    on first boot, only when zero admins exist, with must_change_password forced true and
--    MFA enrollment required before any other admin action is permitted.
DELETE FROM users WHERE email = 'admin@neelastack.com';

-- 2) Forced password change flag -- set on the bootstrap admin and on any account an admin
--    force-resets; AuthGuard/step-up middleware blocks everything except the password-change
--    endpoint while this is true.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) Token version -- bumped on password reset, MFA disable, and MFA force-reset. Embedded in
--    every JWT (access + refresh) as the "tv" claim; JwtAuthFilter/AuthService reject any token
--    whose "tv" doesn't match the user's current value, so a security event invalidates every
--    outstanding token (across every device/tab) immediately, without needing a per-user Redis
--    session registry.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

-- 4) Payment attempt history -- every Razorpay order creation for an invoice is now recorded
--    here rather than only tracked via the single `invoices.razorpay_order_id` column. This is
--    what lets InvoiceService reject a second concurrent checkout attempt against an invoice
--    that already has a live (non-expired, non-failed) order, instead of silently overwriting
--    razorpay_order_id and orphaning the first browser tab's order.
CREATE TABLE payment_attempts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id         UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    razorpay_order_id  VARCHAR(64) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'CREATED', -- CREATED | SUCCEEDED | FAILED | SUPERSEDED
    amount             NUMERIC(12, 2) NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_payment_attempts_order_id ON payment_attempts (razorpay_order_id);
CREATE INDEX idx_payment_attempts_invoice ON payment_attempts (invoice_id);
-- Fast lookup of "is there already a live attempt for this invoice" without a full table scan.
CREATE INDEX idx_payment_attempts_invoice_status ON payment_attempts (invoice_id, status);
