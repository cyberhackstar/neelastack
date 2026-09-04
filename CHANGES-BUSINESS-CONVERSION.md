# Business/conversion changes — project estimator + lead scoring + intake flow

Scope note: this pass implements one workstream from the "NEELASTACK FINAL 10/10" master
prompt (the 100+ item version pasted into chat) — items 17-21, 47, 49, and part of 46 —
not the whole document. That document also asks for a revenue/pipeline dashboard, proposal
analytics, follow-up automation, admin MFA, audit logging, real testimonials/pricing/photos,
a full SEO content strategy, and a Google Search Console/analytics setup that all remain
untouched. See the bottom of this file.

Nothing here has been compiled or run — no Maven/npm network access in this sandbox, same
limitation as the prior P0 passes. Run `mvn -B clean verify` and `npm test` before merging.

## Backend

- **New estimator intake**, `POST /api/v1/public/estimator` (`PublicEstimatorController`),
  distinct from the existing plain `/api/v1/public/inquiries` (untouched — the simple
  contact form still works exactly as before). Already covered by the existing
  `/api/v1/public/**` permit-all security rule, so no `SecurityConfig` change was needed.
- **`Inquiry` entity extended** (migration `V14__estimator_lead_scoring.sql`) with intent,
  existing-system, scope, users/scale, integrations, timeline, urgency, estimate
  low/high/currency, lead score/tier, and lightweight UTM/referrer/landing-page columns.
  Every new column is nullable or defaulted — existing rows get `intent=GENERAL`,
  `leadScore=0`, `leadTier=NURTURE` and are **not** retroactively scored, since the inputs
  that scoring needs (budget, timeline, existing-system, etc.) were never captured for them.
- **`LeadScoringService`** — implements the exact weights from master-prompt section 49
  (budget +30, urgent timeline +20, existing production system +15, 2+ integrations +15,
  business platform +10, clear timeline +10; 80+ HOT / 60-79 WARM / below NURTURE). This is
  a rule-of-thumb model with no conversion data behind it yet — the doc comment says so and
  flags it for retuning once real won/lost history exists.
- **`EstimateCalculatorService`** — rule-based preliminary range (audit/API/full-stack/
  enterprise tiers, bumped for integration count and legacy-modernization complexity).
  **The base price figures are placeholders lifted from the example ladder in master-prompt
  section 87** ("these are examples only... use your actual commercial strategy") — replace
  them with real numbers before this reaches a client. Every response carries the
  "not a binding quotation" disclaimer the doc requires.
- The plain contact-form path (`InquiryService.submit`) now also computes a lead score, so
  lead-tier visibility isn't estimator-only.
- `AdminAnalyticsController`'s summary now includes a `hotLeads` count
  (`InquiryRepository.countByLeadTier`), and its recent-inquiries list carries intent/
  score/tier.
- Admin new-inquiry alert email now shows lead tier/score/intent and puts a 🔥 prefix on
  HOT leads in the subject line — the entire "notification" half of the follow-up-automation
  ask (section 50) that's achievable without adding a scheduler/queue.
- New unit tests: `LeadScoringServiceTest`, `EstimateCalculatorServiceTest` — both pure
  logic, no Spring context, should run under plain `mvn test`.

## Frontend

- **New `/estimate` route** (`EstimatorComponent`) — a single reactive form whose fields are
  shown a few at a time as "steps" (intent → project type → existing system [skipped for
  BUILD] → scope → users/integrations → timeline/budget → contact → preliminary estimate),
  matching the section-21 step list. Submits once, at the last input step.
- Homepage's existing BUILD/FIX/MODERNIZE cards now link to `/estimate?intent=...` instead
  of the plain `/contact` form; the estimator itself links back to `/contact` for anyone who
  just wants to send a message ("retain a simple alternative," section 23) — the old form is
  unchanged and still reachable.
- **`AttributionService`** — captures first-touch `utm_source`/`utm_medium`/`utm_campaign`/
  referrer/landing-page into `localStorage` on first load with any of those present, and
  attaches it to estimator submissions. Browser-only (checked via `isPlatformBrowser`); a
  no-op during SSR.
- **`GaAnalyticsService.trackEvent()`** — added a generic custom-event method (the service
  only did automatic page-view tracking before) and wired `estimator_start`/
  `estimator_complete` through it, per the event list in section 46. Only fires when GA is
  actually configured (`environment.gaMeasurementId` set), same as page-view tracking.
- Admin inquiries list, inquiry detail, and dashboard now show intent, lead score/tier, and
  (on the detail page) the preliminary estimate range — no new admin page, just fields added
  to the ones that already existed.

## Deliberately not touched this pass

Everything else in the pasted master prompt: revenue/pipeline dashboard (section 52-53),
proposal open/view tracking (51), scheduled follow-up automation beyond the one email
tweak above (50), commercial offer ladder / retainer pages (54-55, 88), admin MFA / audit
logging / CI security scanning / session hardening (65-66, 94, 64), backup-restore and
observability (62-63), the entire SEO/content workstream — service landing pages, case
studies, the engineering journal, structured data, sitemap segmentation (26-45), real
testimonials/client logos/pricing decisions/founder bio (13-14, 85-87, which need things
only the business owner has), and the Angular/Spring Boot version-currency review (72).

The pricing figures in `EstimateCalculatorService` and the scoring weights in
`LeadScoringService` are both explicitly flagged in code comments as starting points to
replace with real commercial numbers and real conversion data, respectively — don't treat
either as validated.
