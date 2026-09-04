package com.neelastack.entity;

/**
 * How an invoice actually transitioned to PAID -- via the live Razorpay webhook, or
 * self-healed by the reconciliation sweep (see PaymentReconciliationService). Both paths
 * funnel through InvoiceService.markPaidFromWebhook; this just tags which one called it.
 */
public enum PaymentSource {
    WEBHOOK,
    RECONCILIATION
}
