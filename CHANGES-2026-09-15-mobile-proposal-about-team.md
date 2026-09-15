# Changes — 2026-09-15 mobile UX, proposal reliability, navigation, About & Team

- Added a browser-safe mobile form focus-out zoom reset so focus zoom can still occur but does not remain after the user leaves the field.
- Added explicit Home navigation to desktop and mobile primary navigation.
- Hardened quotation validation on the frontend and backend: required commercial narrative, line-item description/amount constraints, currency format, title limits, and non-past validity dates.
- Changed quotation email dispatch to synchronous delivery with bounded SMTP timeouts so an accepted SMTP send is required before a quotation becomes SENT; failures stay actionable instead of silently reporting success.
- Added visible send errors in the admin proposal workspace.
- Reworked About into an enterprise-level brand/story page covering positioning, principles, engineering background, operating model, delivery system, and technical foundation.
- Reworked Team into an enterprise-level people/accountability page with leadership model, collaboration structure, working style, and stronger member presentation.
- Added public E2E coverage for the explicit Home link and mobile form focus-out behavior.
