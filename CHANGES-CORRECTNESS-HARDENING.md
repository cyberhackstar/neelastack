# CHANGES — Post-Review Correctness & Deployment Hardening Pass

This pass addresses the confirmed "release blocker" and "security hardening" findings from
the second-round review (neelastack-p0-hardened.zip audit). Every item below was verified
against the actual source before being changed — file paths and behavior described match
what was actually in the repository, not the review document's assumptions.

**Not done in this pass** (see the end of this file): could not run Maven/Docker/npm in this
sandbox, so nothing here has been compiled or executed. Treat this as source-level, carefully
traced changes that still need a real CI run as the final gate.

---

## Release blockers (fixed)

### 1. `docker-compose.prod.yml` was missing three required backend env vars
`MFA_ENCRYPTION_KEY`, `CLOUDINARY_AUTH_TOKEN_KEY`, and `ADMIN_BOOTSTRAP_*` are now passed
through to the backend service. Without these, `StartupSecretsValidator` and
`CloudinaryConfig` would fail closed on boot, and a fresh production database would end up
with zero admin accounts.

### 2. Email normalization mismatch (register/login)
`AuthService.java` previously checked `existsByEmail(request.email())` (raw) but stored
`request.email().toLowerCase().trim()` — meaning `User@Example.com` could be registered as
a second account distinct from an existing `user@example.com`. Added a single
`normalizeEmail()` boundary used by every lookup, existence check, and persisted value.
`UserDetailsServiceImpl` normalizes defensively too, since it's the generic Spring Security
entry point. New migration `V25__case_insensitive_email_uniqueness.sql` normalizes any
pre-existing rows and adds a case-insensitive unique index as a DB-level backstop.

### 3. CI E2E still referenced the deleted seeded admin
Migration V24 deletes the old `admin@neelastack.com` / `ChangeMe@123` fixture entirely, but
`ci-cd.yml` and both `04-admin-sales-management.spec.ts` / `05-razorpay-checkout.spec.ts`
still hardcoded it — every admin-flow E2E journey would have failed at login. Fixed:
- CI now bootstraps a disposable `admin@neelastack.test` account via `ADMIN_BOOTSTRAP_*`,
  password sourced from a new required repo secret `E2E_ADMIN_BOOTSTRAP_PASSWORD`
  (12+ chars).
- New `e2e/global-setup.ts` runs once before any spec, logs in via the API, completes the
  mandatory post-bootstrap password change, and hands the finalized credentials to all spec
  files via `process.env` — avoiding a race between Playwright's parallel workers changing
  the same account's password at once.

### 4. Forced password-change had no frontend flow
The backend (`AuthResponse.mustChangePassword`, `AuthService.changePassword()`,
`MustChangePasswordFilter`) was ready, but `LoginComponent` always navigated to `/`
regardless, and no `/change-password` route existed. A bootstrapped admin could "log in"
successfully and then get 403'd on every subsequent request. Added:
- `/change-password` route (`authGuard`-protected)
- `ChangePasswordComponent` (form, validation, error handling)
- `LoginComponent` now branches on `mustChangePassword` for both the plain-login and
  MFA-login paths

### 5. `MustChangePasswordFilter` allowed `/refresh`
An account with `mustChangePassword=true` could stay logged in indefinitely via
login → refresh → refresh → ... without ever being forced to change its password. Removed
`/api/v1/auth/refresh` from the filter's allowed-paths list.

---

## Payment correctness

### 6. `verifyAndConfirmPayment` had no row lock
Could race the webhook path (`markPaidFromWebhook`) or a duplicate/retried browser request.
Worst case: an invalid-signature branch could unconditionally overwrite a webhook-confirmed
`PAID` invoice back to `FAILED`. Fixed:
- Takes the same pessimistic row lock (`findByIdForUpdate`) `createOrder` already used
- Idempotent on an already-`PAID` invoice (returns existing state instead of reprocessing)
- Re-checks `PAID` status under the lock before ever setting `FAILED`

### 7. `markPaidFromWebhook` had no row lock
Added `InvoiceRepository#findByRazorpayOrderIdForUpdate` (same lock pattern, keyed by
Razorpay order id) so the webhook path can't race `verifyAndConfirmPayment`.

