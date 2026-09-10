package com.neelastack.dto.booking;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BookingDashboardStatsDto(
        long todayCount,
        long upcomingCount,
        long thisMonthCount,
        long cancelledThisMonthCount,
        long noShowThisMonthCount,
        long hotLeadBookingsThisMonthCount,
        BigDecimal revenueThisMonth
) {}
