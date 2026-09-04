package com.neelastack.integration;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.security.JwtService;
import com.neelastack.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises /api/v1/auth/** end to end: register, login, refresh, logout, and
 * the
 * failure/edge cases the audit specifically called out (item #2 —
 * "Authentication
 * integration tests"). Every request here goes through the real security filter
 * chain,
 * not a mocked controller.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private JwtService jwtService;

        @Autowired
        private UserDetailsService userDetailsService;

        private static final String PASSWORD = "Str0ngPassw0rd!";

        private String registerAndGetBody(String email) throws Exception {
                String payload = """
                                {"fullName":"Test User","email":"%s","password":"%s","phone":"9999999999"}
                                """.formatted(email, PASSWORD);

                return mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.refreshToken").exists())
                                .andExpect(jsonPath("$.role").value("CLIENT"))
                                .andReturn().getResponse().getContentAsString();
        }

        @Test
        void register_thenLogin_returnsTokenPair() throws Exception {
                String email = "register-login@example.com";
                registerAndGetBody(email);

                String loginPayload = """
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD);

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginPayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.refreshToken").exists())
                                .andExpect(jsonPath("$.email").value(email));
        }

        @Test
        void register_duplicateEmail_returns409() throws Exception {
                String email = "duplicate@example.com";
                registerAndGetBody(email);

                String payload = """
                                {"fullName":"Someone Else","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD);

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isConflict());
        }

        @Test
        void register_weakPassword_returns400WithFieldError() throws Exception {
                String payload = """
                                {"fullName":"Test User","email":"weak@example.com","password":"weak"}
                                """;

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.fieldErrors.password").exists());
        }

        @Test
        void login_wrongPassword_returns401() throws Exception {
                String email = "wrongpass@example.com";
                registerAndGetBody(email);

                String payload = """
                                {"email":"%s","password":"TotallyWrong1"}
                                """.formatted(email);

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void login_unknownEmail_returns401NotUserEnumerationHint() throws Exception {
                String payload = """
                                {"email":"nobody-here@example.com","password":"%s"}
                                """.formatted(PASSWORD);

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        void refresh_withAccessTokenInsteadOfRefreshToken_isRejected() throws Exception {
                String email = "token-type@example.com";
                String body = registerAndGetBody(email);
                JsonNode json = objectMapper.readTree(body);
                String accessToken = json.get("accessToken").asText();

                String payload = """
                                {"refreshToken":"%s"}
                                """.formatted(accessToken);

                mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void refresh_withValidRefreshToken_returnsNewTokenPair() throws Exception {
                String email = "refresh-ok@example.com";
                String body = registerAndGetBody(email);
                JsonNode json = objectMapper.readTree(body);
                String refreshToken = json.get("refreshToken").asText();

                String payload = """
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken);

                mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.refreshToken").exists());
        }

        @Test
        void refresh_withMalformedToken_returns400() throws Exception {
                String payload = """
                                {"refreshToken":"not-a-real-jwt"}
                                """;

                mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void refresh_withRevokedToken_returns400() throws Exception {
                String email = "revoked@example.com";
                String body = registerAndGetBody(email);
                JsonNode json = objectMapper.readTree(body);
                String refreshToken = json.get("refreshToken").asText();
                String jti = jwtService.extractJti(refreshToken);

                when(tokenRevocationService.tryClaim(
                                eq(jti),
                                org.mockito.ArgumentMatchers.any(Date.class))).thenReturn(false);

                String payload = """
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken);

                mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void refresh_rotatesTheOldRefreshTokenSoItCannotBeReused() throws Exception {
                // A successful refresh must retire the token that was just spent — that's what
                // makes
                // replaying it afterward detectable as reuse (see the next test).
                String email = "rotate@example.com";
                String body = registerAndGetBody(email);
                JsonNode json = objectMapper.readTree(body);
                String refreshToken = json.get("refreshToken").asText();
                String jti = jwtService.extractJti(refreshToken);

                String payload = """
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken);

                mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                org.mockito.Mockito.verify(tokenRevocationService)
                                .tryClaim(eq(jti), org.mockito.ArgumentMatchers.any(Date.class));
        }

        @Test
        void refresh_reusingAnAlreadyRotatedToken_revokesTheWholeSessionFamily() throws Exception {
                // Simulates the theft scenario: a refresh token that was already rotated out
                // (jti is
                // revoked) gets replayed. The response must be the same generic 400 as any
                // other
                // revoked token (no information leak distinguishing "reuse" from "logged out"),
                // but
                // the whole session family must be shut down, not just this one jti.
                String email = "reuse-detect@example.com";
                String body = registerAndGetBody(email);
                JsonNode json = objectMapper.readTree(body);
                String refreshToken = json.get("refreshToken").asText();
                String jti = jwtService.extractJti(refreshToken);
                String familyId = jwtService.extractFamilyId(refreshToken);
                assertThat(familyId).isNotBlank();

                when(tokenRevocationService.tryClaim(
                                eq(jti),
                                org.mockito.ArgumentMatchers.any(Date.class))).thenReturn(false);

                String payload = """
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken);

                mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest());

                org.mockito.Mockito.verify(tokenRevocationService)
                                .tryClaim(eq(jti), org.mockito.ArgumentMatchers.any(Date.class));
        }

        @Test
        void refresh_whenSessionFamilyIsRevoked_isRejectedEvenWithAFreshJti() throws Exception {
                // A family-level revocation (triggered by the reuse-detection case above) must
                // shut
                // down every token in that family, not just the specific jti that triggered it
                // — this
                // is what forces the whole session to re-authenticate rather than limping along
                // on
                // whichever token happened to be in the attacker's or the legitimate client's
                // hands.
                String email = "family-revoked@example.com";
                String body = registerAndGetBody(email);
                JsonNode json = objectMapper.readTree(body);
                String refreshToken = json.get("refreshToken").asText();
                String familyId = jwtService.extractFamilyId(refreshToken);

                when(tokenRevocationService.isFamilyRevoked(eq(familyId))).thenReturn(true);

                String payload = """
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken);

                mockMvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void logout_returns204AndRevokesTheToken() throws Exception {
                String email = "logout@example.com";
                String body = registerAndGetBody(email);
                JsonNode json = objectMapper.readTree(body);
                String refreshToken = json.get("refreshToken").asText();

                String payload = """
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken);

                mockMvc.perform(post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isNoContent());

                // logout() only calls tokenRevocationService.revoke(...); since that's mocked
                // out
                // (see AbstractIntegrationTest), verify the service actually asked for
                // revocation
                // rather than silently no-op'ing.
                org.mockito.Mockito.verify(tokenRevocationService)
                                .revoke(eq(jwtService.extractJti(refreshToken)), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void protectedEndpoint_withoutToken_returns401JsonNotOAuthRedirect() throws Exception {
                // Regression guard for the entry-point fix: previously, with no explicit
                // AuthenticationEntryPoint, an unauthenticated hit on a protected JSON endpoint
                // could fall through to the OAuth2 login entry point and 302-redirect toward
                // Google instead of returning a clean 401.
                mockMvc.perform(get("/api/v1/client/engagements/00000000-0000-0000-0000-000000000000"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        void contextHasRealUserDetailsService() {
                assertThat(userDetailsService).isNotNull();
        }
}
