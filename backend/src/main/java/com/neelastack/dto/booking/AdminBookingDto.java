package com.neelastack.dto.booking;

import com.neelastack.entity.BookingOutcome;
import com.neelastack.entity.BookingPaymentStatus;
import com.neelastack.entity.BookingStatus;
import com.neelastack.entity.LeadTier;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record AdminBookingDto(
        UUID id,
        String bookingNumber,
        UUID meetingTypeId,
        String meetingTypeName,
        UUID inquiryId,
        LeadTier leadTier,
        Integer leadScore,
        String clientName,
        String clientEmail,
        String clientPhone,
        String clientCompany,
        String clientTimezone,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        BookingStatus status,
        BookingOutcome outcome,
        String internalNotes,
        String cancelReason,
        Boolean noShow,
        BigDecimal price,
        String currency,
        BookingPaymentStatus paymentStatus,
        String source,
        String utmSource,
        String utmMedium,
        String utmCampaign,
        String meetingUrl,
        List<Map<String, String>> formResponses,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {}
