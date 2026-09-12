package com.neelastack.dto.auth;

import lombok.Builder;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        String fullName,
        String email,
        String role,
        boolean emailVerified,
        /**
         * True when this response is the result of a fresh registration whose email is not
         * yet verified — accessToken/refreshToken/tokenType are all null in that case (see
         * AuthService#register, security review P1 #1). The client must direct the user to
         * check their inbox (or call /auth/resend-verification) and complete /verify-email
         * before /auth/login will issue real tokens.
         */
        boolean verificationRequired,
        /** True when the account has MFA enabled and login must be completed via POST /auth/login/mfa instead. */
        boolean mfaRequired,
        /** Opaque short-lived challenge token for /auth/login/mfa. Null unless mfaRequired is true. */
        String mfaToken,
        /** True when the account must call /auth/change-password before anything else will succeed (see MustChangePasswordFilter). */
        boolean mustChangePassword
) {}
