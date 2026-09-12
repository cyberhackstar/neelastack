package com.neelastack.exception;

/**
 * Thrown by AuthService#login when a CLIENT account has a correct password but has not
 * verified its email address. Chosen policy (security review P1 #5, "Option A"):
 * unverified accounts cannot log in at all -- they must use /api/v1/auth/resend-verification
 * and /api/v1/auth/verify-email first. This closes the previously-open gap where
 * verification existed as a mechanism but wasn't an enforced business rule, so an account
 * could register and then log in indefinitely without ever verifying.
 */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
