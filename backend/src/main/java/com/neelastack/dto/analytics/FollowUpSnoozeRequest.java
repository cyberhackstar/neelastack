package com.neelastack.dto.analytics;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Body for {@code POST /admin/analytics/follow-ups/{quotationId}/snooze}. */
public record FollowUpSnoozeRequest(
        @NotNull @Future LocalDateTime until,
        String reason
) {}
