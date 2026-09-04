# Client Acquisition & High-Ticket Conversion Engine — Implementation Status

## What was built

### Module 1 — Instant Architecture Risk Score (`/audit-preview`)
- `ArchitectureRiskScoringService`: deterministic, disclosed-methodology scoring
  over an 8-item bottleneck catalog (DB pooling, SSR hydration, payment
  concurrency, N+1, cache invalidation, JWT refresh races, webhook idempotency,
  auth rate limiting). Score = sum of selected findings' weights + a small stack-
  complexity bonus, capped at 100. No fabricated "AI scanned your code" claim —
  the report explicitly says it's a self-assessment.
- `PublicAuditPreviewController`: `POST /api/v1/public/audit-preview/score` (free,
  anonymous, nothing persisted) and `POST /api/v1/public/audit-preview/unlock`
  (name/email/company required — creates a real `Inquiry`, intent=`AUDIT`,
  source=`audit_preview`, lead-scored, PDF emailed via the existing
  `ExecutiveReportPdfService`).
- Frontend: `/audit-preview` two-step page (select stack/bottlenecks → instant
  score + 2 teaser findings → gated unlock form → full findings list).
- Rate-limited (20/10min for `/score`, 5/10min for `/unlock`).

### Module 2 — Tier-1 Instant Booking
- `app.sales.booking-enabled` / `app.sales.calendly-url` config (off/empty by
  default — never shows a broken embed in an unconfigured environment).
- `InquiryDto.bookingUrl`, computed centrally in `InquiryService.toDto()`, so it
  automatically flows through the plain contact form, estimator, and
  architecture-review responses (and the new audit-preview unlock) alike —
  non-null only for Tier-1 (`HOT`) leads.
- Frontend: shared `BookingWidgetComponent`, wired into the success states of
  Contact, Estimator, and Architecture Review, showing a "Book a call now" CTA
  instead of the generic thank-you when `bookingUrl` is present.

### Module 3 — Contextual Case-Study Injection on Proposals
- `Quotation.serviceCategory` (admin-set, or auto-inferred at creation time from
  the inquiry's free-text project type via a conservative keyword map — a miss
  just means no case study, never a wrong guess).
- `Project.serviceCategories` / `Project.keyMetrics` — both admin-entered,
  empty by default, no fabricated figures.
- `QuotationService.resolveCaseStudy()`: best published, category-matching,
  featured-first case study; returns nothing (not a generic fallback) when no
  real match exists.
- `PublicQuotationDto.relatedCaseStudy` (title, summary, real metrics, rating).
- Frontend: case-study block on the public `/quote/:token` page.
- Admin: `ProjectRequest`/`ProjectDto` now carry `serviceCategories`/`keyMetrics`
  (CMS UI for editing these lists was **not** built this pass — see Known
  Limitations).

### Module 4 — Automated Post-Invoice Testimonial Loop
- `V26__acquisition_engine.sql`: `testimonial_requests` table (unique per
  invoice), `reviews.project_id` made nullable, `reviews.video_url` /
  `submitted_via` added, `engagements.project_id` link.
- `TestimonialService`: idempotent queueing (unique index + existence check) on
  invoice PAID; race-safe, one-time atomic token consumption on submission
  (conditional `UPDATE ... WHERE status = 'PENDING'`, matching this codebase's
  existing pattern for one-time tokens).
- Hooked into **both** `InvoiceService` PAID-transition points
  (`verifyAndConfirmPayment` and `markPaidFromWebhook`) — best-effort, wrapped in
  try/catch, never allowed to affect the payment transaction.
- `PublicTestimonialController` (`GET`/`POST /api/v1/public/testimonials/{token}`).
- Client-submitted reviews land **unpublished** — new admin endpoints
  `GET /api/v1/admin/testimonials/pending` and
  `PUT /api/v1/admin/testimonials/{id}/moderate` let an admin publish (and
  optionally assign a case study) before anything goes live or reaches
  `SchemaBuilderService`'s Review/AggregateRating JSON-LD.
- Frontend: `/testimonial/:token` page (rating, review body, optional video URL).

## Files changed/added (high level)
- **Backend, new**: `ArchitectureRiskScoringService`, `TestimonialService`,
  `PublicAuditPreviewController`, `PublicTestimonialController`,
  `TestimonialRequest` (+status enum), `ReviewSource`, `TestimonialRequestRepository`,
  8 new DTOs (audit preview/unlock, case study proof, testimonial), `V26` migration.
- **Backend, modified**: `InquiryService`, `InvoiceService`, `QuotationService`,
  `ProjectService`, `LeadScoringService`, `EmailService`, `AdminContentController`,
  `RateLimitFilter`, entities (`Project`, `Engagement`, `Quotation`, `Review`,
  `AuditAction`), repositories (`ProjectRepository`, `ReviewRepository`),
  DTOs (`InquiryDto`, `QuotationDto`, `QuotationRequest`, `PublicQuotationDto`,
  `ProjectDto`, `ProjectRequest`, `ReviewDto`), `application.yml`,
  `InvoiceServiceTest` / `QuotationServiceTest` (constructor signatures updated).
- **Frontend, new**: `audit-preview` feature, `testimonial` feature,
  `BookingWidgetComponent`, `TestimonialService` (Angular).
- **Frontend, modified**: `content.model.ts`, `inquiry.service.ts`,
  `contact`/`estimator`/`architecture-review` components, `quote` component,
  `app.routes.ts`, `app.routes.server.ts`.

## VERIFIED vs NOT VERIFIED
- **VERIFIED (by manual cross-reference, not by running a build)**: every new
  field/method reference was checked against the actual entity/DTO/repository
  it calls into (field names, types, constructor signatures, enum values,
  existing exception classes, existing DI patterns). Test files that construct
  the modified services were updated to match new constructor arguments.
- **NOT VERIFIED**: no Maven build, no `mvn test`, no `npm install`/`ng build`,
  no Flyway migration run, no Docker build, no end-to-end test — this sandbox
  has no network access to Maven Central and no pre-existing `node_modules`/
  `.m2` cache, so none of these could actually be executed. Treat this as
  carefully-reviewed source, not a build-verified artifact — run
  `mvn -q compile` and `npm ci && npm run build` before deploying.

## Known limitations (be upfront about these)
- No admin CMS UI was added for editing `Project.serviceCategories` /
  `Project.keyMetrics`, or for the `Quotation.serviceCategory` picker at
  quotation-creation time — the backend fully supports both (admins can set
  them via the existing JSON request bodies), but no new form fields were added
  to the Angular admin screens for them in this pass.
- No automated tests were added for the four new modules themselves (existing
  tests were only updated to keep compiling against the changed constructors).
  Given the emphasis elsewhere in this codebase on concurrency/race tests, the
  testimonial token's atomic consumption and the testimonial-per-invoice unique
  index are the two properties most worth a dedicated concurrency test before
  shipping.
- The risk-scoring catalog and case-study category list are intentionally small
  and hardcoded (not admin-editable) — consistent with this codebase's existing
  "no configurable rules engine yet" approach to `LeadScoringService`.
