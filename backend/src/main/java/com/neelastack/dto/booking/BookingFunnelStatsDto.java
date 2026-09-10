package com.neelastack.dto.booking;

import lombok.Builder;

/**
 * Inquiry -> booking -> attendance -> proposal -> client conversion funnel (feature
 * 28/29), computed over a given date range from existing inquiries + bookings +
 * quotations + engagements. Rates are 0-100, null when the denominator is 0.
 */
@Builder
public record BookingFunnelStatsDto(
        long inquiries,
        long qualifiedInquiries,
        long booked,
        long attended,
        long proposalsSent,
        long clientsWon,
        Double bookingRate,
        Double attendanceRate,
        Double proposalRate,
        Double closeRate
) {}
