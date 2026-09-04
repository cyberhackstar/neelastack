# Neelastack — implementation status as of this handoff

This file exists because this zip is being handed off **mid-implementation**, at the
person's request, rather than after all five sections of
`neelastack-sales-intel-reconciliation/Master Implementation Prompt` were finished.
Read this before assuming anything below is done — several things are deliberately
incomplete and are flagged as such rather than silently missing.

**Nothing in this codebase has been compiled or run.** Maven Central and npm's registry
overlap only partially with what the assistant's sandbox could reach — `npm install` +
`ng build` were run once (early on, for Section 1) and passed with pre-existing,
unrelated errors only. Every backend change since, and the rest of the frontend, was
reviewed by hand (brace-balance checks, careful re-reading, cross-referencing real
class/field names against the actual codebase) but **never built**. Run `mvn -B clean
verify` (backend) and `ng build` (frontend) before trusting this compiles.

## Done

- **Section 1 — Admin Sales Command Center (P0).** Backend (`revenueByAttribution`,
  follow-up dismiss/snooze + migration `V20`) and frontend (dashboard rewrite) both
  complete. This is the one part of this handoff that was actually built and verified —
  `ng build` passed against it.
- **Section 4, small pieces.** Payment source tagging (`PaymentSource` enum on
  `Invoice`, threaded through `InvoiceService`/`PaymentWebhookProcessor`/
  `PaymentReconciliationService`) and pricing version traceability (`pricingRuleId`/
  `pricingRuleVersion` on `Quotation`) — migration `V21`. Existing tests updated for the
  new method signatures.
- **Section 2 — Audit logs.** Append-only `audit_logs` table (migration `V22`, DB-level
  `REVOKE UPDATE, DELETE` plus a raising trigger as backstop), `AuditLogService` as the
  single write path, wired into `InquiryService` (status changes), `QuotationService`
  (create/dispatch/respond), `InvoiceService` (payment-marked-paid, tagged with source),
  `PricingRuleService` (create/update/delete), `ProjectFileService` (delete), and
  `AdminPaymentWebhookController` (replay). Read-only admin query endpoint at
  `GET /api/v1/admin/audit-logs`.
- **Section 2 — MFA (backend only).** Migration `V23`: `users.mfa_enabled` /
  `totp_secret` (AES-256-GCM encrypted at rest via `TotpEncryptionService`) /
  `mfa_enrolled_at`, plus a `mfa_recovery_codes` table. `MfaService` (setup / verify /
  disable / recovery / step-up / force-reset) and `MfaController`
  (`/api/v1/admin/mfa/**`). `StepUpAuthFilter` gates mutating requests to
  invoices/payments/pricing-rules/MFA-disable/force-reset behind a recent TOTP
  assertion. TOTP library (`dev.samstevens.totp:totp:1.7.1`) confirmed as a real,
  current Maven Central artifact via web search before adding it to `pom.xml`.

  **Known, disclosed gaps in MFA:**
  - No frontend UI at all for enrollment/step-up — backend only.
  - Login itself does **not** challenge for a TOTP code — MFA only gates the specific
    high-risk mutation routes via step-up, not the login flow. Deliberately deferred:
    changing `AuthController`/`AuthService` (the core login path) without being able to
    run the auth test suite felt like the wrong risk to take in an unverified pass.
  - `force-reset` is documented in code as needing a superadmin-only role, but this
    codebase only has `ADMIN`/`CLIENT` — today it's reachable by any `ROLE_ADMIN`
    (still step-up-gated and audit-logged). Adding a real superadmin role touches the
    whole authorization model and was out of scope for this pass.
  - `AuthService.logout`/refresh-token revocation is not audit-logged as
    `SESSION_REVOKED` — no admin-facing "revoke someone else's session" endpoint exists
    yet for that action to attach to; self-logout audit-logging every user's own logout
    was judged too noisy to be worth it without that context.

## Done (continued from prior handoff)