### 8. Stale `CREATED` payment attempts were returned indefinitely
`createOrder` would hand back the same `CREATED` payment attempt no matter how old — a
customer returning days after abandoning checkout could get an order id Razorpay had
already expired. Added a configurable TTL (`RAZORPAY_ORDER_TTL_MINUTES`, default 60): an
expired `CREATED` attempt is treated as stale and a fresh order is minted instead.

---

## Atomicity fixes

### 9. MFA recovery-code consumption
Was load-all → bcrypt-match-in-Java → unconditional delete. Two concurrent requests
presenting the same code could both match before either delete landed. Replaced with an
atomic single-statement conditional delete (`MfaRecoveryCodeRepository#deleteByIdAtomic`,
`DELETE ... WHERE id = ?`) — a caller that loses the race sees 0 rows affected and is
rejected, exactly like a legitimate replay would be.

### 10. Refresh-token rotation
Was `isRevoked(jti)` (read) then `revoke(jti, ...)` (write) — two round trips with a race
window in between. Replaced with `TokenRevocationService#tryClaim`, a single atomic Redis
`SET ... NX` operation: exactly one of two concurrent callers can ever win it. Fails closed
(treated as unclaimed) if Redis is unreachable, matching the class's existing security
posture for `isRevoked`/`isFamilyRevoked`.

---

## Smaller fixes

### 11. `AdminBootstrapRunner` loaded the entire `users` table on every boot
Replaced `userRepository.findAll().stream().anyMatch(...)` with a new
`UserRepository#existsByRole(Role)` existence-only query.

### 12. `AuthService.readStoredUser()` could crash frontend bootstrap
A corrupted/stale `localStorage` value threw during service initialization. Now wrapped in
try/catch: clears the bad session and continues logged-out instead of crashing.

### 13. Logo rendering size
Increased `.mark` height (nav 56→72px, footer 68→88px, mobile 44→56px) in
`logo.component.scss`. Note: could not fetch the actual Cloudinary-hosted asset from this
sandbox to check for baked-in padding around the mark — if it still looks small after this
change, check whether the source file itself has excess transparent padding and re-export a
tightly-cropped version.

### 14. `.env.example` / `application.yml` gaps
Documented `MFA_ENCRYPTION_KEY`, `CLOUDINARY_AUTH_TOKEN_KEY`, `MFA_ISSUER`,
`MFA_STEP_UP_TTL_MINUTES`, `RAZORPAY_ORDER_TTL_MINUTES`, `RAZORPAY_RECONCILE_INTERVAL_MS` —
all of which were already required/read by the application but not surfaced in the example
env file. Added `app.razorpay.order-ttl-minutes` to `application.yml`.

---

## Verification performed in this sandbox

- `bash -n` on every shell script in the repo — all pass
- YAML parse check on every `.yml`/`.yaml` file — all pass
- Manual brace/paren/structural review of every edited Java file (no Maven available)
- Manual review of every edited TypeScript file against its surrounding types/services

**No Maven build, no `npm run build`, no Docker build, and no actual Playwright run were
possible in this sandbox.** The real gate is still: push this branch, let CI run
(`.github/workflows/ci-cd.yml`) end to end — backend tests, frontend build/tests, Playwright
E2E, container builds — and fix anything that surfaces there before deploying.

## Not addressed in this pass (still open from the review)

- Mandatory admin MFA enrollment (an admin can still exist without MFA enabled)
- `SUPER_ADMIN` role segregation (`MfaService.forceReset()` is documented as super-admin-only
  but no such role exists)
- Email verification is not enforced as a login gate
- Automatic frontend access-token refresh (401 → refresh → retry, single-flight)
- `GlobalExceptionHandler` mappings for `ConstraintViolationException`,
  `DataIntegrityViolationException`, etc. and duplicate-slug 409 handling on update paths
- Immutable pricing versions; analytics moved to DB-level aggregation
- Real offsite backup destination, schedule, and failure alerting (script supports it, isn't
  wired to a real destination)
- First-deploy TLS/Let's Encrypt bootstrap automation
- A dedicated E2E journey covering the bootstrap → forced password change → MFA enrollment
  flow end-to-end
- Deep case-study content and the 15–30 solution-page content library (see the accompanying
  strategy document)
