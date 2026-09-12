package com.neelastack.dto.actioncenter;

import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row in the client "Action Required" center (P0 #5) -- a unified, cross-entity list of
 * everything this engagement's client needs to personally do something about right now:
 * a milestone awaiting approval, a client-flagged task, an overdue/due invoice, a payment
 * schedule installment coming due. Deliberately a flat read-model rather than a persisted
 * table -- it's always computed fresh from the existing milestone/task/invoice/installment
 * data so it can never drift out of sync with the underlying records.
 */
@Builder
public record ActionItemDto(
        String kind,
        UUID relatedId,
        String title,
        String description,
        LocalDate dueDate,
        String deepLink
) {}
