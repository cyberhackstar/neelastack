package com.neelastack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.exception.ApiError;
import com.neelastack.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Without an explicit entry point, Spring Security falls back to whatever authentication
 * mechanism is configured last resort — here that would be the OAuth2 login flow, which
 * means an API client calling a protected JSON endpoint without a bearer token would get
 * a 302 redirect toward Google's authorization endpoint instead of a clean 401. This app's
 * OAuth2 login is only ever initiated by the frontend explicitly navigating the browser to
 * the public /oauth2/authorization/google endpoint, so it never needs this fallback — every
 * unauthenticated hit on a protected resource should get the same 401 JSON body as every
 * other handled exception (see GlobalExceptionHandler).
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message("Authentication is required to access this resource")
                .path(request.getRequestURI())
                .requestId(MDC.get(RequestIdFilter.MDC_KEY))
                .build();

        objectMapper.writeValue(response.getWriter(), error);
    }
}
