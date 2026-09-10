package com.neelastack.dto.booking;

import lombok.Builder;

import java.time.LocalTime;
import java.util.UUID;

@Builder
public record AvailabilityWindowDto(
        UUID id,
        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String timezone,
        Boolean isActive
) {}
