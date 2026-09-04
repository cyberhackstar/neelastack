package com.neelastack.dto.inquiry;

import com.neelastack.entity.QuotationStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record QuotationDto(
        UUID id,
        UUID inquiryId,
        String title,
        String scopeSummary,
        List<QuotationLineItemDto> lineItems,
        BigDecimal totalAmount,
        String currency,
        QuotationStatus status,
        LocalDate validUntil,
        String notes,
        String responseReason,
        LocalDateTime respondedAt,
        LocalDateTime sentAt,
        Integer viewCount,
        LocalDateTime lastViewedAt,
        LocalDateTime firstViewedAt,
        LocalDateTime acceptedAt,
        LocalDateTime rejectedAt,
        Long responseTimeHours,
        LocalDateTime createdAt,
        UUID pricingRuleId,
        Integer pricingRuleVersion,
        String serviceCategory
) {}
