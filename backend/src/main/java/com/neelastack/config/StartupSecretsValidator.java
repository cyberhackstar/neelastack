package com.neelastack.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail secure, not fail open.
 *
 * Every secret below has a convenient development default in application.yml
 * (e.g. JWT_SECRET falling back to "change-this-to-a-long-random-secret...").
 * That's fine for `dev` — but if the same fallback silently applies in
 * production because an operator forgot to set an env var, the app starts
 * up looking healthy while running with a publicly-known JWT secret, a
 * guessable DB password, or a payment webhook nobody can actually verify.
 *
 * This runs once, only on the `prod` profile, and refuses to start the
 * application context if any required secret is missing or still equal to
 * its known development placeholder.
 */
@Component
@Profile("prod")
@Slf4j
public class StartupSecretsValidator {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.cloudinary.api-secret:}")
    private String cloudinaryApiSecret;

    @Value("${app.razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${app.razorpay.webhook-secret:}")
    private String razorpayWebhookSecret;

    @Value("${app.mfa.encryption-key:}")
    private String mfaEncryptionKey;

    private static final String DEV_JWT_DEFAULT =
            "change-this-to-a-long-random-secret-in-production-min-256-bits";
    private static final String DEV_DB_PASSWORD_DEFAULT = "neelastack";
    private static final String DEV_MFA_KEY_DEFAULT = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=";

    @PostConstruct
    public void validate() {
        List<String> problems = new ArrayList<>();

        if (isBlank(jwtSecret) || jwtSecret.equals(DEV_JWT_DEFAULT)) {
            problems.add("app.jwt.secret (JWT_SECRET) is missing or still set to the development default");
        }
        if (jwtSecret != null && jwtSecret.length() < 32) {
            problems.add("app.jwt.secret (JWT_SECRET) is too short for HS256 — use at least 32 random bytes");
        }
        if (isBlank(dbPassword) || dbPassword.equals(DEV_DB_PASSWORD_DEFAULT)) {
            problems.add("spring.datasource.password (DB_PASSWORD) is missing or still set to the development default");
        }
        if (isBlank(cloudinaryApiSecret)) {
            problems.add("app.cloudinary.api-secret (CLOUDINARY_API_SECRET) is missing");
        }
        if (isBlank(razorpayKeySecret)) {
            problems.add("app.razorpay.key-secret (RAZORPAY_KEY_SECRET) is missing");
        }
        if (isBlank(razorpayWebhookSecret)) {
            problems.add("app.razorpay.webhook-secret (RAZORPAY_WEBHOOK_SECRET) is missing — webhook signature checks cannot run without it");
        }
        if (isBlank(mfaEncryptionKey)) {
            problems.add("app.mfa.encryption-key (MFA_ENCRYPTION_KEY) is missing — TOTP secrets cannot be encrypted at rest without it");
        } else if (mfaEncryptionKey.equals(DEV_MFA_KEY_DEFAULT)) {
            problems.add("app.mfa.encryption-key (MFA_ENCRYPTION_KEY) is still set to the insecure development default");
        } else {
            try {
                if (java.util.Base64.getDecoder().decode(mfaEncryptionKey).length != 32) {
                    problems.add("app.mfa.encryption-key (MFA_ENCRYPTION_KEY) must decode to exactly 32 bytes (AES-256) — generate with `openssl rand -base64 32`");
                }
            } catch (IllegalArgumentException e) {
                problems.add("app.mfa.encryption-key (MFA_ENCRYPTION_KEY) is not valid base64");
            }
        }

        if (!problems.isEmpty()) {
            String message = "Refusing to start with profile 'prod': " + problems.size()
                    + " required secret(s) are missing or insecure:\n  - "
                    + String.join("\n  - ", problems);
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Startup secret validation passed for profile 'prod'.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
