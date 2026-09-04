package com.neelastack.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for POST /auth/login/mfa — exchanges the mfaToken issued by /auth/login (when
 * the account has MFA enabled) for real tokens. Set useRecoveryCode when the person
 * is using a single-use recovery code instead of their authenticator app.
 */
public record LoginMfaRequest(
        @NotBlank String mfaToken,
        @NotBlank String code,
        boolean useRecoveryCode
) {}
