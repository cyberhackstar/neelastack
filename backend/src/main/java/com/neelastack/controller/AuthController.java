package com.neelastack.controller;

import com.neelastack.dto.auth.*;
import com.neelastack.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh, password reset, and email verification")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Create a new client account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive access/refresh tokens (or an MFA challenge if the account has MFA enabled)")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/login/mfa")
    @Operation(summary = "Complete a login challenged by /login, exchanging the mfaToken plus a TOTP or recovery code for real tokens")
    public ResponseEntity<AuthResponse> loginMfa(@Valid @RequestBody LoginMfaRequest request) {
        return ResponseEntity.ok(authService.completeMfaLogin(request.mfaToken(), request.code(), request.useRecoveryCode()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new token pair")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token, ending that session immediately")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email — always returns 202 regardless of whether the email exists")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a valid reset token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address using the token from the verification email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth-exchange")
    @Operation(summary = "Exchange a one-time OAuth2 code (from the Google login redirect) for real tokens")
    public ResponseEntity<AuthResponse> oauthExchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return ResponseEntity.ok(authService.oauthExchange(request.code()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the current password for the authenticated user, invalidating all previously-issued tokens")
    public ResponseEntity<AuthResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.neelastack.entity.User user) {
        return ResponseEntity.ok(authService.changePassword(user, request.currentPassword(), request.newPassword()));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend the verification email — silent no-op if already verified or account doesn't exist")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.resendVerificationEmail(request.email());
        return ResponseEntity.accepted().build();
    }
}
