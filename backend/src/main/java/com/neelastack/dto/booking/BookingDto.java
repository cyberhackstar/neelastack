package com.neelastack.dto.booking;

import com.neelastack.entity.BookingPaymentStatus;
import com.neelastack.entity.BookingStatus;
import com.neelastack.entity.LocationType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What a client sees about their own booking (via a secure token URL — never the raw
 * booking_number/id). Deliberately excludes internalNotes, outcome, and other
 * admin-only fields.
 */
@Builder
public record BookingDto(
        UUID id,
        String bookingNumber,
        String meetingTypeName,
        String meetingTypeSlug,
        LocationType locationType,
        String meetingUrl,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String clientTimezone,
        BookingStatus status,
        String clientName,
        String clientEmail,
        BigDecimal price,
        String currency,
        BookingPaymentStatus paymentStatus,
        String viewToken,
        String rescheduleToken,
        String cancelToken,
        Boolean cancellable,
        Boolean reschedulable
) {}
