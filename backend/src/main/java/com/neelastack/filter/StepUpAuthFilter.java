package com.neelastack.filter;

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
 * consistency -- not every admin GET. And only for accounts that have MFA enabled:
 * this codebase doesn't currently force MFA enrollment for every admin, so an
 * unenrolled admin isn't step-up-gated (they simply aren't protected by MFA yet,
 * which is a real gap but a different one from this filter's job).
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

        Optional<UUID> userId = userRepository.findByEmail(auth.getName())
                .filter(u -> u.isMfaEnabled())
                .map(u -> u.getId());

        if (userId.isEmpty()) {
            // Either the user record is gone (shouldn't happen post-auth) or MFA isn't
            // enabled on this account yet — see class javadoc for why that's not blocked here.
            filterChain.doFilter(request, response);
            return;
        }

        if (!mfaService.hasActiveStepUp(userId.get())) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":403,\"error\":\"Step-up required\","
                            + "\"message\":\"This action requires a recent MFA verification. "
                            + "POST your TOTP code to /api/v1/admin/mfa/step-up and retry.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isHighRiskMutation(HttpServletRequest request) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return HIGH_RISK_PATTERNS.stream().anyMatch(p -> p.matcher(path).matches());
    }
}
