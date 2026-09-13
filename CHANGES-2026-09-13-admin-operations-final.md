# Admin operations production fixes — 2026-09-13

## Included fixes

- Team member uploads: Nginx allows 10 MB requests; browser compresses larger valid profile images before upload; save requests have a 90s timeout; Cloudinary failures are surfaced instead of leaving the UI in an endless Saving state; newly uploaded Cloudinary assets are cleaned up if the DB write fails.
- Admin UPI method uploads: browser compresses large QR images, enforces JPG/PNG/WebP and 5 MB limits, and times out stalled uploads.
- UPI client flow: exact invoice amount and UTR validation, duplicate pending-claim protection, signed/authenticated payment-proof delivery, UPI deep link support, and copy-UTR/UPI-ID feedback.
- UPI verification: retains MFA/step-up protection; the admin UI explains 403 MFA failures instead of presenting them as generic failures.
- Payment history: added admin API and UI for completed payments across Razorpay and manually verified UPI, plus XLSX export.
- Client task actions: client-action tasks are clickable, open a modal, and can be marked complete through a protected client endpoint. Action-center task cards now directly open the corresponding task even when the task list is still loading.
- Admin task creation: added an optional client instruction/description field so the client action modal can show the requested action.
- UPI method action feedback: Copy UPI ID now displays a real copied state instead of writing a message into the invoice-keyed error map.
- Existing production fixes retained: StaffService uses /admin/staff-management, CSP permits blob: image previews, session-expiry handling, and CI Nginx validation uses the production upstream names safely.

## Verification limitations

- ZIP archive is integrity-tested after creation.
- Full Angular/Maven/Docker runtime verification could not be completed in this environment because dependency installation and Docker/Maven runtime tooling were unavailable/time-limited. The application should still be verified through CI and the production smoke tests after deployment.
