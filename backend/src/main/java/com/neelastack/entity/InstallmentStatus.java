package com.neelastack.entity;

public enum InstallmentStatus {
    /** Planned, no invoice raised yet. */
    PENDING,
    /** An Invoice has been generated for this installment and is awaiting payment. */
    INVOICED,
    PAID,
    /** Computed at read time (invoiced + past due date), not persisted independently --
     *  see PaymentScheduleService#deriveDisplayStatus. */
    OVERDUE
}
