package com.neelastack.entity;

/** Lifecycle status of a Booking. See booking-engine master prompt section 18. */
public enum BookingStatus {
    SCHEDULED,
    CONFIRMED,
    RESCHEDULED,
    CANCELLED,
    COMPLETED,
    NO_SHOW
}
