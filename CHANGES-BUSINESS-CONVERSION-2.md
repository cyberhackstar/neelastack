# Business/conversion changes, part 2 — architecture review, proposal analytics, pipeline

Continuation of `CHANGES-BUSINESS-CONVERSION.md`. Same scope caveats apply: this covers
specific items from the pasted master prompt (22, 51, part of 52-53), not the whole
document, and nothing here has been compiled/run — run `mvn -B clean verify` and
`npm test` before merging.

## Backend

- **Architecture review lead magnet** (section 22): new `InquiryIntent.AUDIT` value,
  `ArchitectureReviewRequest` DTO, `InquiryService.submitArchitectureReview()`,
  `PublicArchitectureReviewController` → `POST /api/v1/public/architecture-review`.
  Deliberately a flat request (no multi-step wizard like the estimator) — this is meant
  to be the lowest-friction entry point, so it doesn't ask for budget or timeline. No
  price estimate is generated for it (a free review isn't a priced engagement).
- **Proposal view tracking** (section 51): `Quotation` gained `viewCount`/`lastViewedAt`
  (migration `V15__quotation_view_tracking.sql`), incremented on every real client open
  via the public token (`QuotationService.recordView`) — not on admin creation, and not
  double-counted when a client accepts/rejects (that path now builds its response DTO
  directly instead of re-calling the view-counting method). This required correcting two
  existing tests whose old assertions ("save is never called on a still-valid view")
  were only true before this feature existed — that's an intentional behavior change,
  not a broken test; three new tests were added alongside the fixed two.
- **Pipeline snapshot** (part of section 52): `AnalyticsSummaryDto` now carries
  `openPipelineValue` (sum of SENT quotation amounts) and `wonPipelineValue` (sum of
  ACCEPTED). This is a lightweight sum, not the full weighted-pipeline/win-rate/
  sales-cycle dashboard the master prompt describes — see "not touched" below. Note that
  `totalRevenueCollected`/`pendingInvoiceAmount` (section 53's "revenue"/"pending
  invoices") already existed in `AnalyticsSummaryDto` before this pass; only the
  quotation-side pipeline figures are new.
- New tests: `AnalyticsServiceTest` (pipeline sum correctness, including the empty case),
  three new `QuotationServiceTest` cases for view tracking.

## Frontend

- **New `/architecture-review` route and page** — single form (name/email/phone/company,
  application URL, current stack, concern checkboxes, notes) with its own success state,
  no wizard. Linked from a new homepage section ("Already have an application?") between
  the estimator CTA band and the footer, and cross-linked from the estimator page for
  anyone in the wrong flow.
- Admin dashboard: two new stat cards for open/won pipeline value.
- Admin inquiry detail: each quotation card now shows "Viewed Nx — last <time>" (or "Not
  yet opened") for SENT/ACCEPTED/REJECTED quotations — the section 51 example
  ("₹3.5L proposal — last viewed 2 hours ago") in miniature.
- `architecture_review_start`/`architecture_review_submit` GA4 events, using the generic
  `trackEvent()` method added in the previous pass.

## Deliberately not touched this pass

The weighted-pipeline/win-rate/sales-cycle dashboard and average-deal-value metric
(section 52 in full), scheduled follow-up automation (day-3 reminders, unopened-proposal
nudges — section 50, beyond the one-time HOT-lead email flag from the previous pass),
admin MFA/audit logging/CI security scanning (64-66, 94), the commercial offer ladder and
retainer pages (54-55, 88), and the entire SEO/content workstream. Same reasoning as
before: these need either scheduled-job infrastructure, security decisions, or business
content only the site owner can supply.
