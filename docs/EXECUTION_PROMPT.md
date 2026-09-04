# Neelastack — Master Execution Prompt: 94–96% → 10/10 Revenue-Ready Platform

**Status of this document:** this is not a wishlist — it reflects an actual audit of the
codebase in this repo, file by file, as of this change set. Where something was already
built, it says so and tells you not to rebuild it. Where something was missing, it was
either fixed in this change set (and is marked **DONE**) or scoped precisely for the next
engineering session (marked **NEXT**), with exact file paths, schema, and endpoints —
not vague direction.

---

## 1. Admin Sales Command Center & Frontend Binding — **ALREADY DONE, VERIFIED**

Audit finding: this was **already fully implemented** before this change set. No code
changes were needed or made here.

- `frontend/src/app/core/services/analytics.service.ts` calls `GET /api/v1/admin/analytics/summary`,
  `.../sales-intelligence`, and the follow-up endpoints, typed against
  `AnalyticsSummary`, `SalesIntelligence`, `FollowUpTask` in `content.model.ts`.
- `frontend/src/app/core/services/attribution.service.ts` calls the attribution
  breakdown endpoint and exposes `AttributionBreakdown[]`.
- `frontend/src/app/features/admin/dashboard/admin-dashboard.component.ts` + `.html`
  already render:
  - KPI row: Open Pipeline, **Weighted pipeline (heuristic)** (labeled exactly as
    specified), Won Revenue, Win Rate, Average Deal Size, Average Sales Cycle.
  - Proposal Intelligence panel: unviewed / viewed-awaiting-response counts, response
    velocity.
  - Attribution table by source / medium / campaign / landing page.
  - Follow-Up panel with mark-done and snooze actions wired to the backend.

**If a future session is asked to "build" this again, don't** — read
`admin-dashboard.component.ts` first. If something in it looks wrong, that's a bug fix,
not a rebuild.

---

## 2. Admin MFA — **DONE in this change set**

Audit finding: the backend (`MfaController`, `MfaService`, `StepUpAuthFilter`) already
existed and was correct. The frontend had **zero** MFA UI, and — this was the real gap —
**the login endpoint had no MFA challenge at all**: an MFA-enrolled admin's password
alone was sufficient to get a full session. That's now closed.

### 2a. Backend — login-time MFA challenge (new)
- `AuthResponse` gained `mfaRequired: boolean` and `mfaToken: String` (nullable).
  `POST /auth/login` now returns `mfaRequired=true` + a short-lived challenge token
  instead of real tokens when `user.mfaEnabled`.
- New `POST /auth/login/mfa` (`LoginMfaRequest{ mfaToken, code, useRecoveryCode }`)
  exchanges the challenge token + a TOTP or recovery code for a real `AuthResponse`.
  Implemented in `AuthService.completeMfaLogin()`, reusing `MfaService.stepUp()` /
  `consumeRecoveryCode()` so the validation and rate-limiting logic isn't duplicated.
- Challenge tokens live in Redis via `OneTimeTokenService` under namespace
  `login_mfa`, 5-minute TTL. Read-not-consumed (`OneTimeTokenService.read()`, new
  method) until the code is verified, so a mistyped code doesn't force a fresh login —
  only `invalidate()` on success.
- Rate limit added: `/api/v1/auth/login/mfa` → 10 req/min (same as `/login`) in
  `RateLimitFilter`.
- Test coverage: `AuthServiceLoginMfaTest` (5 cases, pure Mockito, no Redis needed) —
  challenge issuance, successful TOTP completion, recovery-code completion, wrong-code
  non-consumption, expired-token rejection.

### 2b. Frontend — enrollment, step-up, and login integration (new)
- `MfaService` (`core/services/mfa.service.ts`) — thin client for
  `/admin/mfa/{status,setup,verify,disable,recovery,step-up}`.
- `/admin/security` route (`features/admin/security/`) — status view, QR-code
  enrollment, 6-digit verify, **single-show** recovery-code display with a
  "copy all" + explicit acknowledgement gate, and a disable form (password + code).
  Linked from the admin dashboard's CMS-links row.
- `StepUpService` + `StepUpModalComponent` (mounted once in `AppComponent`, same
  pattern as `VerifyBannerComponent`) + `stepUpInterceptor` — catches the exact 403
  body `StepUpAuthFilter` returns on high-risk mutations, prompts for a fresh TOTP
  code via a global modal, and transparently retries the original request exactly
  once (`HttpContextToken` guard against loops).
