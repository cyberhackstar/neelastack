package com.neelastack.security;

import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Runs after Google confirms the user's identity. We don't hand the browser a
 * JWT directly here (that would put long-lived tokens in a redirect URL,
 * visible in browser history / server logs). Instead we issue a one-time
 * exchange code and redirect to the frontend, which immediately trades it for
 * real tokens via a POST request (see AuthController#oauthExchange).
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokenService;

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    private static final String EXCHANGE_NAMESPACE = "oauth_exchange";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        Boolean emailVerifiedAttr = oAuth2User.getAttribute("email_verified");

        if (email == null) {
            response.sendRedirect(frontendUrl + "/login?error=oauth_no_email");
            return;
        }

        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseGet(() -> createUserFromGoogle(email, name, Boolean.TRUE.equals(emailVerifiedAttr)));

        String code = oneTimeTokenService.issue(EXCHANGE_NAMESPACE, user.getId().toString(), Duration.ofSeconds(60));
        response.sendRedirect(frontendUrl + "/oauth-callback?code=" + code);
    }

    private User createUserFromGoogle(String email, String name, boolean emailVerified) {
        User user = User.builder()
                .fullName(name != null ? name : email)
                .email(email.toLowerCase().trim())
                // Google-authenticated users never use a local password — set an unusable random
                // hash rather than leaving it null, since the User entity requires a value.
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.CLIENT)
                .emailVerified(emailVerified)
                .build();
        return userRepository.save(user);
    }
}
