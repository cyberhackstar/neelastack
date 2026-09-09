package com.neelastack.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Simple fixed-window rate limiter backed by Redis (INCR + EXPIRE).
 *
 * Applied only to selected public/auth endpoints that are prone to abuse.
 *
 * Redis failures fail open so an infrastructure/cache outage does not take
 * authentication or public forms completely offline.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, Limit> LIMITS = Map.of(
            "/api/v1/auth/login", new Limit(10, Duration.ofMinutes(1)),
            "/api/v1/auth/login/mfa", new Limit(10, Duration.ofMinutes(1)),
            "/api/v1/auth/register", new Limit(5, Duration.ofMinutes(10)),
            "/api/v1/auth/forgot-password", new Limit(5, Duration.ofMinutes(10)),
            "/api/v1/auth/resend-verification", new Limit(5, Duration.ofMinutes(10)),
            "/api/v1/public/inquiries", new Limit(5, Duration.ofMinutes(10)),
            "/api/v1/public/audit-preview/score", new Limit(20, Duration.ofMinutes(10)),
            "/api/v1/public/audit-preview/unlock", new Limit(5, Duration.ofMinutes(10)));

    private record Limit(int maxRequests, Duration window) {
    }

    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        Limit limit = LIMITS.get(request.getRequestURI());

        if (limit == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String key = "ratelimit:v1:" + request.getRequestURI() + ":" + clientIp;

        Long count;

        try {
            count = redisTemplate.opsForValue().increment(key);

            if (count != null && count == 1L) {
                redisTemplate.expire(key, limit.window());
            }
        } catch (Exception e) {
            log.warn(
                    "Rate limiter could not reach Redis; allowing request through: {}",
                    e.getMessage());

            filterChain.doFilter(request, response);
            return;
        }

        if (count != null && count > limit.maxRequests()) {
            Long ttl = redisTemplate.getExpire(key);

            long retryAfterSeconds = (ttl == null || ttl < 1L) ? 1L : ttl;

            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            response.setHeader(
                    "Retry-After",
                    Long.toString(retryAfterSeconds));

            response.setHeader(
                    "X-RateLimit-Limit",
                    Integer.toString(limit.maxRequests()));

            response.setHeader(
                    "X-RateLimit-Remaining",
                    "0");

            response.setHeader(
                    "X-RateLimit-Window-Seconds",
                    Long.toString(limit.window().toSeconds()));

            response.getWriter().write(
                    "{\"status\":429,"
                            + "\"error\":\"Too Many Requests\","
                            + "\"message\":\"Please slow down and try again shortly.\"}");
            return;
        }

        int remaining = Math.max(
                0,
                limit.maxRequests()
                        - (count == null ? 0 : Math.toIntExact(count)));

        response.setHeader(
                "X-RateLimit-Limit",
                Integer.toString(limit.maxRequests()));

        response.setHeader(
                "X-RateLimit-Remaining",
                Integer.toString(remaining));

        response.setHeader(
                "X-RateLimit-Window-Seconds",
                Long.toString(limit.window().toSeconds()));

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].trim();

            if (!first.isBlank()) {
                return first;
            }
        }

        String realIp = request.getHeader("X-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}