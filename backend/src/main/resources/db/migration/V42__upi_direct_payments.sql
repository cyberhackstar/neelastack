-- P0 #1 (this session's request): a direct UPI QR payment path that settles straight into
-- Bhawesh's own bank account -- no Razorpay/gateway commission. `upi_payment_methods` holds
-- the admin-uploaded QR codes (GPay/PhonePe/Paytm); `upi_payment_submissions` is the client's
-- "I paid, here's my UTR" claim, which an admin manually verifies against the bank statement
-- before the linked invoice is marked PAID (see PaymentSource.MANUAL_UPI).

CREATE TABLE upi_payment_methods (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label                VARCHAR(60) NOT NULL,
    vpa                  VARCHAR(100),
    payee_name           VARCHAR(120),
    qr_image_url         VARCHAR(500) NOT NULL,
    qr_image_public_id   VARCHAR(200),
    qr_image_resource_type VARCHAR(20),
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    display_order        INTEGER NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE upi_payment_submissions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id            UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    upi_method_id         UUID NOT NULL REFERENCES upi_payment_methods (id),
    submitted_by_id       UUID NOT NULL REFERENCES users (id),
    utr_reference         VARCHAR(60) NOT NULL,
    payer_upi_id          VARCHAR(100),
    amount_claimed        NUMERIC(12,2) NOT NULL,
    screenshot_url        VARCHAR(500),
    screenshot_public_id  VARCHAR(200),
    screenshot_resource_type VARCHAR(20),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    admin_note            VARCHAR(500),
    verified_by_id        UUID REFERENCES users (id),
    verified_at           TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_upi_submission_invoice ON upi_payment_submissions (invoice_id);
-- The admin verification queue always filters "still pending" first -- see
-- UpiPaymentService#listPending.
CREATE INDEX idx_upi_submission_status ON upi_payment_submissions (status, created_at);

-- A client submitting a second claim for an invoice already PAID (double-submit, or the
-- webhook/Razorpay path already settled it) should not create a second row for the same
-- (invoice, utr) pair by accident -- protects against an accidental duplicate button click,
-- not a security boundary (an admin still has to verify every row regardless).
CREATE UNIQUE INDEX idx_upi_submission_invoice_utr ON upi_payment_submissions (invoice_id, utr_reference);
