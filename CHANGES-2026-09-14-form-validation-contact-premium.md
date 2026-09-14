# Changes — 2026-09-14 — Form Validation & Premium Contact UX

## Frontend
- Added visible, inline validation feedback to login, registration, forgot-password, contact, and public booking details forms.
- Added client-side validation parity for registration password rules enforced by the backend.
- Added phone/name/email/message length and format validation where the backend already defines corresponding limits.
- Added accessible `aria-invalid`/described-by state to primary auth/contact fields.
- Added required/optional field affordances and contact message character count.
- Fixed the Contact page `Schedule a call` CTA by importing `RouterLink` into the standalone component.
- Redesigned the Contact page with a premium enterprise-style hero, project brief card, response-status indicator, process panel, direct-email panel, and stronger CTA hierarchy.
- Standardized the scheduling CTA to the primary button treatment.
- Added Playwright smoke coverage for inline validation and the Contact → Schedule flow.

## E2E compatibility hardening
- Preserved the existing E2E suite and form control names; the three new regression tests remain in `e2e/tests/01-public-funnel.spec.ts`.
- Added explicit accessible names (`aria-label`) to the auth/contact inputs used by the existing exact-label Playwright selectors so required-marker text cannot change their accessible names.
- Kept the visible Contact/footer wording unchanged while giving the footer booking link a distinct accessible name, leaving exactly one page-level link matching `Schedule a call` on `/contact`.
- No authentication, payment, booking API, or backend contracts were changed.
