package com.neelastack.dto.inquiry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record QuotationLineItemDto(
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount
) {}
