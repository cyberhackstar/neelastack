package com.neelastack.dto.booking;

import lombok.Builder;

import java.math.BigDecimal;

/** One row of the "which source produced how much booked/paid revenue" breakdown. */
@Builder
public record RevenueBySourceDto(
        String source,
        long bookingCount,
        BigDecimal revenue
) {}
