# CHANGES-2026-09-12-followup.md — second-pass security review fixes

Scope: this pass addresses items **P1-1, P1-2, P1-3, P1-9, P1-10** from a second review of
this codebase (a fresh review, not a re-read of `CHANGES-2026-09-12.md` — that first pass's
P0s are confirmed still fixed; this one found new gaps in the same area). See
`IMPLEMENTATION-STATUS.md`'s "2026-09-12 follow-up review" section for the summary table.

## P1-1. `/register` issued live tokens before email verification — FIXED

`backend/src/main/java/com/neelastack/service/AuthService.java`

The first pass's item #5 made `login()` reject an unverified CLIENT account on every sign-in
attempt — but `register()` itself still called `buildAuthResponse(user)` immediately after
creating the (necessarily unverified) account, so a fake/unowned email got a working access
+ refresh token pair before ever proving control of the address. That session just wasn't
renewable by logging back in.

`register()` now returns an `AuthResponse` with `accessToken`/`refreshToken`/`tokenType` all
null, `emailVerified: false`, and a new `verificationRequired: true` flag. No behavior change
to `login()`, `refresh()`, `acceptInvitation()`, `oauthExchange()`, `changePassword()`, or
`completeMfaLogin()` — they all still go through the unchanged `buildAuthResponse()`.

**Frontend** (`frontend/src/app/...`):
- `core/models/user.model.ts` — `AuthResponse.accessToken`/`refreshToken`/`tokenType` are now
  `| null`; added `verificationRequired: boolean`.
- `core/services/auth.service.ts` — `register()` now calls a new
  `persistSessionUnlessUnverified()` (mirrors the existing `persistSessionUnlessChallenged()`
  for MFA) instead of unconditionally persisting; `persistSession()` itself got a defensive
  null-check.
- `features/auth/register/register.component.ts` — on success, navigates to `/check-email`
  (with the email as a query param) instead of `/`.
- New `features/auth/check-email/` component — the "check your inbox" landing step, with a
  resend-verification button (reuses the existing `resendVerification()` call). Registered at
  `/check-email` in `app.routes.ts`.

**Tests updated:**
- `backend/.../integration/AuthIntegrationTest.java` — split the old `registerAndGetBody()`
  helper into `registerRaw()` (register only, asserts no tokens +
  `verificationRequired: true`) and `registerAndGetBody()` (register → mark verified in the
  DB → login, for the refresh/logout tests further down that need a real token pair). Added
  `register_doesNotIssueTokens_verificationRequiredInstead`.
- `frontend/.../auth.service.spec.ts`, `.../admin.guard.spec.ts` — added
  `verificationRequired` to the `AuthResponse` test fixtures; replaced the old "persists
  tokens on registration" test with one matching the real new contract.

## P1-2. MFA rate limiter failed open on a Redis outage — FIXED

`backend/src/main/java/com/neelastack/service/MfaService.java`

`enforceRateLimit()` caught the Redis-unreachable exception and just logged it, letting the
TOTP/recovery-code attempt straight through — unlimited guesses against an account's secret
for as long as the outage lasted. This is exactly the kind of fail-open behavior item #6's
`RateLimitFilter` fallback was built to avoid, just left unaddressed here.

