# Neelastack — Feature Documentation

This document describes what the Neelastack platform actually does, derived from the
current source tree (backend controllers/services/migrations, frontend routes, and
CI/deployment configuration). It is organized by user-facing capability, not by file
layout — see `README.md` for the technical stack and `docs/` for architecture/deployment
detail on specific subsystems.

Neelastack is a full client-acquisition-to-delivery platform for an independent software
engineering practice: a public marketing site that qualifies and scores leads, a
quotation/proposal workflow, a client portal for active engagements, invoicing with
Razorpay payments, and an admin back office to run all of it — backed by Spring Boot 3.4
(Java 21), Angular 19 (SSR), PostgreSQL, and Redis.

---

## 1. Public site & SEO

- **Marketing pages** — Home, Services, Portfolio (list + case study detail), Blog (list +
  article detail, with tag search and "related posts"), Team, About, Contact, and
  per-technology "solutions" landing pages (`/api/v1/public/solutions/{slug}`) for
  programmatic SEO.
- **SSR** — Angular Universal server-side rendering for public routes, so pages are fully
  rendered for search engines and fast on first paint.
- **On-page SEO** — per-route meta tags, canonical URLs, Open Graph/Twitter cards,
  JSON-LD structured data, and a server-generated `/sitemap.xml` and `/robots.txt` that
  stay in sync with published content automatically.
- **IndexNow** — a keyed verification endpoint (`/{key}.txt`) for search-engine
  IndexNow submission.
- **FAQs & reviews** — services and portfolio projects carry FAQs and moderated client
  reviews; admins approve/reject submitted testimonials before they go public.

## 2. Lead acquisition & qualification

- **Public inquiry form** — captures a prospect's request; every inquiry is scored and
  routed for admin follow-up.
- **Lead scoring** — `LeadScoringService` scores inbound inquiries so the admin sales
  view can prioritize the ones most likely to convert.
- **Instant project estimator** — a public estimator (`/api/v1/public/estimator`) gives
  prospects a ballpark cost/timeline based on project parameters, backed by configurable
  **pricing rules** the admin can create/edit without a code change
  (`AdminPricingController`).
- **Architecture review request** — a public form for prospects to request a technical
  architecture review of their existing system.
- **Automated audit preview** — `PublicAuditPreviewController` scores a prospect's public
  site/repo on request and gates the full report behind an "unlock" step (progressive
  lead capture), using `ArchitectureRiskScoringService`.
- **Lead follow-up queue** — `LeadFollowUpService` surfaces inquiries/quotations that need
  a follow-up nudge; admins can dismiss or snooze individual follow-ups from the
  analytics dashboard.

## 3. Quotation & proposal workflow

- **Admin-authored quotations** — admins turn a qualified inquiry into a formal quotation
  and send it to the prospect (`AdminInquiryController`: create, send).
- **Client-facing proposal links** — prospects view and respond to (accept/decline) a
  quotation via a tokenized public link (`PublicQuotationController`), with no login
  required.
- **View tracking** — quotation views are tracked so admins can see whether/when a
  prospect opened a proposal.
- **Lifecycle timestamps** — sent/viewed/responded timestamps are recorded for each
  quotation for pipeline reporting.
- **Executive report export** — a per-inquiry executive summary PDF is generated on
  demand for admin use (`ExecutiveReportPdfService`).

## 4. Client portal

- **Engagements** — once a quotation converts, the client gets a portal view of their
  engagement(s): status, milestones, and files (`ClientEngagementController`).
- **Milestones** — each engagement tracks delivery milestones with their own status.
- **Project files** — clients can view and upload files against their engagement, and
  remove their own uploads; files are served through Cloudinary with authenticated,
  signed delivery rather than public URLs (`ProjectFileService`, `FileStorageService`).
- **Invoices & payment** — clients see invoices issued against their engagement, pay via
  Razorpay checkout, and download an invoice PDF (`ClientInvoiceController`,
  `PdfInvoiceService`).

## 5. Admin back office

- **Content management** — full CRUD for Services, Portfolio Projects, Blog Posts,
  Solution landing pages, FAQs, and testimonial moderation
  (`AdminContentController`) — this is the site's CMS.
