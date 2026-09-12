package com.neelastack.entity;

/**
 * Event types shown on the client-facing project activity timeline (Section 9 of the
 * client-workspace review). Deliberately a much shorter list than {@link AuditAction} --
 * this only covers events worth narrating back to a client as project progress, not every
 * security-relevant mutation in the system.
 */
public enum ProjectActivityType {
    ENGAGEMENT_CREATED,
    ENGAGEMENT_STATUS_CHANGED,
    MILESTONE_CREATED,
    MILESTONE_STATUS_CHANGED,
    MILESTONE_APPROVED,
    MILESTONE_CHANGES_REQUESTED,
    TASK_CREATED,
    TASK_STATUS_CHANGED,
    TASK_ASSIGNED,
    FILE_UPLOADED,
    FILE_DELETED,
    INVOICE_CREATED,
    INVOICE_PAID,
    CHANGE_REQUEST_SUBMITTED,
    CHANGE_REQUEST_QUOTED,
    CHANGE_REQUEST_ACCEPTED,
    CHANGE_REQUEST_DECLINED,
    CHANGE_REQUEST_COMPLETED
}
