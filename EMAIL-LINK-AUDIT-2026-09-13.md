# Email link audit — 2026-09-13

Verified every transactional email action URL in `EmailService` and every admin/client deep link used by `NotificationService` call sites against the Angular route table.

## Fixed

- Quotation response notice no longer links to the non-existent `/admin/quotations` route. It now links directly to `/admin/inquiries/{inquiryId}`.
- UPI payment submission notification no longer links to the non-existent `/admin/upi-verification` route. It now links to `/admin/payments`.
- Quotation dispatch email is async-safe: `QuotationService.send()` now initializes both the `lineItems` collection and the lazy `Inquiry` association before handing the entity to `EmailService.sendQuotation()`.

## Verified working targets

- Client quotation: `/quote/{publicToken}`
- Password reset: `/reset-password?token={token}`
- Admin invitation: `/accept-admin-invitation?token={token}`
- Client invitation: `/accept-invitation?token={token}`
- Email verification: `/verify-email?token={token}`
- Testimonial request: `/testimonial/{token}`
- Booking view/reschedule/cancel: `/booking/{viewToken}`
- Booking cancellation/no-show rebook: `/book/{meetingTypeSlug}`
- Admin inquiry alerts/follow-up digest: `/admin/inquiries`
- Admin booking alerts: `/admin/bookings`
- Client notification deep links: `/dashboard/{engagementId}` (with optional tabs)
- Admin milestone/approval deep links: `/admin/engagements/{engagementId}`

All corresponding Angular routes exist in `app.routes.ts`, with matching server-route entries where required for direct email-link navigation.