- **Section 3 — CI/CD + e2e, now complete** (was partially done in the prior handoff):
  - Fixed a real bug in journey 3 (`03-client-dashboard.spec.ts`): it waited for a
    redirect to `/dashboard` or `/login` after registration, but `register.component.ts`
    always navigates to `/` regardless of outcome — that test would have hung/timed out
    in CI. Corrected.
  - **Journey 4 — admin sales management** (`04-admin-sales-management.spec.ts`): logs
    in as the seeded admin, exercises the Section 1 dashboard rewrite end to end —
    summary stats, sales-intelligence/proposal-intelligence sections (including their
    documented empty-state fallbacks), the revenue-attribution dimension toggle, the
    follow-up panel's mark-done action, and confirms a non-admin is redirected away from
    `/admin` (the real route, confirmed in `app.routes.ts` — not `/admin/dashboard`).
  - **Journey 5 — Razorpay checkout, mocked** (`05-razorpay-checkout.spec.ts`):
    intercepts `checkout.js` and substitutes a fake `window.Razorpay` that computes a
    real HMAC-SHA256 signature (Razorpay's own documented algorithm, matching exactly
    what `InvoiceService#verifyAndConfirmPayment` recomputes via the SDK's
    `Utils.verifyPaymentSignature`) using the same `RAZORPAY_KEY_SECRET` the CI stack is
    configured with. This means the test exercises the real `POST /verify` endpoint and
    a real DB status flip, not a stubbed response. A second case (wrong secret) confirms
    the invoice is never marked PAID and the UI surfaces the error. Fixture setup
    (register client, create engagement + invoice) goes through the real API directly
    via Playwright's `request` fixture rather than driving the admin UI, since journey 4
    already covers that UI.
  - `.github/workflows/ci-cd.yml`'s deploy job: the SSH-to-a-host remote-invocation step
    is still, honestly, a guess — nothing in this repo documents whether production
    deploy is SSH, a self-hosted runner, or a cloud provider's own CLI, and inventing an
    answer would be worse than flagging it. What changed: added a fail-fast step that
    checks all five deploy secrets are set *before* building/pushing images, with an
    explicit error message naming the assumption, instead of failing confusingly deep
    inside the `ssh` command with no image cleanup. **Still needs**: swap this step for
    your actual deploy mechanism once you confirm what it is.
  - `e2e/tsconfig.json` and `@types/node` added to `e2e/package.json` — the two new spec
    files reference `process.env` and Node's `crypto`; this makes them type-check
    cleanly rather than relying on Playwright's bundler to paper over missing types.

## Not started

- **Section 4 — backup/restore drill script.** Needs a real offsite storage target and
  credentials from the business owner before it can be more than a template; per the
  master prompt's own non-negotiable rule, an invented cloud account/bucket would be
  worse than nothing.
- **Section 5 — Growth/SEO/case studies.** Explicitly blocked on real facts (actual
  project outcomes, verifiable metrics) from whoever built ElectroMart / Ladies Apparel
  / GymAI / Car Rental — the master prompt is emphatic that fabricated case-study
  numbers are a liability, not a shortcut, and that instruction was followed here.

## Suggested next steps, in order

1. Actually run `mvn -B clean verify` and `ng build` — nothing here has been compiled.
   (This sandbox has no Maven Central / npm registry access and no local Maven install,
   so this pass could not do it either — every backend change since Section 1 has only
   been hand-reviewed, and the e2e specs have only been syntax/type-checked, never
   actually run against a live stack.)
2. Confirm the real deploy mechanism and update `.github/workflows/ci-cd.yml`'s deploy
   job accordingly — see above.
3. Build the MFA frontend (enrollment flow, QR display, recovery-code modal, step-up
   prompt on 403 responses from the gated routes).
4. Decide on the login-time MFA challenge and superadmin role, if you want those gaps
   closed.
5. Backup/restore drill script — once you have a real offsite target.
6. Case studies — once you have real facts to hand over.

## P0 hardening pass (this handoff) — hand-reviewed, never compiled

Same disclaimer as above applies in full: nothing below has been built. Cross-checked
against the actual code (grep/view, not assumption) before touching anything, and every
change was picked because a real gap was confirmed to exist.

### Done

- **Secure admin bootstrap.** Migration `V24` purges the seeded `admin@neelastack.com` /
  `ChangeMe@123` row. New `AdminBootstrapRunner` (`@PostConstruct`) provisions exactly
  one admin, once, from `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` (see
  `.env.example`) — only if zero admins exist, refuses a password under 12 chars, and
  sets `mustChangePassword=true`. New `MustChangePasswordFilter` blocks every
  authenticated route except `/auth/change-password|logout|refresh` for such an
  account; new `POST /api/v1/auth/change-password` endpoint (+ frontend
  `AuthService.changePassword()`) is the way out. **Not done:** no frontend page/route
  actually calls that endpoint yet — `AuthResponse.mustChangePassword` is threaded
  through so the UI *can* redirect on login, but the redirect + change-password form
  itself still needs to be built.
