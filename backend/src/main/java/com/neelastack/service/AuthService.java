package com.neelastack.service;

import com.neelastack.dto.auth.AuthResponse;
import com.neelastack.dto.auth.LoginRequest;
import com.neelastack.dto.auth.RegisterRequest;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.EmailAlreadyExistsException;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.JwtService;
import com.neelastack.security.OneTimeTokenService;
import com.neelastack.security.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenRevocationService tokenRevocationService;
    private final OneTimeTokenService oneTimeTokenService;
    private final EmailService emailService;
    private final MfaService mfaService;

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    private static final String VERIFY_NAMESPACE = "verify_email";
    private static final String RESET_NAMESPACE = "reset_password";
    private static final String OAUTH_EXCHANGE_NAMESPACE = "oauth_exchange";
    private static final String LOGIN_MFA_NAMESPACE = "login_mfa";
    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final Duration LOGIN_MFA_TTL = Duration.ofMinutes(5);

    /**
     * Single normalization boundary for email addresses. Every lookup, existence check, and
     * persisted value must go through this so "User@Example.com" and "user@example.com "
     * are always treated as the same account (fixes duplicate-account / login-mismatch bug).
     */
    private static String normalizeEmail(String rawEmail) {
        return rawEmail == null ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .role(Role.CLIENT)
                .emailVerified(false)
                .build();

        userRepository.save(user);
        sendVerificationEmail(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found after successful authentication"));

        // Password alone isn't enough for an MFA-enrolled account — hand back a short-lived
        // challenge token instead of real tokens; the frontend prompts for a TOTP/recovery
        // code and completes the exchange via completeMfaLogin() below.
        if (user.isMfaEnabled()) {
            String mfaToken = oneTimeTokenService.issue(LOGIN_MFA_NAMESPACE, user.getId().toString(), LOGIN_MFA_TTL);
            return AuthResponse.builder()
                    .mfaRequired(true)
                    .mfaToken(mfaToken)
                    .build();
        }

        return buildAuthResponse(user);
    }

    /**
     * Completes a login that was challenged for MFA. The mfaToken is only invalidated on
     * success — a wrong code just fails this call and the same token (and its remaining
     * TTL) can be retried, same as any other TOTP entry. MfaService's own per-account rate
     * limiting is what actually guards against brute-forcing the code itself.
     */
    @Transactional
    public AuthResponse completeMfaLogin(String mfaToken, String code, boolean useRecoveryCode) {
        UUID userId = oneTimeTokenService.read(LOGIN_MFA_NAMESPACE, mfaToken)
                .map(UUID::fromString)
                .orElseThrow(() -> new BadRequestException("This login challenge has expired — sign in again."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (useRecoveryCode) {
            mfaService.consumeRecoveryCode(user, code); // throws BadRequestException on an invalid/used code
        } else {
            mfaService.stepUp(user, code); // throws BadRequestException on an invalid code; also grants a fresh step-up assertion
        }

        oneTimeTokenService.invalidate(LOGIN_MFA_NAMESPACE, mfaToken);
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        String email;
        String tokenType;
        try {
            tokenType = jwtService.extractTokenType(refreshToken);
            email = jwtService.extractUsername(refreshToken);
        } catch (RuntimeException e) {
            // Malformed, tampered, or expired tokens throw deep inside JJWT's parser (e.g.
            // MalformedJwtException, ExpiredJwtException, SignatureException) — none of that
            // is meaningful to a client, and letting it bubble up would surface as a raw 500
            // via the generic exception handler instead of a clean, expected 4xx response.
            throw new BadRequestException("This refresh token is invalid or has expired");
        }

        // Without this check, a still-valid access token (15 min) could be handed to /refresh
        // and would work exactly like a refresh token — silently defeating the whole point of
        // having short-lived access tokens in the first place.
        if (!"refresh".equals(tokenType)) {
            throw new BadRequestException("This is not a valid refresh token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (!jwtService.isTokenValid(refreshToken, user, user.getTokenVersion())) {
            throw new IllegalStateException("Refresh token is invalid or expired");
        }

        String jti = jwtService.extractJti(refreshToken);
        // Null for refresh tokens issued before session-family tracking existed — those fall
        // back to plain per-jti revocation below and get enrolled in a fresh family on this
        // refresh, same as buildAuthResponse's fallback for a brand-new login.
        String familyId = jwtService.extractFamilyId(refreshToken);

        if (familyId != null && tokenRevocationService.isFamilyRevoked(familyId)) {
            throw new BadRequestException("This session has been signed out — please log in again");
        }

        // Atomic claim replaces the old check-then-write (isRevoked() then revoke()): two
        // refresh requests racing on the same jti — multi-tab, a client retry, a replayed
        // stolen token — could previously both pass isRevoked()==false before either call
        // to revoke() became visible, so both would rotate forward successfully. tryClaim is
        // a single atomic Redis SET NX: exactly one concurrent caller can ever win it.
        if (!tokenRevocationService.tryClaim(jti, jwtService.extractExpiration(refreshToken))) {
            // Either already claimed (this exact token was used/rotated once before, or
            // explicitly logged out) or already expired. We can't distinguish a legitimate
            // client retry from a stolen-token replay, so treat it as theft: revoke the whole
            // session family so a possibly-compromised family can't stay alive under a
            // rotated-forward token the attacker doesn't have.
            if (familyId != null) {
                tokenRevocationService.revokeFamily(familyId, Duration.ofMillis(jwtService.getRefreshTokenExpirationMs()));
            }
            throw new BadRequestException("This session has been signed out — please log in again");
        }

        return buildAuthResponse(user, familyId != null ? familyId : UUID.randomUUID().toString());
    }

    /** Revokes a specific refresh token so it can never be used again, even before it expires naturally. */
    public void logout(String refreshToken) {
        try {
            String jti = jwtService.extractJti(refreshToken);
            tokenRevocationService.revoke(jti, jwtService.extractExpiration(refreshToken));
        } catch (Exception e) {
            // Malformed/expired token — nothing meaningful to revoke, and logout should never fail visibly.
        }
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            String token = oneTimeTokenService.issue(RESET_NAMESPACE, user.getId().toString(), RESET_TTL);
            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetUrl);
        });
        // Always returns silently regardless of whether the email exists — prevents user enumeration.
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String userId = oneTimeTokenService.consume(RESET_NAMESPACE, token)
                .orElseThrow(() -> new BadRequestException("This reset link is invalid or has expired"));

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BadRequestException("Account no longer exists"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        // Security event: every access/refresh token issued before this reset must stop
        // working immediately, on every device, not just the one that requested the reset.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    /**
     * Self-service password change for an already-authenticated user — the path the
     * bootstrap admin (mustChangePassword=true) and any force-reset account use to satisfy
     * that requirement, without needing a fresh reset-email round trip. Requires the current
     * password so a hijacked-but-not-yet-logged-out session can't silently change it.
     */
    @Transactional
    public AuthResponse changePassword(User user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        // Old tokens (including the one on this very request) are now invalid by tokenVersion —
        // hand back a fresh pair so the caller doesn't get logged out by their own request.
        return buildAuthResponse(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        String userId = oneTimeTokenService.consume(VERIFY_NAMESPACE, token)
                .orElseThrow(() -> new BadRequestException("This verification link is invalid or has expired"));

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BadRequestException("Account no longer exists"));

        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        userRepository.findByEmail(normalizeEmail(email))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
        // Silent no-op if already verified or account doesn't exist — same anti-enumeration reasoning.
    }

    private void sendVerificationEmail(User user) {
        String token = oneTimeTokenService.issue(VERIFY_NAMESPACE, user.getId().toString(), VERIFY_TTL);
        String verifyUrl = frontendUrl + "/verify-email?token=" + token;
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verifyUrl);
    }

    /** Trades the one-time code from OAuth2LoginSuccessHandler for a real token pair. */
    public AuthResponse oauthExchange(String code) {
        String userId = oneTimeTokenService.consume(OAUTH_EXCHANGE_NAMESPACE, code)
                .orElseThrow(() -> new BadRequestException("This sign-in link has expired — please try again"));

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BadRequestException("Account no longer exists"));

        return buildAuthResponse(user);
    }

    /** Starts a brand-new session family — used for login, register, and OAuth exchange. */
    AuthResponse buildAuthResponse(User user) {
        return buildAuthResponse(user, UUID.randomUUID().toString());
    }

    /** Issues a token pair within the given session family — used by refresh() to rotate forward within the same family. */
    private AuthResponse buildAuthResponse(User user, String familyId) {
        String accessToken = jwtService.generateAccessToken(user, user.getTokenVersion());
        String refreshToken = jwtService.generateRefreshToken(user, familyId, user.getTokenVersion());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .emailVerified(user.isEmailVerified())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }
}
