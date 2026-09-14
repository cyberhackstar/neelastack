# Neelastack premium experience upgrade — 2026-09-14

## Public booking
- Reworked `/book` into a consultation-led, premium booking entry point.
- Reworked `/book/:slug` into a three-step time/details/confirmation experience.
- Preserved accessible `Email`, `Full name`, `Phone`, and `Company` labels.
- Shows local timezone, Google Meet format, booking reassurance, and secure manage-booking actions.
- Confirmation includes Google Meet, Google Calendar, and secure booking management links.

## Booking administration
- Reworked admin bookings into a booking command center with operational KPIs, lead signal, client snapshot, discovery context, meeting link and next-action controls.

## Proposal engine
- Added premium proposal fields: executive summary, deliverables, roadmap, payment terms, assumptions, exclusions and next steps.
- Added proposal templates so an admin can start from an enterprise-ready structure instead of writing every section from scratch.
- Added total investment preview and branded proposal PDF download from admin.
- Public proposal now has an executive-document layout with commercial clarity, roadmap, proof and acceptance workflow.
- Added secure public proposal PDF endpoint and a PDF link in proposal email.
- Added Flyway V45 migration with nullable proposal presentation fields so existing quotations remain valid.

## Regression safety
- Existing Playwright selector contracts were preserved; the preceding baseline had 21/21 E2E tests passing before this experience upgrade.
- No E2E test was removed or weakened.
