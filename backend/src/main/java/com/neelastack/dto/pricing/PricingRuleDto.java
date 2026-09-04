package com.neelastack.dto.pricing;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PricingRuleDto(
        UUID id,
        String serviceKey,
        BigDecimal baseLow,
        BigDecimal baseHigh,
        BigDecimal complexityFactor,
        BigDecimal scaleFactor,
        BigDecimal integrationFactor,
        BigDecimal urgencyFactor,
        boolean active,
        Integer version,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
