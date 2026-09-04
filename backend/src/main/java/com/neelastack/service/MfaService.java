package com.neelastack.service;

import com.neelastack.dto.mfa.*;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.MfaRecoveryCode;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.repository.MfaRecoveryCodeRepository;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.TotpEncryptionService;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side TOTP MFA for admin accounts (Section 2, master prompt). Setup is
 * two-phase: /setup only stages a secret in Redis (short TTL); it is written to the
 * User row -- and mfaEnabled flips true -- only once /verify confirms the person
 * actually has the code in their authenticator app. This is deliberate: a secret that
 * was generated but never confirmed working must not silently "protect" an account
 * nobody can actually log into.
 *
 * Rate limiting reuses the same Redis fixed-window pattern as RateLimitFilter, but
 * keyed per-account rather than per-IP (5 attempts / 15 min, per master prompt) since
 * this guards a specific account's TOTP secret, not a public endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    private final UserRepository userRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final TotpEncryptionService totpEncryptionService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.mfa.issuer}")
    private String issuer;

    @Value("${app.mfa.step-up-ttl-minutes}")
    private long stepUpTtlMinutes;

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration PENDING_SECRET_TTL = Duration.ofMinutes(10);

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();

    public MfaStatusResponse status(User user) {
        int remaining = recoveryCodeRepository.findByUserId(user.getId()).size();
        return MfaStatusResponse.builder()
                .mfaEnabled(user.isMfaEnabled())
                .mfaEnrolledAt(user.getMfaEnrolledAt())
                .remainingRecoveryCodes(remaining)
                .build();
    }

    /** Generates a new secret, stages it in Redis (never written to the User row yet), returns a scannable QR code. */
    public MfaSetupResponse setup(User user) {
        if (user.isMfaEnabled()) {
            throw new BadRequestException("MFA is already enabled — disable it first to re-enroll.");
        }

        String secret = secretGenerator.generate();
        redisTemplate.opsForValue().set(pendingSecretKey(user.getId()), secret, PENDING_SECRET_TTL);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] png = qrGenerator.generate(qrData);
            String dataUri = "data:" + qrGenerator.getImageMimeType() + ";base64," + Base64.getEncoder().encodeToString(png);
            return MfaSetupResponse.builder().manualEntrySecret(secret).qrCodeDataUri(dataUri).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate MFA QR code", e);
        }
    }

    /** Confirms the first code from the freshly-scanned QR, enrolling the account and issuing recovery codes shown exactly once. */
    @Transactional
    public MfaVerifyResponse verify(User user, String code) {
        enforceRateLimit(user.getId());

        String pendingSecret = redisTemplate.opsForValue().get(pendingSecretKey(user.getId()));
        if (pendingSecret == null) {
            throw new BadRequestException("No pending MFA setup found — call /setup again (it may have expired).");
        }
        if (!codeVerifier.isValidCode(pendingSecret, code)) {
            throw new BadRequestException("Invalid code — check your authenticator app and try again.");
        }

        user.setTotpSecret(totpEncryptionService.encrypt(pendingSecret));
        user.setMfaEnabled(true);
        user.setMfaEnrolledAt(LocalDateTime.now());
        userRepository.save(user);
        redisTemplate.delete(pendingSecretKey(user.getId()));

        List<String> recoveryCodes = issueRecoveryCodes(user.getId());
        grantStepUp(user.getId());

        auditLogService.recordBestEffort(AuditAction.MFA_MODIFIED, "User", user.getId().toString(), Map.of("op", "enabled"));

        return MfaVerifyResponse.builder().mfaEnabled(true).recoveryCodes(recoveryCodes).build();
    }

    /** Requires a fresh password AND a current TOTP code — not just a valid session — per master prompt. */
    @Transactional
    public void disable(User user, String password, String code) {
        if (!user.isMfaEnabled()) {
            throw new BadRequestException("MFA is not enabled on this account.");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("Incorrect password.");
        }
        enforceRateLimit(user.getId());
        if (!codeVerifier.isValidCode(totpEncryptionService.decrypt(user.getTotpSecret()), code)) {
            throw new BadRequestException("Invalid code.");
        }

        user.setMfaEnabled(false);
        user.setTotpSecret(null);
        user.setMfaEnrolledAt(null);
        // Security event: invalidate every outstanding access/refresh token for this account
        // on every device, since MFA being turned off changes what a stolen password alone
        // can now do.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUserId(user.getId());
        redisTemplate.delete(stepUpKey(user.getId()));

        auditLogService.recordBestEffort(AuditAction.MFA_MODIFIED, "User", user.getId().toString(), Map.of("op", "disabled"));
    }

    /**
     * Superadmin-only in intent (master prompt: "an admin locked out ... needs an
     * out-of-band reset path"). This codebase currently has only ROLE_ADMIN, not a
     * separate superadmin role — that's a real gap, flagged rather than silently
     * assumed away: until a superadmin role exists, this is reachable by any
     * ROLE_ADMIN account (still step-up gated, still audit logged), which is weaker
     * than the master prompt's "superadmin-only" intent.
     */
    @Transactional
    public void forceReset(UUID targetUserId, User actingAdmin) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BadRequestException("User not found: " + targetUserId));

        target.setMfaEnabled(false);
        target.setTotpSecret(null);
        target.setMfaEnrolledAt(null);
        // Security event: force every session for the target account to re-authenticate.
        target.setTokenVersion(target.getTokenVersion() + 1);
        userRepository.save(target);
        recoveryCodeRepository.deleteByUserId(target.getId());
        redisTemplate.delete(stepUpKey(target.getId()));
        redisTemplate.delete(pendingSecretKey(target.getId()));

        auditLogService.recordBestEffort(AuditAction.MFA_MODIFIED, "User", target.getId().toString(),
                Map.of("op", "force-reset", "by", actingAdmin.getEmail()));
    }

    /**
     * Consumes one single-use recovery code; on success it counts as a step-up assertion
     * (equivalent to a successful TOTP challenge).
     *
     * Fixed race: matching candidates in Java (bcrypt hashes aren't queryable) then deleting
     * unconditionally meant two concurrent requests presenting the same code could both match
     * before either delete landed. Now the delete itself is the atomic claim — see
     * MfaRecoveryCodeRepository#deleteByIdAtomic — and a caller that loses the race falls
     * through to the next candidate (in the rare case bcrypt somehow matched more than one
     * row) or ultimately gets the same "invalid or already-used" error a legitimate replay
     * would get.
     */
    @Transactional
    public void consumeRecoveryCode(User user, String rawCode) {
        if (!user.isMfaEnabled()) {
            throw new BadRequestException("MFA is not enabled on this account.");
        }
        enforceRateLimit(user.getId());

        List<MfaRecoveryCode> candidates = recoveryCodeRepository.findByUserId(user.getId()).stream()
                .filter(c -> passwordEncoder.matches(rawCode, c.getCodeHash()))
                .toList();

        boolean consumed = false;
        for (MfaRecoveryCode candidate : candidates) {
            if (recoveryCodeRepository.deleteByIdAtomic(candidate.getId()) == 1) {
                consumed = true;
                break;
            }
            // deleteByIdAtomic returned 0: a concurrent request already claimed this exact
            // row between our SELECT and this DELETE — try the next candidate, if any.
        }

        if (!consumed) {
            throw new BadRequestException("Invalid or already-used recovery code.");
        }

        grantStepUp(user.getId());

        auditLogService.recordBestEffort(AuditAction.MFA_MODIFIED, "User", user.getId().toString(),
                Map.of("op", "recovery-code-consumed"));
    }

    /** Refreshes the step-up assertion for an already-enrolled user ahead of a high-risk mutation. See StepUpAuthFilter. */
    public void stepUp(User user, String code) {
        if (!user.isMfaEnabled()) {
            throw new BadRequestException("MFA is not enabled on this account.");
        }
        enforceRateLimit(user.getId());
        if (!codeVerifier.isValidCode(totpEncryptionService.decrypt(user.getTotpSecret()), code)) {
            throw new BadRequestException("Invalid code.");
        }
        grantStepUp(user.getId());
    }

    public boolean hasActiveStepUp(UUID userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(stepUpKey(userId)));
        } catch (Exception e) {
            // Fail closed: same reasoning as TokenRevocationService — a Redis outage should
            // not silently disable the step-up requirement on financial/security mutations.
            log.error("Redis unreachable while checking MFA step-up — failing closed: {}", e.getMessage());
            return false;
        }
    }

    private void grantStepUp(UUID userId) {
        redisTemplate.opsForValue().set(stepUpKey(userId), "1", Duration.ofMinutes(stepUpTtlMinutes));
    }

    private List<String> issueRecoveryCodes(UUID userId) {
        recoveryCodeRepository.deleteByUserId(userId);
        String[] raw = recoveryCodeGenerator.generateCodes(RECOVERY_CODE_COUNT);
        for (String code : raw) {
            recoveryCodeRepository.save(MfaRecoveryCode.builder()
                    .userId(userId)
                    .codeHash(passwordEncoder.encode(code))
                    .build());
        }
        return List.of(raw);
    }

    private void enforceRateLimit(UUID userId) {
        String key = "mfa_attempts:" + userId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ATTEMPT_WINDOW);
            }
            if (count != null && count > MAX_ATTEMPTS) {
                throw new BadRequestException("Too many MFA attempts — try again in a few minutes.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            // Redis unavailable for the counter itself — fail open on rate limiting specifically
            // (same trade-off as RateLimitFilter): don't let a Redis outage lock everyone out of
            // MFA entirely, since the TOTP/password checks themselves are still enforced either way.
            log.warn("MFA rate limiter could not reach Redis, allowing attempt through: {}", e.getMessage());
        }
    }

    private String pendingSecretKey(UUID userId) {
        return "mfa_pending_secret:" + userId;
    }

    private String stepUpKey(UUID userId) {
        return "mfa_step_up:" + userId;
    }
}
