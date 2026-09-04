-- Neelastack platform - invoicing & Razorpay payments

CREATE TABLE invoices (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id           UUID NOT NULL REFERENCES engagements(id) ON DELETE CASCADE,
    invoice_number          VARCHAR(40) NOT NULL UNIQUE,
    description             VARCHAR(200) NOT NULL,
    amount                  NUMERIC(12,2) NOT NULL,
    currency                VARCHAR(8) NOT NULL DEFAULT 'INR',
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    razorpay_order_id       VARCHAR(100),
    razorpay_payment_id     VARCHAR(100),
    razorpay_signature      VARCHAR(200),
    due_date                DATE,
    paid_at                 TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoices_engagement ON invoices (engagement_id);
CREATE INDEX idx_invoices_razorpay_order ON invoices (razorpay_order_id);