- `LoginComponent` now branches into a code-entry step when `/login` returns
  `mfaRequired`, with a "lost your device? use a recovery code" toggle, calling the
  new `AuthService.loginMfa()`.
- **Bug found and fixed while here (unrelated to MFA):** `app.routes.server.ts` was
  missing `admin/pricing-rules` and `admin/content/solutions` — in a production SSR
  build these would silently fall through to the `**` wildcard and be served with a
  **404 status header**, breaking two existing admin pages. Both added, plus
  `admin/security`.

**Nothing further needed here.** This closes item 2 completely, including the part of
the original brief ("integrate login-time MFA challenges where configured") that had
no backend support to integrate against — that support now exists.

---

## 3. Backup/Restore Drill — **DONE in this change set**

`scripts/backup-restore-drill.sh` (executable, `set -euo pipefail`, matches the
existing `infra/deploy/deploy.sh` logging/exit-code conventions):

1. `pg_dump`s the live `neelastack-postgres` container, gzip -9's the output to
   `backups/neelastack-<UTC timestamp>.sql.gz`. Refuses to keep a zero-byte dump.
2. Applies a **grandfather-father-son retention** pass over the local backup
   directory (daily for `RETENTION_DAILY_DAYS`, then one/week, then one/month —
   all env-overridable). Documented plainly as a **local simulation** of a real
   object-storage lifecycle policy, not a replacement for one.
3. Calls `OFFSITE_UPLOAD_CMD` if set (e.g. `aws s3 cp --storage-class STANDARD_IA`,
   `rclone copyto :b2:...`) — **no cloud credentials exist anywhere in this repo**,
   so the script does not fake a provider integration; if the var is unset it says
   so loudly rather than pretending an offsite copy happened.
4. Spins up an isolated `postgres:16-alpine` container + isolated Docker network,
   restores the dump into it, runs the **official `flyway/flyway` image** against
   it using the backend's real migration files
   (`backend/src/main/resources/db/migration`), then validates:
   - zero `success = false` rows in `flyway_schema_history`
   - `users`, `projects`, `invoices`, `quotations` all exist and are non-empty
5. Reports the measured wall-clock time as **this run's actual RTO**, and states
   explicitly that RPO is a function of how often this script is scheduled (cron /
   systemd timer on the VM), not something the script itself can enforce.
