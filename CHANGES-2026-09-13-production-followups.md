# Production follow-up fixes — 2026-09-13

## Fixed

- Corrected `StaffService` from the nonexistent `/api/v1/admin/staff` endpoint to the implemented `/api/v1/admin/staff-management` endpoint.
- Mapped the richer `AdminStaffDto` response to the `StaffSummary` shape required by project task assignment.
- Added a focused Angular unit test to prevent regression of the staff endpoint contract.
- Added `blob:` to the SSR Content-Security-Policy `img-src` directive so local `URL.createObjectURL(...)` team-photo previews are permitted before Cloudinary upload.

## Not treated as application defects

- `chext_driver.js` / `chext_loader.js` `startTime` errors are browser-extension injected scripts, not Neelastack application bundles.
- Historical `/admin/analytics/follow-ups` 500 entries predate the latest deployment restart; verify the endpoint again against a fresh post-deploy request.
