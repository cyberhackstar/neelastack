# P0 changes applied on top of v5

Scope note: this pass focused on fixes verifiable by reading the actual code paths, without
new infrastructure, credentials, or product/design decisions. Everything here is a code or
CI/CD change; nothing here required a live Redis, backup target, GA4 property, or new
business feature. See the bottom of this file for what's deliberately still open.

## Backend

- **Cloudinary auth-token-key is now mandatory in the `prod` profile** (`CloudinaryConfig`).
  If `CLOUDINARY_AUTH_TOKEN_KEY` is unset, the app now fails to start in production instead
  of just logging a warning and silently issuing permanently-reusable signed file URLs.

- **Fixed a real authentication bug found while writing the authorization tests below**:
  there was no explicit `AuthenticationEntryPoint`. With `oauth2Login()` configured and no
  entry point set, an unauthenticated request to a protected JSON endpoint could fall
  through to the OAuth2 login entry point and 302-redirect toward Google instead of
  returning 401. Added `RestAuthenticationEntryPoint` (plain JSON 401, same `ApiError` shape
  as every other handled exception) and wired it into `SecurityConfig`. This app's OAuth
  login is only ever initiated by the frontend explicitly navigating to the public
  `/oauth2/authorization/google` endpoint, so the OAuth entry point was never actually
  needed as the unauthenticated-access fallback.

- **New integration tests** (`src/test/java/com/neelastack/integration/`), on top of the
  existing service-layer unit tests:
  - `AuthIntegrationTest` — register, login (success/wrong password/unknown email),
    duplicate-email conflict, weak-password validation, refresh (valid/malformed/wrong
    token type/revoked), logout, and a regression test for the entry-point fix above.
  - `AuthorizationIntegrationTest` — the exact matrix the audit asked for: client A cannot
    read client B's engagement (403), the owning client can, anonymous access is rejected
    (401), a CLIENT-role token cannot call an admin endpoint (403), an ADMIN-role token can,
    and a garbage bearer token is treated as unauthenticated rather than crashing.
  - These load the real Spring context, real security filter chain, real JWT
    issuing/validation, real password hashing, and a real (in-memory H2) database — the gap
    the audit called "business logic can pass unit tests while the HTTP boundary is broken."
  - `TokenRevocationService` (Redis-backed) is the one dependency mocked out via
    `@MockBean`, so these tests don't need a live Redis in CI. That's the only test double
    used; everything else in the request path is real.

  **I could not compile or run these in this sandbox** — its network allowlist covers
  npm/PyPI/crates/GitHub but not Maven Central, so `mvn` can't resolve dependencies here.
  Run `mvn -B clean verify` yourself before merging.

## Frontend

- **First `*.spec.ts` files in the project** (previously zero — the CI job was skipping the
  test step entirely):
  - `auth.guard.spec.ts`, `admin.guard.spec.ts` — redirect/allow behavior for both guards.
  - `auth.interceptor.spec.ts` — Authorization header attached on browser platform with a
    token, absent without one, and never attached during SSR.
  - `auth.service.spec.ts` — session persisted to localStorage on login/register, and fully
    cleared + redirected on logout even when server-side revocation fails.
  - This flips `.github/workflows/ci-cd.yml`'s existing "skip tests if none exist" step into
    an actually-enforcing gate, with no further CI changes needed (that logic was already
    there, just dormant).
  - **Also not run in this sandbox** — no headless Chrome available here. Run
    `npm test -- --watch=false --browsers=ChromeHeadless` yourself before merging.

## CI/CD & deployment

- **SHA-pinned image deploys** (`docker-compose.prod.yml`): `backend` and `frontend` images
  now resolve to `${IMAGE_TAG:-latest}` instead of a hardcoded `:latest`, so production can
  be pinned to an exact, reproducible build.
- **New `infra/deploy/deploy.sh`**: deploys a given tag, polls container health (using the
  healthchecks already baked into both Dockerfiles — nothing new needed there), then runs a
  public HTTP smoke test against the live site (`/api/v1/public/ping`, `/`, `/sitemap.xml`,
  `/robots.txt`). On failure it automatically redeploys the last tag that passed this same
  check, recorded in `.last_good_sha` on the VM, and fails the CI job either way — a bad
  deploy or a rollback can never look green.
- `.github/workflows/ci-cd.yml`'s deploy job now copies and runs this script with
  `${{ github.sha }}` instead of the old bare `pull && up -d`.
- Added `condition: service_healthy` to the `frontend`→`backend` and `nginx`→`frontend`/
  `backend` `depends_on` edges, since the healthchecks already existed but weren't being
  used to sequence startup.

## Deliberately not touched, and why

These need infrastructure, credentials, or product/design calls this pass shouldn't make on
your behalf:

- Backup/restore for PostgreSQL, retention, offsite copy, restore verification (needs an
  actual offsite/object storage target and an RPO/RTO decision).
- Observability metrics/alerting beyond what Sentry + health endpoint already give you
  (needs a metrics backend — Prometheus/Grafana/hosted APM — and alert routing).
