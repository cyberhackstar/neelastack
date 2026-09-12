package com.neelastack.entity;

/**
 * How an invoice actually transitioned to PAID -- via the live Razorpay webhook, or
 * self-healed by the reconciliation sweep (see PaymentReconciliationService). Both paths
 * funnel through InvoiceService.markPaidFromWebhook; this just tags which one called it.
 */
public enum PaymentSource {
    WEBHOOK,
    RECONCILIATION,
    /** Marked paid by an admin after manually verifying a direct UPI QR scan-and-pay claim
     *  against the bank statement -- see UpiPaymentService#verify. No gateway commission,
     *  but also no programmatic proof beyond the UTR the client typed in, so this path is
     *  always a deliberate human decision rather than an automatic transition. */
    MANUAL_UPI
}
