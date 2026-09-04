package com.neelastack.dto.payment;

import jakarta.validation.constraints.NotBlank;

public record PaymentVerificationRequest(
        @NotBlank String razorpayOrderId,
        @NotBlank String razorpayPaymentId,
        @NotBlank String razorpaySignature
) {}
