package com.neelastack.dto.mfa;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /admin/mfa/recovery -- consumes one single-use recovery code. */
public record MfaRecoveryRequest(
        @NotBlank String recoveryCode
) {}
