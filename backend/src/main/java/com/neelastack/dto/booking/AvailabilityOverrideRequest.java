package com.neelastack.dto.booking;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityOverrideRequest(
        @NotNull LocalDate overrideDate,
        boolean isAvailable,
        LocalTime startTime,
        LocalTime endTime,
        String reason
) {}
