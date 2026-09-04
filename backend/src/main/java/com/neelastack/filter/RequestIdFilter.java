package com.neelastack.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a correlation ID (X-Request-ID) that flows through
 * Nginx -> Spring Boot -> logs -> Sentry -> the client. When a client says
 * "my payment failed", the ID printed on their error response is the same
 * ID you can grep for across every log line the request touched.
 *
 * Runs before everything else (HIGHEST_PRECEDENCE) so the ID is in the MDC
 * for the full lifetime of the request, including inside the rate limiter
 * and security filters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Trust an inbound ID from Nginx ($request_id) or an upstream caller so a single
        // request keeps one ID across every hop, rather than minting a new one per service.
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String requestId = (incoming != null && !incoming.isBlank())
                ? incoming.trim()
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // MDC is thread-local and threads are pooled/reused — always clear it, or the
            // next request handled by this thread inherits a stale ID.
            MDC.remove(MDC_KEY);
        }
    }
}
