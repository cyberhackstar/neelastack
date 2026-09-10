package com.neelastack.entity;

/** Post-meeting sales outcome, set by the admin. See master prompt section 34. */
public enum BookingOutcome {
    QUALIFIED,
    PROPOSAL_REQUESTED,
    FOLLOW_UP,
    NOT_A_FIT,
    WON,
    LOST
}
