package com.neelastack.filter;

import com.neelastack.entity.User;
import com.neelastack.repository.UserRepository;
import com.neelastack.service.MfaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Financial/client/project-mutating admin endpoints, plus MFA and session management
 * themselves, need a *recent* MFA-verified assertion, not just a valid JWT issued
 * hours ago (master prompt, Section 2). Runs after JwtAuthFilter (so the
 * SecurityContext is already populated) and only ever narrows an already-authenticated
 * ROLE_ADMIN request further -- it never grants access SecurityConfig wouldn't
 * otherwise allow.
 *
 * Deliberately only gates mutations (POST/PUT/PATCH/DELETE) on the specific high-risk
 * route list below -- the same list the audit-logging call sites target, for
 * consistency -- not every admin GET.
 *
 * An admin with MFA *disabled* is DENIED these high-risk mutations outright, not waved
 * through: "no MFA enrolled" must never be a softer security posture than "MFA enrolled
 * but no recent step-up", or MFA becomes something an attacker (or a careless admin) can
 * bypass simply by never turning it on. The only routes exempt from this filter entirely
 * are the account-setup routes themselves (mfa/setup, /verify, /step-up -- see
 * MfaController; none of them match HIGH_RISK_PATTERNS), so an unenrolled admin can
 * always reach enrollment/step-up without a chicken-and-egg lockout.
 */
@Component
@RequiredArgsConstructor
@Order(150) // after JwtAuthFilter, before controller dispatch
public class StepUpAuthFilter extends OncePerRequestFilter {

    private final MfaService mfaService;
    private final UserRepository userRepository;

    private static final List<Pattern> HIGH_RISK_PATTERNS = List.of(
            Pattern.compile("^/api/v1/admin/invoices(/.*)?$"),
            Pattern.compile("^/api/v1/admin/payments(/.*)?$"),
            Pattern.compile("^/api/v1/admin/pricing-rules(/.*)?$"),
            Pattern.compile("^/api/v1/admin/mfa/(disable|.*force-reset)$"),
            Pattern.compile("^/api/v1/admin/sessions(/.*)?$")
    );

    private static final List<String> MUTATING_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!isHighRiskMutation(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // Not our job — JwtAuthFilter/Spring Security's own entry point handles this.
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> maybeUser = userRepository.findByEmail(auth.getName());

        if (maybeUser.isEmpty()) {
            // User record is gone -- shouldn't happen post-auth, but fail closed rather than
            // silently letting a high-risk mutation through.
            denyStepUpRequired(response);
            return;
        }

        User user = maybeUser.get();

        if (!user.isMfaEnabled()) {
            // MFA not enrolled is NOT a bypass for high-risk operations -- see class javadoc.
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":403,\"error\":\"MFA required\","
                            + "\"message\":\"This action requires MFA to be enabled on your account. "
                            + "POST to /api/v1/admin/mfa/setup to enroll, then retry.\"}"
            );
            return;
        }

        if (!mfaService.hasActiveStepUp(user.getId())) {
            denyStepUpRequired(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void denyStepUpRequired(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":403,\"error\":\"Step-up required\","
                        + "\"message\":\"This action requires a recent MFA verification. "
                        + "POST your TOTP code to /api/v1/admin/mfa/step-up and retry.\"}"
        );
    }

    private boolean isHighRiskMutation(HttpServletRequest request) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return HIGH_RISK_PATTERNS.stream().anyMatch(p -> p.matcher(path).matches());
    }
}
