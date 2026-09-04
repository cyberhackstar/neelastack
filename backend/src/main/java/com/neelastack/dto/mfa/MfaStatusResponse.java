package com.neelastack.dto.mfa;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record MfaStatusResponse(
        boolean mfaEnabled,
        LocalDateTime mfaEnrolledAt,
        int remainingRecoveryCodes
) {}
