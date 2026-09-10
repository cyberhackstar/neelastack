-- First-party scheduling & appointment platform ("Booking Engine").
--
-- Replaces the Calendly-embed instant-booking widget (app.sales.calendly-url, see
-- InquiryService / EmailService) with a real, first-party booking system: meeting
-- types, a rule-based availability engine (weekly windows, breaks, holidays/date
-- overrides, buffers, min-notice, max-horizon), secure tokenized bookings, and an
-- append-only link back to the existing inquiries/lead-scoring pipeline.
--
-- Everything here is additive -- no existing table, column, or query is changed.
--
-- Double-booking protection is enforced at the DATABASE level via a GiST exclusion
-- constraint on the booking time range (not just an application-level check), per
-- the "never rely solely on frontend/if-available checks" requirement.

CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Atomic, gap-tolerant numbering for human-facing booking references (e.g. NS-2026-000142).
-- A DB sequence -- rather than counting existing rows -- so concurrent bookings never race
-- for the same number.
CREATE SEQUENCE IF NOT EXISTS booking_number_seq START WITH 100000 INCREMENT BY 1;

-- ---------------------------------------------------------------------------
-- Meeting types (admin-configured appointment templates)
-- ---------------------------------------------------------------------------

CREATE TABLE meeting_types (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(120) NOT NULL,
    slug                VARCHAR(140) NOT NULL UNIQUE,
    description         TEXT,
    duration_minutes    INTEGER NOT NULL CHECK (duration_minutes > 0),
    buffer_before_minutes INTEGER NOT NULL DEFAULT 0 CHECK (buffer_before_minutes >= 0),
    buffer_after_minutes  INTEGER NOT NULL DEFAULT 0 CHECK (buffer_after_minutes >= 0),
    min_notice_minutes  INTEGER NOT NULL DEFAULT 120 CHECK (min_notice_minutes >= 0),
    max_horizon_days    INTEGER NOT NULL DEFAULT 30 CHECK (max_horizon_days > 0),
    location_type       VARCHAR(20) NOT NULL DEFAULT 'GOOGLE_MEET',
    location_detail     VARCHAR(300),
    price               NUMERIC(12, 2),
    currency            VARCHAR(8) NOT NULL DEFAULT 'INR',
    requires_payment    BOOLEAN NOT NULL DEFAULT FALSE,
    cancellable_until_hours INTEGER NOT NULL DEFAULT 12 CHECK (cancellable_until_hours >= 0),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON COLUMN meeting_types.location_type IS
    'GOOGLE_MEET | ZOOM | TEAMS | PHONE | IN_PERSON | CUSTOM (see LocationType enum).';
COMMENT ON COLUMN meeting_types.requires_payment IS
    'When true, a booking for this meeting type stays in PAYMENT_PENDING (slot held,
     see bookings.hold_expires_at) until the existing Razorpay flow confirms payment.';

-- Dynamic intake fields per meeting type (feature 7: admin-configurable booking form)
CREATE TABLE booking_form_fields (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_type_id UUID NOT NULL REFERENCES meeting_types(id) ON DELETE CASCADE,
    field_key       VARCHAR(60) NOT NULL,
    label           VARCHAR(200) NOT NULL,
    field_type      VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    is_required     BOOLEAN NOT NULL DEFAULT FALSE,
    options         JSONB,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    UNIQUE (meeting_type_id, field_key)
);

COMMENT ON COLUMN booking_form_fields.field_type IS
    'TEXT | TEXTAREA | EMAIL | PHONE | NUMBER | SELECT | MULTI_SELECT | RADIO | CHECKBOX | URL | DATE';

-- ---------------------------------------------------------------------------
-- Availability engine
-- ---------------------------------------------------------------------------

-- Recurring weekly windows. Multiple rows per day_of_week model multiple windows
-- (e.g. 10:00-13:00 and 15:00-18:00), so a "break" is simply the gap between them --
-- no separate breaks table needed.
CREATE TABLE availability_windows (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1=Monday .. 7=Sunday (ISO-8601)
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    timezone    VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (end_time > start_time)
);

