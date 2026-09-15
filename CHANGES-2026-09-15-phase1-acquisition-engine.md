# 2026-09-15 — Phase 1 Client Acquisition Engine

Implemented the first conversion layer for promotion while preserving the existing application flows.

## Added
- `/free-business-audit`: business-owner-focused digital readiness lead magnet.
- Anonymous score endpoint and gated full-report endpoint.
- Business audit leads reuse the existing `inquiries` pipeline, lead scoring, email confirmation/admin alert, executive report dispatch, attribution and HOT-lead booking trigger.
- First-touch attribution now also recognises `?ref=` referral links by storing it as the campaign value when no explicit UTM campaign is present.
- Public inquiry submission now accepts and persists UTM/referrer/landing-page attribution.
- GA4 conversion events for contact submission and booking completion.
- Home, industry detail, navbar and footer CTAs route users into the free audit.
- Free business audit added to sitemap and SSR/prerender configuration.
- Production OG image refreshed at `frontend/src/assets/og/og-image.png`.
- Playwright regression coverage for the new audit score/gate/full unlock flow.

## Compatibility
- No database migration required; audit leads reuse existing inquiry columns.
- Existing `/audit-preview` remains the technical architecture-risk lead magnet.


## 2026-09-15 E2E test hardening follow-up
- Fixed the business-audit Playwright assertion that incorrectly required a native `<option>` placeholder to be visible.
- The test now asserts the Industry select itself is visible, verifies its initial value, selects Gym / fitness business, and verifies the selected value.


## 2026-09-15 E2E selector-value correction
- Corrected the Business Audit Playwright assertion to match the native select option value currently used by the Angular form (`Gym / fitness business`).
- Kept the assertion against the actual selected control rather than attempting to assert visibility of a native `<option>`.
