# UI, Booking, Cache and CSP Fixes — 2026-09-13

## Included

- Reworked Redis cache serialization to use a copy of the application Jackson ObjectMapper and bumped the cache schema namespace from v5 to v6. This fixes Java `LocalDateTime` serialization for cached blog DTOs and isolates the new representation from legacy cache entries.
- Added a public `/book` consultation chooser that loads active meeting types from the existing public booking API. Existing `/book/:slug` booking, availability, confirmation, reschedule, and cancel flows remain unchanged.
- Added Schedule / Schedule a call links to the desktop and mobile navigation.
- Added `/book` and `/book/:slug` to Angular server rendering so the public booking experience is SSR-capable and indexable.
- Made admin booking availability rendering defensive around malformed/missing availability-window values instead of calling `.slice()` directly.
- Moved the pre-CSS theme bootstrap from inline JavaScript in `index.html` to a same-origin static asset, keeping the CSP free of `unsafe-inline` for scripts.
- Allowed the observed Cloudflare Insights script origin in the CSP while retaining the existing Razorpay, Google, and Cloudinary policy.
- Added a deployment-time Nginx SSR cache purge with retries; a failed purge now participates in the health/smoke/rollback gate. This prevents stale cached SSR HTML from referencing asset hashes from a previous frontend image.

## Validation performed in the working copy

- `bash -n infra/deploy/deploy.sh` passed.
- The updated Docker/frontend behavior was previously verified on the supplied environment with the CSP present in the response.
- Full Maven/Angular build was not executed in this container because Maven is not installed here and the npm dependency installation timed out; run the project CI/local test suite before pushing.


## Follow-up hardening pass

- Bumped Redis cache namespace v6 -> v7 so stale values written by the incompatible v6 serializer are never read by the new serializer.
- Refresh-token invalid/unknown-user paths now return the existing controlled 400 error instead of falling into the generic 500 handler.
- Added missing client-rendered routes for booking management and admin child pages so direct browser navigation/refresh does not hit the SSR 404 fallback.
- Separated static asset traffic from the Nginx SSR cache; hashed assets no longer share HTML cache entries, and theme bootstrap has a short cache lifetime.
- Public booking now exposes availability-load failures with an explicit retry action and captures landing URL/referrer for attribution.
- Added public Schedule a call CTAs to the homepage and footer and strengthened small-screen booking layout.


## Appointment UX and production hardening

- Added a public booking CTA to the Contact page and a Google Calendar add-event link to booking confirmation.
- Booking slot times now render explicitly in the visitor's selected/browser timezone.
- Admin payment settings now surface 401/403/loading errors rather than silently rendering empty state.
