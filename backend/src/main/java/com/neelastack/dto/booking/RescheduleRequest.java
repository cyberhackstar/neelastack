package com.neelastack.dto.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record RescheduleRequest(
        @NotNull OffsetDateTime newStartAt,
        @Size(max = 60) String clientTimezone
) {}
