-- P0 #2 (client-workspace review): the notification engine. A single generic table backs
-- every in-app notification -- milestone approvals, invoices, UPI payment verification,
-- change requests, payment-schedule reminders -- rather than a bespoke table per event
-- family. `related_entity_type`/`related_entity_id` let the frontend deep-link back to the
-- thing the notification is about without needing a foreign key per possible target type.

CREATE TABLE notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    engagement_id       UUID REFERENCES engagements (id) ON DELETE CASCADE,
    type                VARCHAR(40) NOT NULL,
    priority            VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    title               VARCHAR(200) NOT NULL,
    body                VARCHAR(1000),
    related_entity_type VARCHAR(40),
    related_entity_id   UUID,
    deep_link           VARCHAR(300),
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMP,
    expires_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

-- The dashboard bell always queries "my unread notifications, newest first" -- this partial
-- index keeps that query fast without indexing rows that are already read and will never be
-- queried by that filter again.
CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_id, created_at DESC)
    WHERE is_read = FALSE;

CREATE INDEX idx_notifications_recipient_all
    ON notifications (recipient_id, created_at DESC);