- HttpOnly-cookie refresh-token redesign (a real architecture change to how the SPA and API
  communicate, not a drop-in fix — needs CSRF strategy decisions too).
- File-upload hardening beyond the existing Tika magic-byte check (decompression-bomb
  protection, malware scanning needs a scanning service).
- GA4/Search Console wiring, the real OG image, and all SEO content (service pages, case
  studies) — these are content/marketing decisions, not code fixes.
- The whole ₹10L/month business layer — project estimator, lead scoring, follow-up engine,
  proposal analytics, revenue dashboard — these are net-new features, not "hardening," and
  deserve their own scoped design pass rather than being bolted on here.

## Second pass — numbering, webhook idempotency, refresh rotation, Testcontainers

Prompted by a follow-up review of this file. Three of that review's claims didn't hold up
against the actual code and needed no change (noted below); the rest were real and are fixed
here.

**Did not need fixing — already correct:**
- The review's headline "P0": Compose's `depends_on: condition: service_healthy` for
  `backend`/`frontend` supposedly had no healthcheck to evaluate. Not true — both
  Dockerfiles already define `HEALTHCHECK`, and Compose uses an image's own `HEALTHCHECK`
  when the compose file doesn't override it. Nothing to fix here.
- Production secret validation (`StartupSecretsValidator`) — already implemented in the
  first pass above.
- Mandatory `CLOUDINARY_AUTH_TOKEN_KEY` in prod — already implemented in the first pass above.

**Fixed:**
- **Redis had no healthcheck at all** (unlike Postgres/backend/frontend). Added one to both
  `docker-compose.yml` and `docker-compose.prod.yml`, and made `backend` depend on
  `redis: condition: service_healthy` too.
- **Invoice numbering** (`InvoiceService`, `InvoiceRepository`): replaced the
  count-then-retry-on-collision scheme with an atomic per-year counter table
  (`invoice_number_counters`, migration `V11`) incremented via a single
  `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` statement — race-free by construction, no
  retry loop needed. `V11` also backfills the counter from any existing invoice numbers so
  numbering continues where the old scheme left off.
- **Payment webhook idempotency** (`PaymentWebhookEvent`, `PaymentWebhookEventService`,
  migration `V12`): every webhook delivery is now persisted, keyed on Razorpay's own
  `X-Razorpay-Event-Id` header (confirmed via Razorpay's docs as the documented dedup
  mechanism — Razorpay explicitly retries on non-2xx and can send duplicates even on
  success). A request missing that header is rejected outright rather than processed without
  a dedup key. Gives both idempotency and a durable audit trail of every delivery.
- **Refresh-token rotation + reuse detection** (`JwtService`, `TokenRevocationService`,
  `AuthService`): refresh tokens now carry a session-family id. Each successful refresh
  immediately revokes the token it just consumed and issues a new one in the same family. If
  an already-consumed token is replayed — the signature of a stolen refresh token — the
  entire family is revoked, not just that one jti, forcing full re-login instead of leaving a
  possibly-compromised session alive.
- **Redis fail-open → fail-closed for revocation checks** (`TokenRevocationService`): if
  Redis is unreachable, `isRevoked()`/`isFamilyRevoked()` now treat the token as revoked
  rather than valid. This is a deliberate behavior change and a real availability trade-off —
  a Redis outage now blocks refreshes/logins instead of silently letting revocation checks
  pass through. The rate limiter is unchanged and still fails open (protects request-handling
  capacity, not a security-sensitive check). This should be monitored/alerted on in
  production so a Redis outage shows up as a login/refresh disruption, not silent risk.
- **Integration tests moved off H2 onto Testcontainers PostgreSQL**
  (`AbstractIntegrationTest`, `NeelastackApplicationTests`): both now start a real, ephemeral
  `postgres:16-alpine` container via `@ServiceConnection`, with real Flyway migrations and
  `hibernate.ddl-auto=validate` (same as prod), rather than H2 in Postgres-compatibility
  mode. This is what actually proves the Flyway migration set and JPA entity mappings agree
  with a real Postgres schema. The `h2` test dependency is now unused and was removed from
  `pom.xml`; `testcontainers` (`junit-jupiter`, `postgresql`) and `spring-boot-testcontainers`
  were added — versions come from the Spring Boot BOM, none pinned explicitly.
- **New unit tests**: `InvoiceServiceTest` (atomic sequence-based numbering),
  `PaymentWebhookEventServiceTest` (first delivery persists, duplicate event id is a no-op,
  a concurrent duplicate insert is absorbed instead of thrown). **New integration tests** in
  `AuthIntegrationTest`: refresh rotates the old jti, replaying an already-rotated token
  revokes the whole session family, and a family-revoked session is rejected even with an
  otherwise-fresh jti.

**Still not run in this sandbox** — no Docker/network access here, so I could not run
`mvn -B clean verify` (Testcontainers needs to pull `postgres:16-alpine` and talk to a Docker
daemon) or confirm the migrations apply cleanly against real Postgres. Run it yourself before
merging.

