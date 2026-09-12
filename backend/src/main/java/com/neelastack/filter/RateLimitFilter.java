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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple fixed-window rate limiter backed by Redis (INCR + EXPIRE).
 *
 * Applied only to selected public/auth endpoints that are prone to abuse.
 *
 * Redis failures fall back to a local in-memory fixed-window counter for the
 * authentication-sensitive routes in {@link #FAIL_CLOSED_PATHS} (security review P1 #6):
 * an outage should not silently turn off brute-force protection on login, MFA, register,
 * password reset, and verification. The local fallback is necessarily per-instance (it
 * doesn't share state across app replicas the way Redis does), so it's a degraded but
 * present defense rather than the full distributed limit -- that tradeoff is acceptable
 * for the duration of a Redis outage, unlike having no limit at all. Every other route
 * keeps the original fail-open behavior, since blocking public marketing forms during a
 * cache outage isn't worth the availability cost.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, Limit> LIMITS = Map.ofEntries(
            Map.entry("/api/v1/auth/login", new Limit(10, Duration.ofMinutes(1))),
            Map.entry("/api/v1/auth/login/mfa", new Limit(10, Duration.ofMinutes(1))),
            Map.entry("/api/v1/auth/register", new Limit(5, Duration.ofMinutes(10))),
            Map.entry("/api/v1/auth/forgot-password", new Limit(5, Duration.ofMinutes(10))),
            Map.entry("/api/v1/auth/resend-verification", new Limit(5, Duration.ofMinutes(10))),
            Map.entry("/api/v1/public/inquiries", new Limit(5, Duration.ofMinutes(10))),
            Map.entry("/api/v1/public/audit-preview/score", new Limit(20, Duration.ofMinutes(10))),
            Map.entry("/api/v1/public/audit-preview/unlock", new Limit(5, Duration.ofMinutes(10))),
            // Security review P1 #3: MfaService already caps these at 5 attempts / 15 min
            // *per account*, but an attacker holding a valid admin JWT (or spraying TOTP/
            // recovery-code guesses across several compromised admin accounts) can still
            // hammer the endpoint from one source. This adds a second, per-IP layer on top --
            // deliberately looser than the per-account limit so a legitimate admin retrying
            // a mistyped code from one browser never hits it first.
            Map.entry("/api/v1/admin/mfa/step-up", new Limit(20, Duration.ofMinutes(15))),
            Map.entry("/api/v1/admin/mfa/verify", new Limit(20, Duration.ofMinutes(15))),
            Map.entry("/api/v1/admin/mfa/disable", new Limit(20, Duration.ofMinutes(15))),
            Map.entry("/api/v1/admin/mfa/recovery", new Limit(20, Duration.ofMinutes(15))));

    // Routes where a Redis outage must NOT silently disable brute-force protection.
    private static final Set<String> FAIL_CLOSED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/login/mfa",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/resend-verification",
            // Same reasoning as MfaService's own local fallback for these -- a TOTP/recovery
            // secret is exactly what this limit protects, so this IP-layer should degrade to
            // the local counter rather than disappear during a Redis outage too.
            "/api/v1/admin/mfa/step-up",
            "/api/v1/admin/mfa/verify",
            "/api/v1/admin/mfa/disable",
            "/api/v1/admin/mfa/recovery");

    private record Limit(int maxRequests, Duration window) {
    }

    private record LocalWindow(AtomicInteger count, Instant windowResetAt) {
    }

    // Local per-instance fallback counters, used only while Redis is unreachable.
    // Entries are actively evicted so an attacker cannot grow this map indefinitely
    // during a prolonged Redis outage.
    private static final int MAX_LOCAL_FALLBACK_KEYS = 20_000;

    private final Map<String, LocalWindow> localFallbackWindows = new ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 60_000L)
    void evictExpiredLocalWindows() {
        Instant now = Instant.now();
        localFallbackWindows.entrySet().removeIf(entry -> now.isAfter(entry.getValue().windowResetAt()));
    }

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
            if (FAIL_CLOSED_PATHS.contains(request.getRequestURI())) {
                log.warn(
                        "Rate limiter could not reach Redis for a sensitive auth route; "
                                + "applying local in-memory fallback limit for {}: {}",
                        request.getRequestURI(), e.getMessage());
                handleWithLocalFallback(request, response, filterChain, key, limit);
                return;
            }

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

    private void handleWithLocalFallback(HttpServletRequest request, HttpServletResponse response,
                                          FilterChain filterChain, String key, Limit limit)
            throws IOException, ServletException {

        Instant now = Instant.now();
        if (!localFallbackWindows.containsKey(key) && localFallbackWindows.size() >= MAX_LOCAL_FALLBACK_KEYS) {
            evictExpiredLocalWindows();
            if (!localFallbackWindows.containsKey(key) && localFallbackWindows.size() >= MAX_LOCAL_FALLBACK_KEYS) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", "60");
                response.setHeader("X-RateLimit-Limit", Integer.toString(limit.maxRequests()));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Security protection is temporarily busy; please retry shortly.\"}");
                return;
            }
        }

        LocalWindow window = localFallbackWindows.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowResetAt())) {
                if (existing == null && localFallbackWindows.size() >= MAX_LOCAL_FALLBACK_KEYS) {
                    // Preserve availability without permitting unbounded memory growth.
                    // The Redis-backed limiter remains the normal distributed guard.
                    return new LocalWindow(new AtomicInteger(limit.maxRequests() + 1), now.plus(limit.window()));
                }
                return new LocalWindow(new AtomicInteger(0), now.plus(limit.window()));
            }
            return existing;
        });

        int count = window.count().incrementAndGet();

        if (count > limit.maxRequests()) {
            long retryAfterSeconds = Math.max(1, Duration.between(now, window.windowResetAt()).toSeconds());

            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
            response.setHeader("X-RateLimit-Limit", Integer.toString(limit.maxRequests()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.getWriter().write(
                    "{\"status\":429,"
                            + "\"error\":\"Too Many Requests\","
                            + "\"message\":\"Please slow down and try again shortly.\"}");
            return;
        }

        response.setHeader("X-RateLimit-Limit", Integer.toString(limit.maxRequests()));
        response.setHeader("X-RateLimit-Remaining",
                Integer.toString(Math.max(0, limit.maxRequests() - count)));

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Production traffic is Cloudflare -> cloudflared -> nginx -> Spring.
        // The origin is not publicly exposed in production; proxy headers are accepted here
        // only because that topology is enforced by the deployment. Keep the origin bound to
        // loopback/private networking when using this resolver.
        String cloudflareIp = request.getHeader("CF-Connecting-IP");
        if (cloudflareIp != null && !cloudflareIp.isBlank()) {
            return cloudflareIp.trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        // Local/E2E proxy fallback. Do not trust the first user-supplied XFF hop.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].trim();
            if (!last.isBlank()) {
                return last;
            }
        }

        return request.getRemoteAddr();
    }
}