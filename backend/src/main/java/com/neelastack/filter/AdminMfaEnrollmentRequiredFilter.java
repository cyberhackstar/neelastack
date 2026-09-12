package com.neelastack.filter;

import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.repository.UserRepository;
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
import java.util.Optional;
import java.util.Set;

/**
 * Closes the P0 gap where "the bootstrap admin must enroll MFA" was documented policy
 * but not an actual server-side gate: previously an admin who had already changed the
 * bootstrap password (see MustChangePasswordFilter) could still reach ordinary
 * /api/v1/admin/** functionality without ever completing MFA enrollment, because
 * StepUpAuthFilter only narrows the specific high-risk mutation routes, not admin
 * access as a whole.
 *
 * Rule enforced here: any authenticated ADMIN/SUPERADMIN with mfaEnabled=false is
 * confined to the handful of routes needed to enroll, verify, or leave --
 * everything else under /api/v1/admin/** is denied outright. Runs after
 * MustChangePasswordFilter (Order 100) and before StepUpAuthFilter (Order 150): a
 * bootstrap admin walks password-change -> MFA enrollment -> normal access, in that
 * order, with no way to skip a step.
 *
 * Non-admin roles (CLIENT) are untouched -- MFA enrollment is an admin/back-office
 * requirement only, per the master prompt.
 *
 * Looks the user up by {@code authentication.getName()} rather than casting
 * {@code authentication.getPrincipal()} to {@link User} (security review P1 #10): the old
 * cast worked only because JwtAuthFilter happens to load the actual User entity as
 * UserDetails, and would silently stop detecting anyone the moment that authentication
 * implementation changed to a wrapper/custom principal. StepUpAuthFilter already used this
 * safer lookup; this filter now matches it.
 */
@Component
@RequiredArgsConstructor
@Order(110) // after MustChangePasswordFilter, before StepUpAuthFilter
public class AdminMfaEnrollmentRequiredFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    // Routes an unenrolled admin must still be able to reach: MFA setup/status/verify
    // itself (see MfaController), plus password/security housekeeping and logout.
    // Deliberately does NOT include /disable, /recovery, /step-up, or /force-reset --
    // those presuppose MFA is already enrolled or are gated as high-risk mutations by
    // StepUpAuthFilter regardless.
    private static final Set<String> ALLOWED_EXACT_PATHS = Set.of(
            "/api/v1/admin/mfa/status",
            "/api/v1/admin/mfa/setup",
            "/api/v1/admin/mfa/verify",
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/v1/admin/") || ALLOWED_EXACT_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            // Not our job -- Spring Security's own entry point / access-denied handling
            // deals with unauthenticated requests to these routes.
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> maybeUser = userRepository.findByEmail(auth.getName());

        if (maybeUser.isEmpty()) {
            // User record is gone -- shouldn't happen post-auth, but not our job either;
            // fail through rather than guessing at admin-enrollment status for a principal
            // we can't resolve.
            filterChain.doFilter(request, response);
            return;
        }

        User user = maybeUser.get();
        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SUPERADMIN;

        if (!isAdmin || user.isMfaEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":403,\"error\":\"MFA enrollment required\","
                        + "\"message\":\"Admin accounts must enroll MFA before using admin features. "
                        + "POST to /api/v1/admin/mfa/setup to begin, then /api/v1/admin/mfa/verify to finish.\"}"
        );
    }
}
