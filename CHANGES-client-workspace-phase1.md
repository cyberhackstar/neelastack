# Client Workspace 2.0 — Phase 1

Scope: the highest-value, most tractable items from the "Client Workspace 2.0" review,
built on what the codebase already had. Everything here is real, wired end-to-end
(migration → entity → service → controller → frontend), not scaffolding.

## What I found already done (no changes needed)

Before writing anything, I read the actual code against the review's 28 sections. Three
things it raised as gaps were already fixed in earlier sessions:

- **Section 3 (file-permission bug)** — `ProjectFileService.delete()` already verifies the
  file belongs to the engagement *and* that the caller is either staff or the uploader. Already
  correct, left untouched.
- **Sections 7–8 (messaging)** — `ProjectMessage`/`ProjectMessageRead` entities, migration
  `V35`, `ProjectMessageService`, and both `ClientEngagementController` and
  `AdminEngagementController` message endpoints (list/send/mark-read/unread-count) already
  existed. The client-side thread UI (`dashboard-detail.component`) already renders it, polls
  every 15s, and already branches on `isAdmin` to hit the admin endpoints — so staff replying
  from the same shared component already worked.
- **The admin "project workspace" (part of sections 15–16)** — turned out to already work
  functionally: `dashboard-detail.component` fully supports an admin viewer (status changes,
  milestones, files, invoices, messages) because `/api/v1/client/**` isn't role-restricted and
  every service method's ownership check already allows `ADMIN`/`SUPERADMIN`. The only thing
  missing was a link *into* it — see below.

## What this pass built

### 1. Admin → project workspace link (`admin-engagements` list)
The admin engagements page was exactly what the review described: a list with a status
dropdown and nothing else. Added an **"Open workspace"** link on every card to
`/dashboard/:id`, which — per the finding above — was already a fully working admin project
view. Also restacked `.project-actions` from a single flex-end row to a column so the new
button and the status dropdown both read cleanly.

Files: `admin-engagements.component.ts/.html/.scss`.

### 2. Client invitation — no pre-existing account required (review Section 5)
This was flagged as "the biggest UX improvement" and was a real, enforced restriction:
`EngagementService.create()` used to throw ("the client must sign up first") if the email had
no account, and the frontend surfaced that as a plain error.

Now, when an admin converts an inquiry/quote into a project for an email with no account:

1. A placeholder `User` is created — `role=CLIENT`, an unusable random-hash password (same
   technique already used by `OAuth2LoginSuccessHandler#createUserFromGoogle` for
   Google-only accounts, since the column is `NOT NULL`), `enabled=false`, and a new
   `invitationPending=true` flag.
2. The engagement is created and attached to that placeholder account immediately — it shows
   up in the (not-yet-real) client's workspace the moment they activate it.
3. An invitation email goes out (`EmailService.sendClientInvitationEmail`) with a 7-day,
   single-use link (`OneTimeTokenService`, same Redis-backed mechanism as password reset —
   no new token table), to `/accept-invitation?token=...`.
4. The client sets a password on that page (`AcceptInvitationComponent` →
   `AuthService.acceptInvitation`), which activates the account (`enabled=true`,
   `emailVerified=true`, `invitationPending=false`) and logs them straight in — no separate
   `/register` step.
5. **Alternate path**: if the client instead clicks "Sign in with Google" using the same
   address before ever opening the invitation email, `OAuth2LoginSuccessHandler` now also
   detects `invitationPending` and activates the account there — Google confirming the
   address is equally valid proof of ownership as the emailed link. Both paths consume/clear
   `invitationPending`; whichever happens first wins, and the other becomes a no-op login.
6. `EngagementRequest` gained an optional `clientName` field, used only for naming this
   placeholder account. If omitted, `EngagementService` falls back to the linked inquiry's
   name, then to the email's local part — the client can always fix it from the "your name"
   field on the accept-invitation page (optional, submitted with the same request).

