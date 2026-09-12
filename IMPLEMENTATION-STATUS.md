# Neelastack Implementation Status — Canonical

This file is the current source of truth. Older CHANGES/FIXES files are historical notes and may describe an earlier state.

## Production-readiness status

| Area | Status | Notes |
|---|---|---|
| Authentication / JWT | COMPLETE | Email verification and token-version invalidation enforced |
| Admin MFA / step-up | COMPLETE | MFA enrollment gate, high-risk step-up and recovery controls |
| Rate limiting | COMPLETE | Redis + bounded local fallback for auth/MFA paths |
| Razorpay | COMPLETE | Checkout, webhook and reconciliation paths present |
| Direct UPI | COMPLETE | Client submission + admin verification UI |
| Payment schedules | COMPLETE | Client visibility + admin creation/invoice raising |
| Project health | COMPLETE | Client health and admin operations summary use backend health engine |
| Action center | COMPLETE | Client dashboard presents actionable items |
| Booking engine | COMPLETE | Types, weekly windows, date overrides, analytics, Calendar connection UI |
| Portfolio case-study CMS | COMPLETE | Service categories and key metrics editable in admin |
| Staff management | COMPLETE | SUPERADMIN invitation, role and enable/disable controls |
| Observability | COMPLETE | Sentry + Actuator metrics/Prometheus endpoint |
| Backup / DR | COMPLETE | Restore drill + systemd timer example + health/backup-age check |
| E2E | IN PROGRESS | Core journeys exist; security/payment/booking regression suite added |

## Required release gate

A deployment is only considered release-ready after local/CI success for:

1. Backend clean build and tests.
2. Angular clean install, build and unit tests.
3. Playwright E2E against the production-like Docker stack.
4. ARM64 Docker image build if the target host is ARM64.
5. Database migrations from a fresh database and from an existing backup restore.
6. `scripts/check-production-health.sh` against the deployed origin.

## Known operational prerequisites

- Configure real `JWT_SECRET`, `MFA_ENCRYPTION_KEY`, database/Redis/mail credentials and OAuth secrets.
- Keep the application origin private behind the trusted proxy/tunnel before trusting proxy IP headers.
- Configure an actual offsite object-storage command for `backup-restore-drill.sh` and enable the supplied systemd timer/cron equivalent.
- Treat `/actuator/prometheus` as an authenticated operational endpoint; do not expose it publicly through the edge proxy.
