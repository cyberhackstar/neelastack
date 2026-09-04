package com.neelastack.dto.mfa;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /admin/mfa/disable -- requires a fresh password AND a current TOTP code, not just a valid session. */
public record MfaDisableRequest(
        @NotBlank String password,
        @NotBlank String code
) {}
