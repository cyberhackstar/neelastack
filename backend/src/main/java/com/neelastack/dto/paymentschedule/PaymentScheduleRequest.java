package com.neelastack.dto.paymentschedule;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PaymentScheduleRequest(
        @NotNull UUID engagementId,
        @NotNull @Positive BigDecimal totalAmount,
        String currency,
        @NotEmpty @Valid List<InstallmentRequest> installments
) {}
