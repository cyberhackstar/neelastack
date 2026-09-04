package com.neelastack.entity;

/** High-risk mutation types recorded by AuditLogService. See master prompt Section 2. */
public enum AuditAction {
    LEAD_STATUS_CHANGE,
    QUOTATION_CREATED,
    QUOTATION_DISPATCHED,
    QUOTATION_RESPONDED,
    INVOICE_UPDATED,
    PAYMENT_MARKED_PAID,
    WEBHOOK_REPLAYED,
    FILE_DELETED,
    MFA_MODIFIED,
    SESSION_REVOKED,
    PRICING_RULE_UPDATED,
    TESTIMONIAL_REQUEST_QUEUED,
    TESTIMONIAL_SUBMITTED
}
