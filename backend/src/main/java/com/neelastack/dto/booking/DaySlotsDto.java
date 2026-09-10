package com.neelastack.dto.booking;

import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** All bookable slot start-times for a single calendar date, in the requested timezone. */
@Builder
public record DaySlotsDto(
        LocalDate date,
        List<OffsetDateTime> slots
) {}
