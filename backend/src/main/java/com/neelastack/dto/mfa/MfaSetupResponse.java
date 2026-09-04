package com.neelastack.dto.mfa;

import lombok.Builder;

/** Returned by POST /admin/mfa/setup. mfaEnabled stays false until /verify succeeds. */
@Builder
public record MfaSetupResponse(
        String manualEntrySecret,
        /** data:image/png;base64,... -- ready to drop straight into an <img src>. */
        String qrCodeDataUri
) {}
