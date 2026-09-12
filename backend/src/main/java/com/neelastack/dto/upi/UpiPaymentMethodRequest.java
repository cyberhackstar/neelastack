package com.neelastack.dto.upi;

import jakarta.validation.constraints.NotBlank;

public record UpiPaymentMethodRequest(
        @NotBlank String label,
        String vpa,
        String payeeName,
        Integer displayOrder
) {}
