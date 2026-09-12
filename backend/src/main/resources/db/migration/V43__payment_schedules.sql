-- P0 #3 (client-workspace review): enterprise projects need a deposit + milestone-linked
-- payment plan, not just ad-hoc invoices. A schedule belongs to one engagement; each
-- installment turns into a real Invoice once an admin raises it.

CREATE TABLE payment_schedules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id UUID NOT NULL REFERENCES engagements (id) ON DELETE CASCADE,
    total_amount  NUMERIC(12,2) NOT NULL,
    currency      VARCHAR(8) NOT NULL DEFAULT 'INR',
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- One active payment schedule per engagement keeps "the" schedule for a project unambiguous
-- for both the client dashboard and the admin command center.
CREATE UNIQUE INDEX idx_payment_schedule_engagement ON payment_schedules (engagement_id);

CREATE TABLE payment_schedule_installments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_schedule_id UUID NOT NULL REFERENCES payment_schedules (id) ON DELETE CASCADE,
    label               VARCHAR(100) NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    percentage          NUMERIC(5,2),
    due_date            DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invoice_id          UUID REFERENCES invoices (id),
    display_order       INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_installment_schedule ON payment_schedule_installments (payment_schedule_id, display_order);
