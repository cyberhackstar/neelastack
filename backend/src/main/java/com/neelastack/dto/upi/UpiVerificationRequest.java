package com.neelastack.dto.upi;

import jakarta.validation.constraints.NotNull;

public record UpiVerificationRequest(
        @NotNull boolean approve,
        String adminNote
) {}
