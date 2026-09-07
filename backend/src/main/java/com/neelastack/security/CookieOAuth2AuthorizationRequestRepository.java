package com.neelastack.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.util.Base64;
import java.util.Optional;

/**
 * Carries the in-flight OAuth2 authorization request (the "state" Spring Security needs to
 * survive the redirect to Google and back) in a short-lived, httpOnly cookie instead of the
 * HttpSession.
 *
 * This API is otherwise pure JWT/Bearer and runs {@link org.springframework.security.config.http.SessionCreationPolicy#STATELESS}
 * (see SecurityConfig) specifically so it scales across replicas without sticky sessions or a
 * shared session store. Spring Security's default HttpSessionOAuth2AuthorizationRequestRepository
 * calls request.getSession(true) unconditionally, ignoring that policy — which would reintroduce
 * server-side session state (and a JSESSIONID cookie, and the SecurityContext leaking into it via
 * HttpSessionSecurityContextRepository) for this one endpoint regardless of how the rest of the
 * app is configured. Replacing it here removes server-side session state from the picture
 * entirely rather than just cleaning up after it.
 *
 * The cookie holds nothing but the transient authorization-request state (PKCE verifier, the
 * "state" nonce, requested scopes) needed to validate Google's callback; it never carries an
 * authenticated identity and is deleted the moment {@link #removeAuthorizationRequest} runs,
 * which Spring Security calls as soon as the callback is processed — success or failure.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    // Only needs to survive the round trip to Google's consent screen and back.
    private static final int COOKIE_MAX_AGE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request)
                .map(CookieOAuth2AuthorizationRequestRepository::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }
        writeCookie(response, request.isSecure(), serialize(authorizationRequest), COOKIE_MAX_AGE_SECONDS);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                   HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        deleteCookie(response);
        return authorizationRequest;
    }

    private static Optional<String> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private static void writeCookie(HttpServletResponse response, boolean secure, String value, int maxAgeSeconds) {
        // Built via ResponseCookie (not jakarta.servlet.http.Cookie) so SameSite is explicit
        // rather than left to the servlet container's default CookieProcessor config. "Lax" is
        // required here, not just permissible: this cookie has to survive a cross-site top-level
        // redirect (browser -> Google -> back to us), which "Strict" would strip.
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static void deleteCookie(HttpServletResponse response) {
        writeCookie(response, false, "", 0);
    }

    private static String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(authorizationRequest));
    }

    private static OAuth2AuthorizationRequest deserialize(String cookieValue) {
        try {
            return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(
                    Base64.getUrlDecoder().decode(cookieValue));
        } catch (Exception e) {
            // Malformed, tampered, or stale (e.g. serialVersionUID mismatch after a deploy)
            // cookie value — treat exactly like "no authorization request in flight" rather
            // than 500ing the callback.
            return null;
        }
    }
}
