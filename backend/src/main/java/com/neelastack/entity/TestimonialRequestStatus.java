package com.neelastack.entity;

/**
 * Lifecycle of a post-invoice testimonial request (see {@code TestimonialService}).
 * PENDING is the only state from which a client link can still be submitted —
 * every transition out of it is a one-time, atomic consumption of the token.
 */
public enum TestimonialRequestStatus {
    PENDING,
    SUBMITTED,
    DECLINED,
    EXPIRED
}