CREATE INDEX idx_availability_windows_day ON availability_windows (day_of_week, is_active);

-- One-off date overrides: full-day holidays/blocks, or a special date with its own
-- (possibly narrower) hours. is_available=false + null times = whole day blocked.
-- is_available=true + times set = "only these hours today" (overrides the weekly rule).
CREATE TABLE availability_date_overrides (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    override_date DATE NOT NULL UNIQUE,
    is_available BOOLEAN NOT NULL DEFAULT FALSE,
    start_time   TIME,
    end_time     TIME,
    reason       VARCHAR(200),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (
        (is_available = FALSE) OR
        (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    )
);

-- ---------------------------------------------------------------------------
-- Bookings
-- ---------------------------------------------------------------------------

CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_number      VARCHAR(30) NOT NULL UNIQUE,

    meeting_type_id     UUID NOT NULL REFERENCES meeting_types(id),
    inquiry_id          UUID REFERENCES inquiries(id),
    rescheduled_from_id UUID REFERENCES bookings(id),

    client_name         VARCHAR(160) NOT NULL,
    client_email        VARCHAR(180) NOT NULL,
    client_phone        VARCHAR(30),
    client_company      VARCHAR(160),
    client_timezone     VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',

    -- Stored in UTC; converted to client/admin timezone at display time only.
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    outcome             VARCHAR(30),
    internal_notes      TEXT,

    cancel_reason       TEXT,
    no_show             BOOLEAN NOT NULL DEFAULT FALSE,

    -- Secure, opaque, unguessable tokens for the no-login client flows (view/reschedule/
    -- cancel a booking from an email link) -- never the sequential booking_number/id.
    view_token          VARCHAR(64) NOT NULL UNIQUE,
    reschedule_token     VARCHAR(64) NOT NULL UNIQUE,
    cancel_token        VARCHAR(64) NOT NULL UNIQUE,

    -- Idempotency: a retried POST /bookings with the same client-generated key never
    -- creates a second row.
    idempotency_key     VARCHAR(80) UNIQUE,

    -- Payment (reuses the existing Razorpay integration -- feature 20/21)
    requires_payment    BOOLEAN NOT NULL DEFAULT FALSE,
    price               NUMERIC(12, 2),
    currency            VARCHAR(8) DEFAULT 'INR',
    payment_status      VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    razorpay_order_id   VARCHAR(80),
    hold_expires_at     TIMESTAMPTZ,

    -- Attribution -- mirrors inquiries.utm_* (feature 30/31). When booked from an
    -- existing inquiry, inquiry_id is the source of truth and these are left null.
    source              VARCHAR(60),
    utm_source          VARCHAR(120),
    utm_medium          VARCHAR(120),
    utm_campaign        VARCHAR(120),
    landing_page        VARCHAR(300),
    referrer            VARCHAR(300),

    -- Calendar sync (optional -- see V34 / GoogleCalendarService)
    calendar_event_id   VARCHAR(200),
    meeting_url         VARCHAR(300),

    -- Reminder / lifecycle timestamps
    confirmation_sent_at   TIMESTAMP,
    reminder_24h_sent_at   TIMESTAMP,
    reminder_1h_sent_at    TIMESTAMP,
    confirmed_at           TIMESTAMP,
    cancelled_at           TIMESTAMP,
    completed_at           TIMESTAMP,

    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_bookings_time_order CHECK (end_at > start_at),
    CONSTRAINT chk_bookings_status CHECK (status IN
        ('SCHEDULED', 'CONFIRMED', 'RESCHEDULED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT chk_bookings_payment_status CHECK (payment_status IN
        ('NOT_REQUIRED', 'PENDING', 'PAID', 'FAILED', 'REFUNDED'))
);

CREATE INDEX idx_bookings_meeting_type ON bookings (meeting_type_id);
CREATE INDEX idx_bookings_inquiry ON bookings (inquiry_id);
CREATE INDEX idx_bookings_start_at ON bookings (start_at);
CREATE INDEX idx_bookings_status ON bookings (status);
CREATE INDEX idx_bookings_client_email ON bookings (client_email);
CREATE INDEX idx_bookings_created_at ON bookings (created_at);

-- Database-level double-booking protection (feature 40): no two bookings that are
-- still "live" (not cancelled/rescheduled-away) may overlap in time, enforced
-- regardless of any race between concurrent requests or application bugs.
ALTER TABLE bookings ADD CONSTRAINT excl_bookings_no_overlap
    EXCLUDE USING gist (
        tstzrange(start_at, end_at, '[)') WITH &&
    )
    WHERE (status NOT IN ('CANCELLED', 'RESCHEDULED'));

-- Free-form answers to each meeting type's dynamic form fields (feature 7)
CREATE TABLE booking_form_responses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    field_key   VARCHAR(60) NOT NULL,
    label       VARCHAR(200) NOT NULL,
    value       TEXT,
    UNIQUE (booking_id, field_key)
);

