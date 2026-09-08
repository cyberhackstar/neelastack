# Changes: E2E/MFA security alignment + deployment hardening (post fixed-v4 review)

Addresses the external review of `neelastack-fixed-v4.zip`. Verdict was "do not deploy
yet" — the blocker was that the E2E suite hadn't been updated to match the
mustChangePassword + MFA + step-up security model already implemented in the backend.
That is fixed here without weakening any backend security check.

## 🔴 Critical — E2E suite now exercises the real security flow

- **`e2e/tests/helpers/admin-auth.ts` (new)**: shared helper implementing the actual
  login → MFA-challenge → TOTP → step-up flow every admin fixture needs, instead of
  each spec file re-implementing (and re-breaking) it.
- **`e2e/global-setup.ts`**: now also enrolls MFA on the bootstrap admin account once
  per suite run (`/admin/mfa/setup` + `/verify`), using the `otpauth` library to
  generate a real TOTP code from the returned secret. The secret is cached to a
  gitignored local file so repeated local runs against a persisted Postgres volume
  don't hit "MFA already enabled" with no way to recover it; CI's ephemeral volume
  means this enrolls fresh every run there.
- **`e2e/tests/05-razorpay-checkout.spec.ts`**: fixture and both tests now log in with
  a fresh step-up immediately before each high-risk mutation (engagement/invoice
  creation), rather than a single bootstrap-password login that the security layer
  now correctly rejects with 401/403.
- **`e2e/tests/04-admin-sales-management.spec.ts`**: fixed a knock-on break the
  original review didn't call out — once the shared admin account has MFA enrolled,
  its UI login flow now shows a "Two-factor verification" step (see
  `login.component.html`) instead of redirecting straight to `/`. `beforeEach` now
  completes that step with a live TOTP code before continuing.
- **`e2e/package.json`**: added `otpauth` (pure-JS, no native deps) as a dev
  dependency; lockfile regenerated via `npm install`.

No security bypass was introduced anywhere in this pass — every fixture goes through
the same endpoints and checks a real admin user would.

## 🟡 Deployment hardening

- **`infra/deploy/deploy.sh`**:
  - Added `ghcr_login_if_configured()`, run once per deploy, so this VM authenticates
    to GHCR itself using `GHCR_USERNAME`/`GHCR_PAT` from `.env` — previously only the
    CI runner logged in, and `docker compose pull` on the VM had no credential of its
    own for private GHCR packages.
  - Split the smoke test into `smoke_test()` (local-only, hard gate — triggers
    rollback) and `public_smoke_test()` (through Cloudflare, logged as a warning,
    never gates). A Cloudflare/DNS blip can no longer trigger a rollback of an
    otherwise-healthy deploy.
- **`.env.example`**: documented `GHCR_USERNAME`/`GHCR_PAT`.
- **`docs/DEPLOY-ORACLE-CLOUDFLARE.md`**: added the GHCR credential checklist item,
  corrected the stale "known gaps" list (frontend MFA UI, login-time TOTP, and the
  SUPERADMIN role all already exist in this codebase — that list hadn't been updated
  since they were added), and pointed at the two new docs below.
- **`docs/DATABASE-MIGRATIONS.md` (new)**: documents the expand → deploy → contract
  policy required because `deploy.sh`'s rollback only ever reverts the application
  image, never the database schema (Flyway migrations are one-way).
- **`infra/deploy/pin-images.sh` (new)**: resolves `postgres:16-alpine`,
  `redis:7-alpine`, and `nginx:1.27-alpine` to ARM64 digests via `docker buildx
  imagetools inspect`, for pinning `docker-compose.prod.yml`'s currently-mutable
  infra image tags. Deliberately a manual, reviewed script — not run automatically by
  `deploy.sh` — since taking a new pin is a decision, not a side effect of every
  deploy.

## 🟢 Documentation accuracy

- **`backend/src/main/java/com/neelastack/service/MfaService.java`**: fixed a stale
  Javadoc comment on `forceReset()` that still described a "no SUPERADMIN role"
  limitation `Role.SUPERADMIN` had already closed.

## Not done in this pass (flagged, not silently skipped)

- Full digest-pinning of infra images: `pin-images.sh` is provided, but running it and
  committing the resulting digests needs registry access this environment doesn't
  have — do this from a machine with Docker/network access and commit the diff.
- A retroactive audit of `V1`–`V32` migrations against the new expand/contract policy:
  the policy applies going forward, per `docs/DATABASE-MIGRATIONS.md`'s own scope note.
