package com.neelastack.service;

import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.repository.MfaRecoveryCodeRepository;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.TotpEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Security review P1 #2: a Redis outage must not silently disable MFA brute-force
 * protection. Previously enforceRateLimit() logged the Redis failure and let the attempt
 * straight through, which meant unlimited TOTP guesses against an account's secret for as
 * long as the outage lasted. This covers the local, per-instance fallback counter it now
 * falls back to instead — pure Mockito, no Spring context or real Redis needed, same
 * pattern as AuthServiceLoginMfaTest.
 */
class MfaServiceRateLimitTest {

    private TotpEncryptionService totpEncryptionService;
    private MfaService mfaService;

    // A syntactically valid base32 TOTP secret (RFC 4648 alphabet) — the real DefaultCodeVerifier
    // used inside MfaService needs to decode *something* plausible even though the test only
    // cares that the submitted code ("000000") doesn't match it.
    private static final String DUMMY_SECRET = "JBSWY3DPEHPK3PXP";

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        MfaRecoveryCodeRepository recoveryCodeRepository = mock(MfaRecoveryCodeRepository.class);
        totpEncryptionService = mock(TotpEncryptionService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Simulate Redis being completely unreachable for the rate-limit counter itself.
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Connection refused"));

        when(totpEncryptionService.decrypt(any())).thenReturn(DUMMY_SECRET);

        mfaService = new MfaService(
                userRepository, recoveryCodeRepository, totpEncryptionService,
                passwordEncoder, auditLogService, redisTemplate);
    }

    private User mfaEnabledUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .fullName("Admin User")
                .email("admin@neelastack.com")
                .password("hashed")
                .role(Role.ADMIN)
                .emailVerified(true)
                .mfaEnabled(true)
                .totpSecret("encrypted-secret")
                .build();
    }

    @Test
    void verifyLoginTotp_fallsBackToLocalCounter_whenRedisUnreachable() {
        User user = mfaEnabledUser();

        // The first MAX_ATTEMPTS (5) guesses must still be evaluated -- they fall through
        // to the real TOTP check and fail on an invalid code, exactly as they would with a
        // healthy Redis counter allowing the attempts through.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> mfaService.verifyLoginTotp(user, "000000"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid code");
        }

        // The 6th attempt must be stopped by the local fallback limiter itself -- never
        // reaching the TOTP check at all. This is the regression P1 #2 guards against: with
        // the old fail-open behavior this call would also just report "Invalid code."
        assertThatThrownBy(() -> mfaService.verifyLoginTotp(user, "000000"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Too many MFA attempts");
    }
}
