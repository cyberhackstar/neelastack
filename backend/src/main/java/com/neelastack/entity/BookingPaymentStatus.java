package com.neelastack.entity;

/** Payment state of a (possibly free) booking. See master prompt sections 20/21. */
public enum BookingPaymentStatus {
    NOT_REQUIRED,
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}
