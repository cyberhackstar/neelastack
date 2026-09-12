package com.neelastack.dto.engagement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ChangeRequestQuoteRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal estimatedCost,
        String estimatedCostCurrency,
        @Min(0) Integer estimatedTimelineDays
) {}