- **Inquiry & sales pipeline** — view/filter inquiries, update their status, and drill
  into a lead's quotations and executive report.
- **Engagement management** — create engagements from won deals, update engagement and
  milestone status.
- **Invoicing** — issue invoices against an engagement (`AdminInvoiceController`).
- **Pricing rules** — maintain the rule set that drives the public estimator.
- **Payment webhook operations** — inspect incoming Razorpay webhook events and replay a
  specific event for reconciliation (`AdminPaymentWebhookController`,
  `PaymentReconciliationService`).
- **Analytics dashboard** — summary KPIs, sales-intelligence view, revenue-by-source and
  revenue-by-attribution breakdowns, and the follow-up queue described above
  (`AdminAnalyticsController`, `AnalyticsService`).
- **Audit log viewer** — a searchable log of security- and admin-sensitive actions
  (`AdminAuditLogController`, `AuditLogService`).
- **Security settings** — admin-facing MFA management (see §7).

## 6. Authentication

- **Email/password auth** — register, login, logout, forgot/reset password, email
  verification (with resend), and authenticated password change (`AuthController`).
- **JWT access + refresh tokens**, with **refresh-token rotation** and **token-version
  invalidation** so that changing a password or logging out invalidates prior tokens
  rather than leaving them valid until natural expiry.
- **Case-insensitive email uniqueness** enforced at the database level (migration V25).
- **Google OAuth2** sign-in (`/auth/oauth-exchange`) alongside password-based login.
- **Mandatory password change** for bootstrap/temporary admin credentials, enforced
  server-side by `MustChangePasswordFilter` (not just a frontend redirect) — an account
  in this state cannot call other authenticated endpoints until it changes its password.

## 7. MFA & admin security hardening

- **TOTP-based MFA** — setup, verification, and disable (`MfaController`, `MfaService`),
  with the TOTP secret itself encrypted at rest (`TotpEncryptionService`).
- **Recovery codes** — generated on MFA enrollment for account recovery if the
  authenticator device is lost.
- **Step-up authentication** — `StepUpAuthFilter` requires a fresh MFA challenge before
  particularly sensitive actions, even within an already-authenticated session.
- **Forced admin reset** — an admin can force another admin's credentials/MFA to be
  reset (`/mfa/{userId}/force-reset`) for account-recovery/offboarding scenarios.
- **Secure admin bootstrap** — migration V24 permanently removed the old
  committed-to-version-control seed admin. `AdminBootstrapRunner` instead provisions
  exactly one admin, once, from `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD`
  environment variables, and forces that account through a password change before it can
  do anything else (see `README.md`).
- **Audit logging** for security-sensitive changes (login, password change, MFA
  enrollment/disable, admin actions).

## 8. Payments (Razorpay)

- **Order creation & checkout** — client-initiated Razorpay checkout against a specific
  invoice (`ClientInvoiceController`).
- **Webhook processing** — signature-verified webhook ingestion
  (`PaymentWebhookController`, `PaymentWebhookProcessor`), with webhook events persisted
  (migration V12) and attempt counts tracked (migration V13) so retries/duplicates are
  handled idempotently rather than double-processing a payment.
- **Payment source & pricing traceability** — payments are linked back to the pricing
  rule/quotation that produced their amount (migration V21), so revenue can be
  attributed and reconciled.
- **Reconciliation tooling** — `PaymentReconciliationService` plus the admin webhook
  replay endpoint for resolving stuck/failed events.
- **Invoice numbering** — a dedicated invoice-number counter (migration V11) avoids
  collisions/gaps under concurrent invoice creation.

## 9. File handling & delivery

- **Cloudinary-backed storage** with authenticated, signed delivery (migration V10) —
  files aren't served from arbitrary public URLs.
- **MIME sniffing / validation** on upload (not just trusting the file extension).
- **Per-engagement access control** — a client can only see/upload files for their own
  engagement; deletion is restricted to files they own.

## 10. Platform-wide cross-cutting features