Added a `ConcurrentHashMap`-based local fixed-window counter (same 5-attempts/15-minute
window), used only when the Redis call itself throws. Degraded (per-instance, not shared
across replicas — same trade-off `RateLimitFilter`'s fallback already accepts) but never open.

**New test:** `backend/.../service/MfaServiceRateLimitTest.java` — pure Mockito, forces the
Redis `increment()` call to throw and asserts the 6th attempt is stopped by the fallback
counter (not the underlying TOTP check).

## P1-3. No per-IP brute-force layer on admin MFA endpoints — FIXED

`backend/src/main/java/com/neelastack/filter/RateLimitFilter.java`

`MfaService`'s per-account limit (5/15min) doesn't stop an attacker who holds a valid admin
JWT (or is spraying guesses across several compromised admin accounts) from hammering
`/api/v1/admin/mfa/step-up` and friends from one source. Added a second, looser (20/15min)
per-IP layer for `step-up`/`verify`/`disable`/`recovery`, and added those four paths to
`FAIL_CLOSED_PATHS` (same reasoning as `MfaService`'s own fallback above — a Redis outage
shouldn't silently drop this layer either).

`LIMITS` went from `Map.of(...)` (8 entries) to `Map.ofEntries(...)` (12 entries) since
`Map.of` only has fixed-arity overloads up to 10 key-value pairs.

## P1-9. `StepUpAuthFilter` javadoc overstated its actual scope — FIXED

`backend/src/main/java/com/neelastack/filter/StepUpAuthFilter.java`

The class comment described it as covering "financial/client/project-mutating admin
endpoints", but `HIGH_RISK_PATTERNS` only ever gated invoices/payments/pricing/UPI/payment-
schedules/sessions/MFA-disable — ordinary project/engagement/milestone/task/booking/content
mutations were never in scope, and that's the intended design (requiring a fresh TOTP on
every admin write would make step-up ubiquitous rather than meaningful). Chose the review's
own recommended fix (option B): rewrote the comment to describe the actual, narrower scope,
rather than widening the filter to match the old comment.

## P1-10. `AdminMfaEnrollmentRequiredFilter` coupled to `instanceof User` — FIXED

`backend/src/main/java/com/neelastack/filter/AdminMfaEnrollmentRequiredFilter.java`

Worked only because `JwtAuthFilter` happens to load the real `User` entity as `UserDetails` —
would silently stop detecting anyone the moment that changed to a wrapper/custom principal.
`StepUpAuthFilter` already used the safer `authentication.getName()` → repository lookup
pattern; this filter now matches it (added a `UserRepository` constructor dependency).

## Also fixed: e2e fallout from P1-1

Two Playwright specs assumed `/register` returned a usable token pair directly:
`e2e/tests/03-client-dashboard.spec.ts` and the Razorpay fixture in `e2e/global-setup.ts`.
Both now register, then verify via a new test-only endpoint, then `/login` — same as a real
user, minus clicking an actual email link (the disposable e2e stack has no inbox to click one
from; `MAIL_HOST`/`MAIL_USERNAME` are blank there and `sendVerificationEmail` is
fire-and-forget `@Async`).

New: `backend/src/main/java/com/neelastack/controller/TestSupportController.java` —
`POST /api/v1/test-support/force-verify-email`, gated `@Profile("test")` (mirrors
`RateLimitFilter`'s `@Profile("!test")`). This bean, and therefore this route, does not exist
under `dev` or `prod` profiles — only the disposable e2e stack and the JUnit test classpath
both run with `SPRING_PROFILES_ACTIVE=test`. Added `/api/v1/test-support/**` to
`SecurityConfig`'s `PUBLIC_ENDPOINTS` (inert outside the `test` profile — no controller means
a plain 404 regardless of this matcher).

## Verification status

No Maven, npm, or network access in this sandbox — everything above was checked by careful
manual reading against the actual source (constructor signatures, field/import consistency,
existing test patterns), not by running `mvn test` / `ng test` / Playwright. **Run your own
full build and test suite on this patch before deploying it**, same caveat as item #20 in the
first pass.

## Not addressed in this pass

The frontend feature gaps from the second review (UPI checkout journey, payment-schedule UI,
wiring `ProjectHealthService`/Action Center into the dashboard, booking admin UI) and the
documentation-consolidation item are unstarted — each is a multi-file feature build in its
own right, not a fix, and is better scoped as its own session (same reasoning
`IMPLEMENTATION-STATUS.md` already gives for items #8–18 from the first pass).

## Final completion pass

- Bounded Redis-outage rate-limit fallbacks with active expiry cleanup and a hard key cap for both public auth filtering and MFA service counters.
- Added the client project workspace panels for authoritative server-side project health, action-required items, payment schedules, and direct UPI proof submission.
- Added admin Payments & Operations for UPI method management, UPI verification queue, payment-plan creation/invoice raising, and project-operations health summary.
- Added admin Booking Settings for meeting types, weekly availability, booking funnel metrics, and revenue by source.
- Added navigation/routes for the new product surfaces.
- Kept high-risk financial mutations behind step-up authentication and email verification before normal login/session issuance.
