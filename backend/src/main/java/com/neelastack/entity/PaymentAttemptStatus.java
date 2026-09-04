package com.neelastack.entity;

/**
 * Lifecycle of a single Razorpay order created for an invoice checkout attempt.
 * CREATED is the only "live" status a new checkout call needs to check for -- see
 * InvoiceService#createOrder.
 */
public enum PaymentAttemptStatus {
    /** Order created, checkout in progress, outcome not yet known. */
    CREATED,
    /** Signature verified and invoice marked PAID against this specific order. */
    SUCCEEDED,
    /** Signature verification failed for this order. */
    FAILED,
    /** A newer attempt was created for the same invoice before this one resolved (e.g. the checkout was abandoned and retried). */
    SUPERSEDED
}
