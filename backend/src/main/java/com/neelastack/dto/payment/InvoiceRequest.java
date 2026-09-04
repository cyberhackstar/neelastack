package com.neelastack.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceRequest(
        @NotNull UUID engagementId,
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount,
        String currency,
        LocalDate dueDate
) {}
