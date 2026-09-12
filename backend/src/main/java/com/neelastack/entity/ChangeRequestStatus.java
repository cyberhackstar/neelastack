package com.neelastack.entity;

public enum ChangeRequestStatus {
    /** Just submitted by the client; no cost/timeline impact recorded yet. */
    SUBMITTED,
    /** Staff have attached an estimated cost/timeline impact (see ChangeRequestService#quote) and are waiting on the client's decision. */
    QUOTED,
    /** Client accepted the quote -- see ChangeRequestService#accept. Reachable only from QUOTED, and only by the engagement's own client. */
    ACCEPTED,
    /** Client declined the quote -- see ChangeRequestService#decline. Same access restriction as ACCEPTED. */
    DECLINED,
    /** Work on an ACCEPTED change request has actually been delivered. */
    COMPLETED
}
