package com.neelastack.filter;

import com.neelastack.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Blocks every authenticated request from an account with {@code mustChangePassword=true}
 * (the freshly-bootstrapped admin -- see AdminBootstrapRunner and migration V24 -- or any
 * account an admin has force-reset) except the handful of routes that account needs to
 * actually change its password and log out. Runs after JwtAuthFilter, before StepUpAuthFilter
 * (this gate is broader and should apply first) and before controller dispatch, so a stale
 * client can never reach a real admin action using a bootstrap password that was only ever
 * meant to be used once.
 */
@Component
@Order(100) // after JwtAuthFilter (populates the SecurityContext), before StepUpAuthFilter
public class MustChangePasswordFilter extends OncePerRequestFilter {

    // Deliberately does NOT include /api/v1/auth/refresh: allowing refresh here let an
    // account with mustChangePassword=true stay logged in indefinitely (login -> refresh ->
    // refresh -> ...) without ever satisfying the requirement. Only the two routes actually
    // needed to change the password or walk away remain open.
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User user)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!user.isMustChangePassword() || ALLOWED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":403,\"error\":\"Password change required\","
                        + "\"message\":\"This account must change its password before continuing. "
                        + "POST current and new password to /api/v1/auth/change-password.\"}"
        );
    }
}
