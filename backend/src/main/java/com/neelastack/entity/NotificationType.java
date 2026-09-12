package com.neelastack.entity;

/**
 * Every in-app/email notification the platform sends is one of these. Deliberately mirrors
 * the "Notification engine" event list from the client-workspace review (P0 #2) rather than
 * trying to cover every possible future event up front -- new types get added as new
 * workflows actually wire into {@link com.neelastack.service.NotificationService}.
 */
public enum NotificationType {
    MILESTONE_READY_FOR_APPROVAL,
    MILESTONE_APPROVED,
    MILESTONE_CHANGES_REQUESTED,
    TASK_ACTION_REQUIRED,
    INVOICE_CREATED,
    INVOICE_DUE_SOON,
    INVOICE_OVERDUE,
    INVOICE_PAID,
    UPI_PAYMENT_SUBMITTED,
    UPI_PAYMENT_VERIFIED,
    UPI_PAYMENT_REJECTED,
    PAYMENT_SCHEDULE_INSTALLMENT_DUE,
    CHANGE_REQUEST_QUOTED,
    PROJECT_MESSAGE_RECEIVED,
    GENERAL
}
