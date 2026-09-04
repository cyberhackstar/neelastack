package com.neelastack.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Simple fixed-window rate limiter backed by Redis (INCR + EXPIRE), applied
 * only to the handful of public, abuse-prone endpoints: login, register, and
 * the inquiry (lead capture) form. Everything else is left alone — this is
 * not a general-purpose API gateway, just a guard against credential
 * stuffing and contact-form spam.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    // path prefix -> [max requests, window]
    private static final Map<String, Limit> LIMITS = Map.of(
            "/api/v1/auth/login", new Limit(10, Duration.ofMinutes(1)),
            "/api/v1/auth/login/mfa", new Limit(10, Duration.ofMinutes(1)),
            "/api/v1/auth/register", new Limit(5, Duration.ofMinutes(10)),
            "/api/v1/auth/forgot-password", new Limit(5, Duration.ofMinutes(10)),
            "/api/v1/auth/resend-verification", new Limit(5, Duration.ofMinutes(10)),
            "/api/v1/public/inquiries", new Limit(5, Duration.ofMinutes(10)),
            "/api/v1/public/audit-preview/score", new Limit(20, Duration.ofMinutes(10)),
            "/api/v1/public/audit-preview/unlock", new Limit(5, Duration.ofMinutes(10))
    );

    private record Limit(int maxRequests, Duration window) {}

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        Limit limit = LIMITS.get(request.getRequestURI());

        if (limit == null || request.getMethod().equals("OPTIONS")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String key = "ratelimit:" + request.getRequestURI() + ":" + clientIp;

        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, limit.window());
            }
        } catch (Exception e) {
            // Redis unavailable — fail open rather than blocking all traffic on this endpoint.
            log.warn("Rate limiter could not reach Redis, allowing request through: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (count != null && count > limit.maxRequests()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Please slow down and try again shortly.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
