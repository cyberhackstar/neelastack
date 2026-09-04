package com.neelastack.dto.mfa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body for POST /admin/mfa/verify (completes setup) and POST /admin/mfa/step-up. */
public record MfaVerifyRequest(
        @NotBlank @Pattern(regexp = "\\d{6}") String code
) {}
