package com.neelastack.dto.booking;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Builder
public record AvailabilityOverrideDto(
        UUID id,
        LocalDate overrideDate,
        Boolean isAvailable,
        LocalTime startTime,
        LocalTime endTime,
        String reason
) {}
