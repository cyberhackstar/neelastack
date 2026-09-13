# Team Members CMS + current-log hardening — 2026-09-13

## SUPERADMIN team-member management

Added a dedicated public Team Member CMS. SUPERADMIN can add, edit, hide/show, reorder and delete public team members from `/admin/team`.

Each member supports:
- Name
- Role
- Bio
- Comma-separated skills
- Display order
- Active/hidden state
- JPG/PNG/WebP profile photo, max 5MB

Photos are uploaded to Cloudinary under `neelastack/team`. Replacing a photo removes the previous image from Cloudinary after the database update succeeds; deleting a team member also removes its managed Cloudinary image.

## Public Team page

`/team` now reads active team members from `/api/v1/public/team-members`, so SUPERADMIN changes appear on the public page without a frontend code edit or redeploy. The route is server-rendered rather than build-time prerendered because the data is live CMS content. A static fallback remains in the browser if the public API is temporarily unavailable.

Existing three public members are seeded into the new table with their current Cloudinary URLs so the migration is non-breaking.

## Authorization

The backend endpoint is explicitly SUPERADMIN-only in the service layer even though `/api/v1/admin/**` is broadly available to ADMIN accounts. The Angular `/admin/team` route also has a dedicated `superAdminGuard`.

## Current production log hardening

The supplied production log still contains two `GET /api/v1/admin/analytics/follow-ups` `LazyInitializationException` entries at 05:23 and 05:23:56 on 2026-09-13, plus a transient Gmail SMTP `454-4.7.0` health-check warning. The latest source already had the `Inquiry` entity graph on the affected quotation queries; this update additionally wraps `AnalyticsService.followUpTasks()` in a read-only transaction so the DTO conversion cannot access the lazy `Inquiry` proxy outside the transaction.

The SMTP warning is a remote/provider authentication failure from the health check and is not caused by the team-member feature. It should be rechecked after deployment using the real production mail credentials.

## Production hardening
- Backend now requires a profile photo on Team Member creation (API and UI contract are aligned).
- Backend validates display order to a bounded non-negative range.
- Admin UI performs client-side Team Member photo MIME and 5 MB size validation before upload.
- Added `TeamMemberServiceTest` coverage for required-photo and display-order validation.