6. `trap cleanup_staging EXIT` — staging container/network are always removed, pass
   or fail. Exit codes 0–6 are distinct per failure stage (see the file's header).

Usage: `./scripts/backup-restore-drill.sh` (full), `--backup-only`, or
`--drill-only <path>` to re-validate an existing dump without taking a new one.

**NEXT (ops, not code):** wire this into a cron job or systemd timer on the
production VM (e.g. `0 */6 * * * cd /opt/neelastack && ./scripts/backup-restore-drill.sh >> /var/log/neelastack-backup.log 2>&1`)
and alert (email/Slack) on non-zero exit. That scheduling decision belongs in ops
config, not in this script, and needs your actual VM access to set up.

---

## 4. Deep Case Studies & Curated SEO Content Library — **NOT implemented as code; scoped precisely below**

This is the one item where I stopped short of writing code, and here's exactly why:
doing it properly needs two different things, and only one of them is something I can
respons­ibly generate.

- **Schema and wiring** (relational structure, admin UI, public rendering) — this is
  ordinary engineering work, fully specified below, ready to implement directly.
- **Content** — case-study numbers ("35% conversion lift," "₹X saved") and 15–25 pages
  of solution copy. I will not fabricate business outcomes or performance stats for
  ElectroMart, Ladies Apparel, GymAI, or Car Rental. Numbers that didn't come from you
  are dishonest on a page whose entire purpose is proving credibility to a buyer — the
  first prospect who asks a follow-up question would find nothing under it. This section
  gives you the exact schema and a fill-in-the-blank content brief instead of invented
  stats.

### 4a. Deep case study schema (NEXT)

`Project` (`backend/.../entity/Project.java`) currently has `problemStatement`,
`solution`, `outcome` — three flat text fields. Extend it:

```sql
-- V24__project_case_study_depth.sql
ALTER TABLE projects
  ADD COLUMN technical_constraints TEXT,
  ADD COLUMN architecture_overview TEXT,
  ADD COLUMN frontend_details      TEXT,
  ADD COLUMN backend_details       TEXT,
  ADD COLUMN database_details      TEXT,
  ADD COLUMN security_details      TEXT,
  ADD COLUMN performance_tradeoffs TEXT,
  ADD COLUMN business_outcomes     TEXT;  -- only ever filled with real, verifiable numbers
```

Then: add the matching fields to `Project.java`, `ProjectDto`/`ProjectRequest` (whatever
your existing DTO pair is named — check `dto/content/Project*.java`), the admin project
editor form (`features/admin/content/projects/`), and the public project detail template
(render each section only `@if` it's non-blank, so older projects without the new
sections don't show empty headers).

### 4b. Curated solution relations (NEXT)

The brief's `solution.relatedProjects` / `relatedArticles` / `relatedServices` don't
exist as a concept yet — "solution" pages are `TechStackPage`
(`backend/.../entity/TechStackPage.java`). Add explicit join tables rather than a
keyword-overlap query:

```sql
-- V25__solution_curated_relations.sql
CREATE TABLE solution_related_projects (
  solution_id UUID NOT NULL REFERENCES tech_stack_pages(id) ON DELETE CASCADE,
  project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  display_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (solution_id, project_id)
);
CREATE TABLE solution_related_articles (
  solution_id UUID NOT NULL REFERENCES tech_stack_pages(id) ON DELETE CASCADE,
  article_id  UUID NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,  -- confirm actual blog table name
  display_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (solution_id, article_id)
);
CREATE TABLE solution_related_services (
  solution_id UUID NOT NULL REFERENCES tech_stack_pages(id) ON DELETE CASCADE,
  service_id  UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,   -- confirm actual services table name
  display_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (solution_id, service_id)
);
```

(Confirm the exact `blog_posts`/`services` table names against the existing migrations
before running this — don't guess past what's already in the repo.)

Wire: `@ManyToMany` or a small linking-entity pattern on `TechStackPage`, an admin UI
that lets the editor **pick from existing content** (searchable multi-select, not
free text) for each of the three relations, ordered by `display_order`, and swap
whatever keyword-overlap "related content" query currently backs the public solution
page for a direct read of these three tables.

### 4c. Content brief (for you / your team to fill in — not fabricated here)

For each of the 4 named case studies, the schema above has a slot for:
business context (already covered by existing `problemStatement`), technical
constraints, architecture overview, frontend/backend/database specifics, security
approach, performance trade-offs made and why, and — the one that must come from
real records — verified business outcomes (conversion, revenue, latency, cost, or
retention deltas, each traceable to an actual number you can stand behind if a
prospect asks).

For the 15–25 solution pages: prioritize by search intent + your actual delivery
capability (a page you can't back with a real project behind it via §4b's
`relatedProjects` doesn't help conversion — it just dilutes the ones that can).
A reasonable starting list, to be confirmed against what you've actually delivered:
industry verticals (e-commerce, fashion/apparel, fitness/wellness, mobility/rental)
crossed with your actual tech stack pages, plus a small number of pure-service pages
(e.g. "Spring Boot + Angular development," "MVP development for D2C brands"). Each
page should end up with 2–4 real related projects via §4b once populated — that's
the signal that makes a solution page worth ranking for, not the page copy alone.

---

## 5. Logo size — **DONE**

`shared/components/logo/logo.component.scss`: nav mark 40px → 56px (44px at
≤480px viewport), footer mark 52px → 68px.

---

## Path to ₹10L/month — how these pieces actually connect to revenue

None of items 1–5 generate revenue by existing; they remove friction and risk from a
pipeline that already has to close deals to hit ₹10L/month. In order of leverage:

1. **Sales Command Center (done)** is the highest-leverage item on this list precisely
   because it's already live — use the Follow-Up panel's "unopened after 4 days" and
   "viewed 2 days ago, no response" tasks *daily*. A proposal that sits unchased is
   the single most avoidable pipeline leak in a services business at this stage.
2. **MFA (done)** protects the sessions that touch invoices, payments, and pricing
   rules — the things that would actually hurt if compromised. This is risk reduction,
   not growth, but a payment-data or client-data incident at ₹10L/month scale is an
   existential setback, not a line item.
3. **Backup/restore drill (done)** is the same category: it doesn't grow revenue, it
   protects the ability to keep operating at all. Schedule it (see §3 NEXT) and
   actually look at its output monthly — a backup script nobody reads the logs of is
   just a false sense of security.
4. **Curated case studies + solution silos (§4, scoped)** is the actual growth lever
   left on this list — but only once populated with real, verifiable outcomes and
   real project links. Sequence it last, and don't rush the numbers to hit a content
   quota; a handful of case studies you can defend in a sales call outperform 25 pages
   of generic copy that fold under a follow-up question.
