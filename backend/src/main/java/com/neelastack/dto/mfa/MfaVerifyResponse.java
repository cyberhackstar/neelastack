package com.neelastack.dto.mfa;

import lombok.Builder;

import java.util.List;

/** Returned once, immediately after successful enrollment -- recovery codes are never retrievable again after this response. */
@Builder
public record MfaVerifyResponse(
        boolean mfaEnabled,
        List<String> recoveryCodes
) {}
