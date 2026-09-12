package com.neelastack.dto.upi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record UpiSubmissionRequest(
        @NotNull UUID upiMethodId,
        @NotBlank String utrReference,
        String payerUpiId,
        @NotNull @Positive BigDecimal amountClaimed
) {}
