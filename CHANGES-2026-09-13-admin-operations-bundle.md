# Admin operations bundle — 2026-09-13

## Included fixes and improvements

- Added completed payment history under **Admin → Payments & operations**.
- Added a server-generated real `.xlsx` export for completed payments.
- Payment history shows invoice, client, project, amount, source, method, and transaction/UTR reference.
- Razorpay payments use the stored Razorpay payment id; manually verified UPI payments use the verified UTR.
- Added stronger UPI client validation: exact invoice amount, UTR length validation, duplicate pending-claim prevention, and 5 MB proof-image limit.
- Added dynamic UPI intent links so a client can open a supported UPI app with the invoice amount and invoice number prefilled when a VPA is configured.
- Added copy-VPA convenience action.
- Added a client-side task action dialog for tasks flagged **Client action**, plus a secure client endpoint to mark the task completed.
- Client task completion verifies the task belongs to the requested engagement and that the caller is the actual client.
- Client completion records project activity and notifies staff using the GENERAL notification type.
- Admin payment verification now gives a direct MFA-required message rather than a generic 403.
- Team-member image uploads are compressed in the browser when large, capped server-side at 5 MB, and now have a 90-second client timeout so they cannot remain indefinitely in “Saving…”.
- Team-member Cloudinary upload/save flow now logs start/completion and removes a freshly uploaded Cloudinary asset if the DB save fails.
