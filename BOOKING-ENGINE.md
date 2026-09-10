# Booking Engine

A first-party scheduling & appointment platform, built to replace the Calendly
embed that used to power the HOT-lead instant-booking flow (`app.sales.calendly-url`).
See the original 58-feature master prompt this was scoped against for the full
ambition — this document says exactly which of those 58 are real, working code in
this build, and which are intentionally not.

## What's actually built (Phase 1 + parts of Phase 2)

- **Meeting types** — admin-configurable appointment templates (name, duration,
  buffers, min-notice, max-horizon, location, price, cancellation policy) with
  dynamic per-type intake form fields. `MeetingTypeService` / `MeetingTypeRequest`.
- **Availability engine** — recurring weekly windows (multiple windows per day model
  breaks as the gap between them), one-off date overrides (holidays / special
  hours), buffers before/after, minimum booking notice, maximum booking horizon.
  `AvailabilityService`.
- **Real double-booking protection at the database level** — a Postgres `EXCLUDE
  USING gist` constraint on the bookings time range (V33 migration), not just an
  application-side check. Idempotency keys prevent a retried submit from creating a
  duplicate booking.
- **Secure, tokenized, no-login client flows** — view / reschedule / cancel a
  booking via an unguessable token (never the sequential booking number or id).
- **Timezone handling** — stored in UTC (`timestamptz`), converted to the client's
  and admin's timezone only at display time.
- **Email notifications** reusing the existing `EmailService` template style —
  confirmation, admin alert, reschedule, cancellation, no-show follow-up, and 24h/1h
  reminders (`BookingReminderScheduler`).
- **Lead ↔ booking relationship** — a booking can carry an `inquiryId` and inherits
  that inquiry's UTM/source attribution; `InquiryService#resolveBookingUrl` now
  points HOT leads at `/book/{slug}?inquiryId=...` instead of Calendly.
- **Admin dashboard** — today/upcoming/month counts, cancellations, no-shows, HOT-lead
  bookings, revenue; a filterable/searchable bookings table with status/outcome
  actions (`AdminBookingController`, `admin-bookings.component`).
- **Sales funnel + revenue attribution** — inquiry → booking → attendance → proposal
  → client conversion rates, and revenue-by-UTM-source, computed from data already
  collected elsewhere (no new tracking pipeline). `BookingAnalyticsService`.
- **No-show automation** — marking a booking NO_SHOW fires a "sorry we missed you /
  book another time" email automatically.
- **Optional Google Calendar sync** (off by default, free tier only) — one-way busy-
  block reads feeding into the availability engine, and best-effort event creation
  (with a Google Meet link) on booking. `GoogleCalendarService`.
- **Audit logging** — every booking lifecycle event goes through the existing
  `AuditLogService`/`AuditAction` rather than a parallel logging system.

## Setup

1. Run the two new Flyway migrations (`V33`, `V34`) — they run automatically on next
   boot, same as any other migration in this project.
2. New env vars — see `.env.example`:
   - `SALES_BOOKING_ENABLED`, `SALES_DEFAULT_MEETING_TYPE_SLUG`, `SALES_CALENDLY_URL`
   - `GOOGLE_CALENDAR_ENABLED`, `GOOGLE_CALENDAR_REDIRECT_URI` (optional feature)
3. **Optional Google Calendar sync**: in the same Google Cloud project as your
   existing `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, enable the Calendar API and
   add `GOOGLE_CALENDAR_REDIRECT_URI` (default
   `{SITE_BASE_URL}/api/v1/admin/booking/calendar/callback`) as an authorized
   redirect URI on that OAuth client. Then set `GOOGLE_CALENDAR_ENABLED=true` and use
   "Connect Google Calendar" from the admin bookings page (not yet a dedicated
   settings UI — call `POST /api/v1/admin/booking/calendar/connect` and open the
   returned URL, or wire a button to it).
4. Seed data creates 3 real meeting types (Discovery Call, Project Consultation,
   Technical Consultation) and a Mon–Fri weekly schedule — edit or replace both from
   the admin dashboard.

## Deliberately not built (needs a paid service, or is a different product)

These were in the original 58-feature list. Building fake/stub code for them would
be worse than not touching them — this is what's left, and why:

- **SMS / WhatsApp reminders (feature 48)** — needs Twilio or WhatsApp Business API,
  both paid per-message. Not built.
- **AI-assisted qualification / meeting-brief generation (features 54-55)** — needs
  a paid LLM API call per inquiry/meeting. The existing rule-based `LeadScoringService`
  already covers qualification; this would be an enhancement on top, not a
  replacement, and needs an explicit decision about which provider/budget to use.
- **Full paid-booking checkout (features 20-21)** — the data model supports it
  (`meeting_types.requires_payment`, `bookings.payment_status`, `hold_expires_at`,
  and `BookingReminderScheduler#releaseExpiredHolds` already release expired
  never-paid holds), but the actual "create a Razorpay order, verify the webhook,
  confirm the booking" wiring was **not** connected to your existing
  `PaymentController`/Razorpay webhook flow in this pass, to avoid guessing at that
  flow's internals without reading it directly. All seeded meeting types currently
  ship free (`requiresPayment=false`) so this gap doesn't block anything today.
- **Multi-tenant SaaS / subscription billing (features 57-58)** — turning this into
  a sellable product for other agencies is a different, much larger project
  (org-scoping every table, billing, isolation). Not attempted.
- **Team calendars / round-robin / collective meetings (features 11-14)** — this
  build is intentionally single-calendar (one business, one set of availability
  rules), matching how Neelastack actually operates today. The schema has room to
  add a `staff_id` later without a rewrite, but that's a real feature addition, not
  a checkbox.
- **A/B testing on CTA copy (feature 51)** — a genuine analytics/experimentation
  feature, out of scope for a scheduling engine.

## Known simplification worth knowing about

**Reschedule is an in-place time update on the same booking row**, not a new row
linked via `rescheduled_from_id` (that column exists but isn't populated by the
current code). The audit log entry for `BOOKING_RESCHEDULED` carries the old/new
time in its metadata, which is the history trail. Simpler, and the database
exclusion constraint protects the new time exactly the same way it would a fresh
booking — but if you want a literal "old appointment becomes a RESCHEDULED row"
history model later, that's a real (small) follow-up, not a bug.
