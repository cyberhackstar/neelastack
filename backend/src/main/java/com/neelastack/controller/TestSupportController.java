package com.neelastack.controller;

import com.neelastack.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * E2E-only scaffolding, gated by {@code @Profile("test")} -- the mirror image of
 * RateLimitFilter's {@code @Profile("!test")}. This whole bean, and therefore every route
 * under it, simply does not exist unless {@code SPRING_PROFILES_ACTIVE=test}, which is only
 * ever set for the disposable local/CI e2e stack (docker-compose.yml) and the JUnit test
 * classpath -- never {@code dev} or {@code prod} (see application-{dev,prod}.yml, neither of
 * which activates this profile). Requests to these paths in any real environment 404, same
 * as any other nonexistent controller.
 *
 * Exists because of a direct, intended consequence of security review P1 #1:
 * AuthService#register() no longer hands back usable tokens for an unverified account, so
 * every e2e fixture that needs an authenticated CLIENT session must now go through the same
 * email-verification gate a real user does. The disposable e2e stack has no real inbox to
 * click a verification link from (MAIL_HOST/MAIL_USERNAME are blank there, and
 * EmailService#sendVerificationEmail is fire-and-forget @Async), so this gives e2e fixtures
 * a narrow way to mark an account verified directly -- equivalent to what clicking the
 * emailed link does, without adding a mail-catcher service or reproducing the token-parsing
 * flow just for tests.
 */
@RestController
@RequestMapping("/api/v1/test-support")
@RequiredArgsConstructor
@Profile("test")
@Tag(name = "Test support (e2e only)", description = "Not present outside the test Spring profile")
public class TestSupportController {

    private final UserRepository userRepository;

    public record ForceVerifyEmailRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email
    ) {}

    @PostMapping("/force-verify-email")
    @Operation(summary = "e2e/test-only: mark an account's email verified directly, bypassing the real verification link")
    public ResponseEntity<Void> forceVerifyEmail(@Valid @RequestBody ForceVerifyEmailRequest request) {
        // Same anti-enumeration shape as /auth/resend-verification: succeeds whether or not
        // the address exists, so a fixture can't use this to probe registered emails either.
        userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .ifPresent(user -> {
                    user.setEmailVerified(true);
                    userRepository.save(user);
                });
        return ResponseEntity.noContent().build();
    }
}
