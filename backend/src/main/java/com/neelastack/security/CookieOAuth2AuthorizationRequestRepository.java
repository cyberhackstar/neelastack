package com.neelastack.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Carries the in-flight OAuth2 authorization request (the "state" Spring Security needs to
 * survive the redirect to Google and back) via a short-lived, httpOnly cookie instead of the
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
 * IMPORTANT: the cookie itself never carries the serialized {@link OAuth2AuthorizationRequest}.
 * It only carries an opaque, unguessable, random {@link OneTimeTokenService} token. The actual
 * (Java-serialized) authorization request lives server-side in Redis, keyed by that token. This
 * matters because {@code SerializationUtils.deserialize} runs Java's native object
 * deserialization, which is unsafe to run over attacker-controlled bytes (arbitrary gadget-chain
 * RCE). Previously this class fed the raw cookie value — fully controlled by the browser/client —
 * straight into {@code SerializationUtils.deserialize}, which is a critical vulnerability: anyone
 * could send a crafted {@code oauth2_auth_request} cookie value to the callback endpoint. By
 * routing the payload through Redis instead, the bytes that ever reach
 * {@code SerializationUtils.deserialize} are always ones *we* wrote, never ones the client
 * supplied — the cookie's random token only proves the caller owns the flow, it can't inject
 * arbitrary serialized content.
 *
 * The cookie holds nothing but a random lookup key for the transient authorization-request state
 * (PKCE verifier, the "state" nonce, requested scopes) needed to validate Google's callback; it
 * never carries an authenticated identity, and both the cookie and the Redis entry are deleted the
 * moment {@link #removeAuthorizationRequest} runs, which Spring Security calls as soon as the
 * callback is processed — success or failure.
 */
@Component
@RequiredArgsConstructor
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final String REDIS_NAMESPACE = "oauth2:authreq";
    // Only needs to survive the round trip to Google's consent screen and back.
    private static final int COOKIE_MAX_AGE_SECONDS = 180;
    private static final Duration REDIS_TTL = Duration.ofSeconds(COOKIE_MAX_AGE_SECONDS);

    private final OneTimeTokenService oneTimeTokenService;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        // Non-destructive read: Spring Security may call this to inspect the in-flight request
        // (e.g. to validate the "state" param) before later calling removeAuthorizationRequest.
        return readCookie(request)
                .flatMap(token -> oneTimeTokenService.read(REDIS_NAMESPACE, token))
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
        String token = oneTimeTokenService.issue(REDIS_NAMESPACE, serialize(authorizationRequest), REDIS_TTL);
        writeCookie(response, request.isSecure(), token, COOKIE_MAX_AGE_SECONDS);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                   HttpServletResponse response) {
        // Atomic read-then-delete (GETDEL) so the entry can't be replayed even if two requests
        // race on the same cookie value.
        OAuth2AuthorizationRequest authorizationRequest = readCookie(request)
                .flatMap(token -> oneTimeTokenService.consume(REDIS_NAMESPACE, token))
                .map(CookieOAuth2AuthorizationRequestRepository::deserialize)
                .orElse(null);
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

    // These bytes are only ever written to / read from our own Redis instance — never sent to
    // or accepted from the client — so running them through Java's native (de)serialization here
    // is safe. The client only ever sees the random lookup token (see class javadoc).
    private static String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(authorizationRequest));
    }

    private static OAuth2AuthorizationRequest deserialize(String redisValue) {
        try {
            return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(
                    Base64.getUrlDecoder().decode(redisValue));
        } catch (Exception e) {
            // Malformed or stale (e.g. serialVersionUID mismatch after a deploy) Redis value —
            // treat exactly like "no authorization request in flight" rather than 500ing the
            // callback.
            return null;
        }
    }
}
