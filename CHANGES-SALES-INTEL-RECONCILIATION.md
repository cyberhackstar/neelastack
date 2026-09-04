# Sales intelligence + payment reconciliation pass

Scope note: this pass implements the two most self-contained, credential-free items from
the "10/10" master prompt — proposal lifecycle analytics / weighted pipeline (prompt
section 2) and the payment reconciliation daemon (prompt section 3, bullet 1). Both are
pure backend code against the existing schema/integrations; neither needed new
infrastructure, secrets, or a business/content decision only you can make. See the bottom
of this file for the rest of the master prompt and why it's still open.

## Sales intelligence & proposal lifecycle analytics

- **New quotation lifecycle timestamps** (migration `V19`, `Quotation` entity):
  `firstViewedAt` (set once, first public-token view), `acceptedAt`/`rejectedAt`
  (query-friendly split of the existing `respondedAt` + status). `sentAt`, `viewCount`,
  `lastViewedAt` already existed from earlier passes — this fills the remaining gap
  against the "granular proposal lifecycle metrics" field list.
- **`QuotationService`** now populates these on view (`firstViewedAt`, once) and on
  accept/reject (`acceptedAt`/`rejectedAt`). `QuotationDto` exposes all of them plus a
  computed `responseTimeHours`.
- **`AnalyticsService.salesIntelligence()`** (new): weighted pipeline
  (`openPipelineValue × stage probability`), win rate, average deal size, average
  sales-cycle duration (sentAt → acceptedAt), average time-to-first-view, and
  unviewed/viewed-awaiting-response counts. Weighting uses a single hardcoded SENT-stage
  probability (40%) rather than a new per-deal probability column — documented in code as
  a heuristic to revisit once there's enough won/lost volume to fit a real one; inventing
  false precision here would be worse than an honest rough number.
- **`AnalyticsService.revenueBySource()`** (new): groups inquiries by `utmSource`
  (already captured, previously unused for revenue — falls back to "Direct / Unknown"),
  rolls up lead/quoted/won counts and won revenue per source.
- **`AnalyticsService.followUpTasks()`** (new) + **`LeadFollowUpService`**: the automated
  follow-up system — a daily 8 AM job that finds (a) SENT quotations unopened after 3
  days and (b) opened-but-unanswered quotations after 2 more days idle, and emails the
  admin a digest (`EmailService.sendFollowUpDigest`, skipped entirely when nothing's due).
  Deliberately **not** a persisted task/reminder entity — the candidate set is fully
  derivable from existing quotation state at read time, so the daily digest and the new
  on-demand `GET /api/v1/admin/analytics/follow-ups` endpoint are guaranteed to agree,
  with nothing to go stale. Thresholds are configurable
  (`FOLLOWUP_UNVIEWED_REMINDER_DAYS` / `FOLLOWUP_VIEWED_NO_RESPONSE_DAYS`, default 3/2).
- **New endpoints**: `GET /api/v1/admin/analytics/sales-intelligence`,
  `GET /api/v1/admin/analytics/revenue-by-source`, `GET /api/v1/admin/analytics/follow-ups`.
  `GET /api/v1/admin/analytics/summary` is unchanged — additive only, nothing existing
  breaks.

**Not done in this slice**: wiring these three endpoints into the Angular admin dashboard
UI (charts/tables) — that's a frontend pass with its own design decisions (which chart
library, layout) better scoped on its own rather than guessed at here. The API contract
above is stable and ready for it.

## Payment reconciliation daemon

- **`PaymentReconciliationService`** (new): every 20 minutes (`RAZORPAY_RECONCILE_INTERVAL_MS`,
  within the required 15–30 min band), queries every PENDING invoice with a Razorpay order
  already attached and older than a 15-minute grace window (so an in-progress checkout is
  never raced), fetches that order's payments directly from Razorpay
  (`razorpayClient.orders.fetchPayments`), and self-heals to PAID through the *same*
  `InvoiceService.markPaidFromWebhook` path the live webhook uses — so a reconciled
  invoice ends up in an identical state to one confirmed live, not a parallel code path
  that can drift.
- This is a backstop for exactly the failure modes the webhook alone can't cover: a
  dropped webhook delivery, a backend restart mid-processing, or a webhook
  misconfiguration in the Razorpay dashboard — not a replacement for the webhook, which
  stays the fast, primary path.
- New config: `InvoiceRepository.findByStatusAndRazorpayOrderIdIsNotNullAndCreatedAtBefore`.

**I could not compile or run either of these changes in this sandbox** — same limitation
noted throughout `CHANGES-P0.md`: no Maven Central access here, and no live Postgres/
Razorpay to exercise the scheduler against. I'm confident in the `Order`/`Payment` API
shapes used (`orders.fetchPayments`, `Payment.toJson()`) based on the razorpay-java
version already pinned in `pom.xml`, but flagging this the same way the Cloudinary
signed-URL note in the main README did: if it doesn't compile, the error will be
immediate and narrow. Run `mvn -B clean verify` before merging, same as every prior pass.

## Everything else from the master prompt — status and why it's not in this slice

Genuinely can't be done blind/safely in one more pass; each is either a bigger scoped
effort or needs an input only you can provide:

- **E2E suite** (prompt section 1): the Playwright suite you uploaded earlier already
  covers the five journeys listed (public funnel, architecture review, client portal,
  admin management, payment/webhook). Worth a dedicated review pass against the *new*
  endpoints/fields added here (sales-intelligence, revenue-by-source, follow-ups) rather
  than bolting assertions on blind — say the word and I'll do that pass next.
- **Comprehensive audit trail, MFA hardening, CI/CD security gateways, backup/restore
  runbook** (prompt section 3, remaining bullets): each is a real, scoped subsystem —
  audit logging touches every mutating service; MFA touches `User`, `AuthService`,
  `SecurityConfig`, and the login UI; CI/CD gates need your actual `.github/workflows/`
  file (not present in this upload — README references
  `.github/workflows/ci-cd.yml` but it wasn't in the zip); backup/restore needs an actual
  offsite target and RPO/RTO decision from you. Each deserves its own focused pass rather
  than being compressed alongside this one.
- **SEO content library, case studies, curated relationships** (prompt section 4): these
  are content/business decisions (real case-study metrics, which 15–30 solution pages,
  what's actually true about each project) that the existing README already correctly
  flags as **not safe to fabricate** — writing convincing-sounding but invented case study
  numbers would actively damage trust with a prospect who checks. I can build the
  *schema/admin tooling* for `relatedProjects`/`relatedArticles`/`relatedServices` and
  scaffold solution-page templates now if useful; the actual content needs your input.

Tell me which of the above to tackle next and I'll go straight at it with the same
grounded, read-the-actual-code approach as this pass.
