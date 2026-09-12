package com.neelastack.entity;

public enum MilestoneStatus {
    PENDING,
    IN_PROGRESS,
    /**
     * A deliverable is ready and the ball is in the client's court (Section 13 of the
     * client-workspace review). Reachable via the same generic admin status-update endpoint
     * as every other status -- the approval flow is opt-in per milestone, not mandatory --
     * but only {@link MilestoneApprovalService#approve} / {@code #requestChanges} can move a
     * milestone OUT of this state on the client's behalf.
     */
    AWAITING_APPROVAL,
    /** Client reviewed an AWAITING_APPROVAL milestone and asked for changes; see MilestoneApproval for the recorded comment. */
    CHANGES_REQUESTED,
    DONE
}
