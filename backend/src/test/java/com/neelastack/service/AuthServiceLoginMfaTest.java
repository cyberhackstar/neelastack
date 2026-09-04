package com.neelastack.service;

import com.neelastack.dto.auth.AuthResponse;
import com.neelastack.dto.auth.LoginRequest;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.JwtService;
import com.neelastack.security.OneTimeTokenService;
import com.neelastack.security.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the login-time MFA challenge added to AuthService: an MFA-enrolled admin's
 * /login returns a challenge token instead of real tokens, and /login/mfa exchanges
 * that token plus a TOTP or recovery code for the actual session. Pure Mockito —
 * no Spring context or Redis needed, same pattern as InvoiceServiceTest.
 */
class AuthServiceLoginMfaTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthenticationManager authenticationManager;
    private TokenRevocationService tokenRevocationService;
    private OneTimeTokenService oneTimeTokenService;
    private EmailService emailService;
    private MfaService mfaService;
    private AuthService authService;

    private static final String LOGIN_MFA_NAMESPACE = "login_mfa";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        authenticationManager = mock(AuthenticationManager.class);
        tokenRevocationService = mock(TokenRevocationService.class);
        oneTimeTokenService = mock(OneTimeTokenService.class);
        emailService = mock(EmailService.class);
        mfaService = mock(MfaService.class);

        authService = new AuthService(
                userRepository, passwordEncoder, jwtService, authenticationManager,
                tokenRevocationService, oneTimeTokenService, emailService, mfaService);
    }

    private User mfaEnabledAdmin(UUID id) {
        return User.builder()
                .id(id)
                .fullName("Admin User")
                .email("admin@neelastack.com")
                .password("hashed")
                .role(Role.ADMIN)
                .emailVerified(true)
                .mfaEnabled(true)
                .build();
    }

    @Test
    void login_mfaEnabledAccount_returnsChallengeNotTokens() {
        UUID userId = UUID.randomUUID();
        User admin = mfaEnabledAdmin(userId);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(oneTimeTokenService.issue(eq(LOGIN_MFA_NAMESPACE), eq(userId.toString()), any(Duration.class)))
                .thenReturn("challenge-token-123");

        AuthResponse response = authService.login(new LoginRequest(admin.getEmail(), "correct-password"));

        assertThat(response.mfaRequired()).isTrue();
        assertThat(response.mfaToken()).isEqualTo("challenge-token-123");
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        verify(jwtService, never()).generateAccessToken(any(), anyInt());
    }

    @Test
    void completeMfaLogin_validTotpCode_issuesTokensAndInvalidatesChallenge() {
        UUID userId = UUID.randomUUID();
        User admin = mfaEnabledAdmin(userId);
        when(oneTimeTokenService.read(LOGIN_MFA_NAMESPACE, "challenge-token-123"))
                .thenReturn(Optional.of(userId.toString()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
        when(jwtService.generateAccessToken(eq(admin), anyInt())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(admin), anyString(), anyInt())).thenReturn("refresh-token");

        AuthResponse response = authService.completeMfaLogin("challenge-token-123", "123456", false);

        verify(mfaService).stepUp(admin, "123456");
        verify(mfaService, never()).consumeRecoveryCode(any(), anyString());
        verify(oneTimeTokenService).invalidate(LOGIN_MFA_NAMESPACE, "challenge-token-123");
        assertThat(response.mfaRequired()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void completeMfaLogin_recoveryCode_consumesRecoveryCodeInstead() {
        UUID userId = UUID.randomUUID();
        User admin = mfaEnabledAdmin(userId);
        when(oneTimeTokenService.read(LOGIN_MFA_NAMESPACE, "challenge-token-123"))
                .thenReturn(Optional.of(userId.toString()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
        when(jwtService.generateAccessToken(eq(admin), anyInt())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(admin), anyString(), anyInt())).thenReturn("refresh-token");

        authService.completeMfaLogin("challenge-token-123", "abcd-1234", true);

        verify(mfaService).consumeRecoveryCode(admin, "abcd-1234");
        verify(mfaService, never()).stepUp(any(), anyString());
        verify(oneTimeTokenService).invalidate(LOGIN_MFA_NAMESPACE, "challenge-token-123");
    }

    @Test
    void completeMfaLogin_wrongCode_doesNotInvalidateChallenge_soItCanBeRetried() {
        UUID userId = UUID.randomUUID();
        User admin = mfaEnabledAdmin(userId);
        when(oneTimeTokenService.read(LOGIN_MFA_NAMESPACE, "challenge-token-123"))
                .thenReturn(Optional.of(userId.toString()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
        doThrow(new BadRequestException("Invalid code.")).when(mfaService).stepUp(admin, "000000");

        assertThatThrownBy(() -> authService.completeMfaLogin("challenge-token-123", "000000", false))
                .isInstanceOf(BadRequestException.class);

        // The whole point of read() (vs. consume()) — a wrong attempt doesn't burn the
        // challenge, so the same mfaToken can be retried within its TTL.
        verify(oneTimeTokenService, never()).invalidate(anyString(), anyString());
    }

    @Test
    void completeMfaLogin_expiredOrUnknownToken_throwsBadRequest() {
        when(oneTimeTokenService.read(LOGIN_MFA_NAMESPACE, "stale-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.completeMfaLogin("stale-token", "123456", false))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(mfaService);
    }

    @Test
    void login_nonMfaAccount_returnsTokensDirectly_unchangedBehavior() {
        User client = User.builder()
                .id(UUID.randomUUID())
                .fullName("Client User")
                .email("client@example.com")
                .password("hashed")
                .role(Role.CLIENT)
                .emailVerified(true)
                .mfaEnabled(false)
                .build();
        when(userRepository.findByEmail(client.getEmail())).thenReturn(Optional.of(client));
        when(jwtService.generateAccessToken(eq(client), anyInt())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(client), anyString(), anyInt())).thenReturn("refresh-token");

        AuthResponse response = authService.login(new LoginRequest(client.getEmail(), "correct-password"));

        assertThat(response.mfaRequired()).isFalse();
        assertThat(response.mfaToken()).isNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        verifyNoInteractions(oneTimeTokenService);
    }
}