- **Razorpay concurrency/idempotency.** New `payment_attempts` table (migration `V24`)
  + `PaymentAttempt`/`PaymentAttemptRepository`. `InvoiceService.createOrder` now takes
  a pessimistic row lock (`findByIdForUpdate`) on the invoice, re-checks PAID status
  after acquiring it, and returns the existing live order instead of minting a second
  one if a CREATED attempt already exists — closes the double-tab/retry race that could
  previously orphan a customer's in-flight payment. `verifyAndConfirmPayment` and
  `markPaidFromWebhook` both now tag the matching attempt SUCCEEDED/FAILED. Tests
  updated (`InvoiceServiceTest`) including a new case for the reused-order path.
- **Atomic one-time tokens.** `OneTimeTokenService.consume()` uses Redis `GETDEL`
  instead of GET-then-DELETE — the exact race window from the spec is closed.
- **Session invalidation on security events.** `users.token_version` (migration `V24`)
  embedded in every JWT as `"tv"`; `JwtAuthFilter`/`JwtService.isTokenValid` reject a
  token whose `tv` doesn't match the current DB value. Bumped on password reset,
  self-service change-password, MFA disable, and MFA force-reset — a security event now
  invalidates every outstanding token on every device, not just the current one.
- **Docker registry namespace.** Confirmed real: CI was pushing to
  `${{ secrets.REGISTRY_HOST }}/neelastack-backend:<sha>` while
  `docker-compose.prod.yml` pulls from
  `ghcr.io/${GITHUB_REPOSITORY_OWNER}/neelastack-backend:${IMAGE_TAG}` — two different
  registries, so a deploy would have pulled nothing CI ever pushed. Standardized on
  GHCR (needs no extra registry secrets, matches what compose already expected).
  `.env.example` now documents `GITHUB_REPOSITORY_OWNER`/`IMAGE_TAG`.
- **SSH host key verification.** `StrictHostKeyChecking=no` replaced with
  `=yes` + a pinned `known_hosts` from a new `DEPLOY_KNOWN_HOSTS` secret.
- **SSR routing bug (confirmed real, high-impact).** `/solutions` and
  `/solutions/:slug` were missing from `app.routes.server.ts` entirely — they were
  silently falling into the wildcard 404 rule, meaning Googlebot was served an HTTP 404
  for the site's highest-intent SEO landing pages. Fixed.
- **Dynamic 404 handling.** Confirmed real: `blog-detail`/`portfolio-detail`/
  `solutions-detail` all called `.subscribe()` with no error handler, so a missing DB
  resource either soft-404'd (empty page, HTTP 200) or crashed SSR into a raw 500. New
  `NotFoundService` (sets `noindex` via `SeoService` + the actual HTTP status via
  Angular's `RESPONSE_INIT` token when running server-side) is now wired into all three
  components' error handlers, with a minimal not-found block in each template.
  **Caveat:** `RESPONSE_INIT` (from `@angular/ssr`) could not be verified against the
  exact installed Angular minor version in this sandbox (no npm registry access) — the
  import path was chosen based on where the token is documented to live for Angular 19,
  but confirm `ng build` succeeds before trusting this compiles.
- **Global exception mapping.** Added `ConstraintViolationException`,
  `DataIntegrityViolationException` (→ 409, covers duplicate slugs on
  services/projects/blogs/solutions), and `HttpMessageNotReadableException` handlers —
  these previously fell through to the generic 500 handler.

### Not done / needs a decision

- **Mandatory admin MFA enforcement at login.** `StepUpAuthFilter` already gates
  high-risk *mutations* behind a recent TOTP assertion for accounts that have MFA
  enabled (see Section 2 above), and `MfaService`/`MfaController` exist — but nothing
  currently forces an admin to *enroll* MFA before using the rest of the admin panel.
  The bootstrap admin gets `mustChangePassword=true` but not an equivalent
  `mustEnrollMfa` flag. This is a real gap, not fixed here — combining it with
  `MustChangePasswordFilter`'s pattern would be the natural next step.
- **Fresh-server TLS bootstrap script.** Not written — needs to be scripted/documented
  against whatever the real target host actually is (see the existing "confirm the real
  deploy mechanism" item above); guessing at a Certbot flow without a real host to test
  it against isn't worth the risk of a subtly wrong runbook.
- **Backup/restore automation + alerting, case studies, topic clusters** — unchanged
  from "Not started" above; same reasoning (need real infra targets / real facts).
- Frontend force-password-change UI (see above) and mandatory-MFA-enrollment UI don't
  exist yet.

