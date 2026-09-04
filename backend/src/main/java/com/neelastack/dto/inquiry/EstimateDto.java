package com.neelastack.dto.inquiry;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * A preliminary, non-binding estimate range. `low`/`high` are null when the intake
 * describes a custom/enterprise-scale engagement that a rule-based calculator
 * shouldn't attempt to bound automatically — the disclaimer still applies either way.
 */
@Builder
public record EstimateDto(
        BigDecimal low,
        BigDecimal high,
        String currency,
        String disclaimer
) {}
