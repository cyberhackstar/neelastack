package com.neelastack.dto.paymentschedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentRequest(
        @NotBlank String label,
        @NotNull @Positive BigDecimal amount,
        BigDecimal percentage,
        LocalDate dueDate,
        Integer displayOrder
) {}