CREATE INDEX idx_booking_form_responses_booking ON booking_form_responses (booking_id);

-- Booking-related audit actions, appended to the existing enum (V22).
-- (Applied via the AuditAction Java enum; no DB-level enum type exists for it --
-- audit_logs.action is a plain VARCHAR -- so no ALTER TYPE is needed here.)

-- Seed a sensible default set of meeting types + weekly availability so the booking
-- page has something real to show immediately after deploy. All admin-editable.
INSERT INTO meeting_types (name, slug, description, duration_minutes, buffer_before_minutes,
    buffer_after_minutes, min_notice_minutes, max_horizon_days, location_type, price, currency,
    requires_payment, cancellable_until_hours, sort_order)
VALUES
    ('Discovery Call', 'discovery-call',
     'A free 20-minute call to understand your project and see if we''re a good fit.',
     20, 5, 10, 120, 30, 'GOOGLE_MEET', NULL, 'INR', FALSE, 6, 1),
    ('Project Consultation', 'project-consultation',
     'A focused 30-minute session to scope your project and discuss timeline and budget.',
     30, 10, 10, 120, 30, 'GOOGLE_MEET', NULL, 'INR', FALSE, 12, 2),
    ('Technical Consultation', 'technical-consultation',
     'A deeper 45-minute architecture/technical discussion for complex or ongoing projects.',
     45, 10, 15, 240, 45, 'GOOGLE_MEET', NULL, 'INR', FALSE, 12, 3);

INSERT INTO availability_windows (day_of_week, start_time, end_time, timezone)
VALUES
    (1, '10:00', '13:00', 'Asia/Kolkata'),
    (1, '15:00', '18:00', 'Asia/Kolkata'),
    (2, '10:00', '13:00', 'Asia/Kolkata'),
    (2, '15:00', '18:00', 'Asia/Kolkata'),
    (3, '10:00', '13:00', 'Asia/Kolkata'),
    (3, '15:00', '18:00', 'Asia/Kolkata'),
    (4, '12:00', '20:00', 'Asia/Kolkata'),
    (5, '10:00', '16:00', 'Asia/Kolkata');

INSERT INTO booking_form_fields (meeting_type_id, field_key, label, field_type, is_required, sort_order)
SELECT id, 'projectSummary', 'What are you trying to build?', 'TEXTAREA', TRUE, 1
FROM meeting_types WHERE slug IN ('project-consultation', 'technical-consultation');

INSERT INTO booking_form_fields (meeting_type_id, field_key, label, field_type, is_required, sort_order)
SELECT id, 'budgetRange', 'Estimated project budget?', 'SELECT', FALSE, 2
FROM meeting_types WHERE slug IN ('project-consultation', 'technical-consultation');

UPDATE booking_form_fields
SET options = '["Under \u20b91L", "\u20b91L - 5L", "\u20b95L - 10L", "\u20b910L+"]'::jsonb
WHERE field_key = 'budgetRange';

INSERT INTO booking_form_fields (meeting_type_id, field_key, label, field_type, is_required, sort_order)
SELECT id, 'howDidYouHear', 'How did you hear about us?', 'TEXT', FALSE, 3
FROM meeting_types;