New/changed backend files: migration `V36__client_invitation.sql`, `User.java`
(`invitationPending`), `EngagementRequest.java` (`clientName`), `EngagementService.java`
(rewrote `create()`, added `inviteClient`/`resolveClientName`/`sendInvitation`),
`AcceptInvitationRequest.java` (new DTO), `AuthService.java` (`acceptInvitation`),
`AuthController.java` (`POST /api/v1/auth/accept-invitation`), `EmailService.java`
(`sendClientInvitationEmail`), `OAuth2LoginSuccessHandler.java` (activate-on-Google-link).

New/changed frontend files: `accept-invitation.component.ts/.html/.scss` (new, modeled on
the existing `reset-password` component), `app.routes.ts` (new public route),
`auth.service.ts` (`acceptInvitation`), `content.model.ts` (`EngagementPayload.clientName`),
`admin-inquiry-detail.component.html/.ts` (updated the "client must already have a
registered account" copy and the generic error fallback, since neither is true anymore).

`AdminEngagementController`'s Swagger `@Tag` description was also corrected — it still said
"the client must already have a registered account."

## Not done in this pass, and why

These are real, substantial features from the review that need their own scoped pass rather
than being squeezed into this one — each is a meaningful chunk of new schema, services, and
UI on its own:

- **Tasks under milestones** (Section 12) — new entity/migration, admin + client UI for
  status/assignee/due-date, no code exists yet.
- **Client approvals / change requests** (Sections 13–14) — new entities, an approval-state
  machine, and (for change requests) a mini-quotation flow tied back into the existing
  quotation/pricing system. Commercially the highest-value P0/P1 items left, but also the
  largest.
- **Notification center** (Section 18) — needs a notification entity/table, a delivery
  mechanism (in-app + the "portal event → notification → email" layering the review
  describes in Section 19), and unread-badge UI on both sides.
- **Client-safe activity timeline** (Section 9) — the review explicitly wants this separate
  from the existing internal `audit_logs` table (which does exist and is solid), so it's a
  new client-facing projection/entity, not a reuse of the audit log as-is.
- **File visibility flag / folders / versioning** (Sections 10–11) — schema change to
  `project_files` plus UI for the visibility toggle and folder view; today every uploaded
  file is visible to both sides.
- **Admin command-center dashboard stats** (Sections 16–17: financials, health score, "client
  action required", unread/pending counts) — the admin workspace link now gets an admin into
  the *existing* per-project view, but that view doesn't yet surface the aggregate
  dashboard-style stats the review mocks up. Needs its own aggregation queries.
- **Contracts/SOW storage, payment schedules, satisfaction scores, health score, SLA metrics,
  shared calendar, time tracking** (P1/P2 list) — untouched, as they're each their own
  feature with product decisions this pass shouldn't make on your behalf (e.g. what counts
  as "at risk" for a health score).
- **Automatic testimonial/case-study conversion on project completion** (Section 21) — ties
  together existing testimonial and portfolio systems but wasn't started this pass.

## Known limitation in what was built

- **Invite race**: if two admins simultaneously create engagements for the same
  not-yet-registered email, both could pass the `findByEmail(...).isEmpty()` check and try to
  insert a `User` with that email — the DB's existing unique constraint on `email` will reject
  the second insert with a 500 rather than a clean error. This is a pre-existing class of race
  in this codebase (the exact same window exists in `AuthService.register`) and wasn't
  introduced here; a proper fix (unique-constraint violation → friendly 409) is a good
  candidate for the next hardening pass rather than this feature pass.

## Not run in this sandbox

Same limitation as every prior pass in this repo: no Maven Central access, so
`mvn -B clean verify` was not run, and no headless Chrome, so `ng test` was not run either.
Please run both before merging. In particular:
- Confirm `V36` applies cleanly and `invitation_pending` round-trips through Hibernate
  (`hibernate.ddl-auto=validate` in prod will fail loudly at startup if it doesn't).
- Manually exercise the invite flow once against a real Postgres + Redis + mail sender:
  create an engagement for a brand-new email from the admin inquiry page, confirm the email
  arrives, click through `/accept-invitation`, confirm login and that the engagement is
  visible on `/dashboard`.
- Manually confirm the Google-sign-in activation path: invite an email, then sign in with
  Google using that same address *before* opening the invitation email, and confirm the
  account activates and the invitation link (now already consumed) shows a clean "invalid or
  expired" error rather than a crash.