**Deliberately not touched in this pass either** — same reasoning as the first pass: the
HttpOnly-cookie refresh-token redesign (still localStorage today — real architecture change,
needs a CSRF strategy decision, touches CORS/interceptors/SSR), file-upload hardening beyond
Tika, observability/backup infrastructure, and all SEO/content/business-layer items.

## Third pass — deploy-script health gate, webhook failure/retry semantics, admin replay

Prompted by a follow-up review of the second pass. Two claims were real and are fixed here;
everything else it flagged as already-fixed (Testcontainers, fail-closed revocation,
production secret validation) was confirmed correct and left alone.

**Fixed:**
- **`deploy.sh` accepted `no-healthcheck` as a passing status** in `wait_for_healthy()`.
  Every container it checks (postgres, redis via `docker-compose.prod.yml`; backend, frontend
  via their own Dockerfiles) genuinely has a real healthcheck today, so this was latent rather
  than actively broken — but it made the gate a silent no-op for any container whose
  healthcheck was ever removed or never added. Now only `healthy` passes.
- **Payment webhook failure handling could permanently strand a paid invoice as unpaid.** The
  previous version recorded a processing failure as `FAILED` but still returned HTTP 200, so
  Razorpay would never retry that delivery, and the idempotency check would then treat any
  later legitimate retry as an already-recorded duplicate and skip it — even though the event
  was never actually handled. Redesigned around a proper claim/process/reclaim state machine
  in `PaymentWebhookEventService`:
  - Only `PROCESSED` is a terminal, skip-forever status. `FAILED`, and a `RECEIVED` row left
    behind by a crash mid-processing, are reclaimed and reprocessed rather than skipped.
  - `PaymentWebhookController` now returns 5xx on a processing failure specifically so
    Razorpay's own webhook retry mechanism drives the reprocessing.
  - **Closed a race in my own first draft of this fix**: an earlier version released the
    pessimistic row lock right after the initial claim step, before running the handler. That
    left a window where two genuinely concurrent deliveries of the same event id could both
    see status `RECEIVED` (because neither had reached a terminal status yet) and both decide
    it was safe to reclaim and process. The lock (`findByRazorpayEventIdForUpdate` /
    `findByIdForUpdate`, both `SELECT ... FOR UPDATE`) is now held for the entire
    claim→process→mark-outcome sequence inside one `@Transactional` method, so a second
    claimant blocks until the first has actually committed a terminal status.
  - `PaymentWebhookProcessor` factors the actual event-type handling (currently just
    `payment.captured` → `InvoiceService.markPaidFromWebhook`) into one place shared by both
    the live endpoint and admin replay, so replay behaves identically to a real redelivery
    rather than a hand-rolled approximation of one.
- **Admin webhook-event visibility + manual replay** (review item #3):
  `AdminPaymentWebhookController` — `GET /api/v1/admin/payments/webhook-events` (paginated,
  filterable by status) and `POST .../{id}/replay`. Replay refuses (400) an already-PROCESSED
  event. New `attempt_count` column (migration `V13`) gives the list view visibility into how
  many times an event has been (re)claimed.
- **New tests**: `PaymentWebhookEventServiceTest` rewritten for the claim/reclaim state
  machine (first delivery, already-processed duplicate, FAILED reclaim, stuck-RECEIVED
  reclaim, concurrent-first-delivery race, replay success/failure/already-processed/not-found).
  `PaymentWebhookProcessorTest` for the event-type dispatch. New
  `PaymentWebhookIntegrationTest` — real HTTP requests against `/api/v1/payments/webhook` and
  the admin replay endpoint, with a genuine HMAC-SHA256 signature (not a mocked check):
  invalid signature, missing event-id header, a real payment.captured delivery marking a real
  invoice PAID, a duplicate delivery being a no-op, and admin replay of a FAILED event.

**Still not run in this sandbox** — same limitation as every prior pass: no Docker/network
access here, so none of this has been compiled, and the webhook integration tests specifically
depend on both the Testcontainers Postgres container and the real `com.razorpay.Utils`
signature-verification code path actually behaving as documented. Run
`mvn -B clean verify` before merging.

**Confirmed already correct, no change needed:** Testcontainers PostgreSQL integration tests,
fail-closed Redis revocation checks, and `StartupSecretsValidator`'s production secret
validation — all from the second pass, all still standing.

**Deliberately not touched, same reasoning as before:** payment-state reconciliation against
Razorpay's API (needs a scheduled job + rate-limit-aware design, not a drop-in fix), the
broader HTTP test-coverage list from this review's item #5 (public/admin/client APIs, rate
limiting, CORS, SEO endpoints, file upload/download, password reset, email verification —
webhook and payment-verification specifically are now covered, the rest isn't yet), frontend
test coverage, the HttpOnly-cookie migration, CI security scanning, nginx hardening, and all
SEO/content/business-layer items.