- **RBAC** — role-based access control (`ADMIN` / client roles) enforced at the API layer
  via Spring Security (`SecurityConfig`).
- **Rate limiting** — `RateLimitFilter` backed by Redis, applied to sensitive endpoints
  (login, password reset, etc.) to slow down credential-stuffing/brute-force attempts.
- **Request correlation IDs** — `RequestIdFilter` tags each request for tracing across
  logs.
- **Audit logs** (migration V22) — a structured, queryable record of security- and
  business-sensitive actions across the platform, surfaced in the admin UI (§5).
- **Production secret validation** — `StartupSecretsValidator` fails the application at
  startup in production if required secrets (JWT signing key, DB/Redis passwords,
  Cloudinary/Razorpay secrets, MFA encryption key) are missing, too short, or an obvious
  development default — rather than silently starting in an insecure state.
- **CORS configuration** scoped to the actual frontend origin(s), not wildcarded.
- **API documentation** — OpenAPI/Swagger UI describing the backend's REST surface
  (`OpenApiConfig`).

## 11. Infrastructure & operations

- **Dockerized stack** — Postgres, Redis, Spring Boot backend, Angular SSR frontend, and
  an nginx reverse proxy, with a separate local-dev (`docker-compose.yml`) and
  production (`docker-compose.prod.yml`) topology.
- **Database migrations via Flyway** — 30 versioned migrations (`V1`–`V30`) covering
  schema evolution, security hardening, and blog seed content; see the migration list in
  `backend/src/main/resources/db/migration/` for the full history.
- **Cloudflare Tunnel deployment** — production traffic reaches the Oracle VM through a
  Cloudflare Tunnel to a loopback-only port, with nginx routing `/` to the SSR frontend
  and `/api/*` to the backend; no database, cache, or backend port is exposed publicly
  (see `docs/DEPLOY-ORACLE-CLOUDFLARE.md`).
- **SHA-pinned, health-gated deploys with automatic rollback** — `infra/deploy/deploy.sh`
  deploys a specific image tag, waits for Postgres/Redis/backend/frontend/nginx to report
  healthy, runs a smoke test against the live site, and automatically rolls back to the
  last known-good tag if either check fails.
- **CI/CD pipeline** (`.github/workflows/ci-cd.yml`) — secret scanning, dependency
  scanning (backend + frontend), static analysis, backend tests, frontend build/test,
  container scanning (Trivy), a full Playwright E2E run against a disposable CI-local
  stack (with its own bootstrapped admin — see §7), then image build/push to GHCR and
  deploy, gated on all of the above passing.
- **Backup & restore** — a `pg_dump`-based backup drill with compression, retention, and
  an optional pluggable offsite-upload command (`OFFSITE_UPLOAD_CMD`), restorable into an
  isolated Postgres instance with Flyway and data-integrity verification and measured
  restore time. *(Offsite upload is optional/pluggable, not configured out of the box —
  see the backup scripts under `scripts/` for the exact interface.)*

## 12. Testing

- **Backend** — Maven + Testcontainers-backed PostgreSQL integration tests, plus unit
  tests for auth, payments, quotations, and analytics services.
- **Frontend** — unit specs for guards (`admin.guard`, `auth.guard`), the auth
  interceptor, and the auth service.
- **End-to-end (Playwright)** — five journeys: public funnel, architecture review,
  client dashboard, admin sales management, and Razorpay checkout, run against a
  disposable CI-local stack per the pipeline above.

---

## Known, disclosed limitations

These are called out here rather than glossed over — see `docs/` and the `CHANGES-*.md`
files for the fuller history:

- Mandatory MFA *enrollment* for admins is not yet a hard server-side gate (password
  change is enforced; MFA enrollment currently is not).
- Offsite backup storage is pluggable but not configured by default — a backup left with
  `OFFSITE_UPLOAD_CMD` unset is local-disk-only, which is not sufficient as a standalone
  disaster-recovery story.
- This document describes what the code implements; it does not certify that a full
  `mvn clean verify` / `npm run build` / production Docker Compose run has been executed —
  run those yourself (or in CI) before treating a release as verified.
