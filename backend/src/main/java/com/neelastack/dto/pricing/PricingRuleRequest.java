package com.neelastack.dto.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Admin-portal payload for creating/updating a {@code PricingRule}. {@code baseHigh}
 * is intentionally nullable — see {@code PricingRule} — for service categories that
 * shouldn't get an automatic upper bound at all (enterprise/custom scope).
 */
public record PricingRuleRequest(
        @NotBlank @Size(max = 60) String serviceKey,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal baseLow,
        @DecimalMin(value = "0", inclusive = true) BigDecimal baseHigh,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal complexityFactor,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal scaleFactor,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal integrationFactor,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal urgencyFactor,
        boolean active,
        @Size(max = 300) String notes
) {}
