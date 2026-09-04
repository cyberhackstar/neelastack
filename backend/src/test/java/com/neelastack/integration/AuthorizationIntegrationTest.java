package com.neelastack.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.EngagementStatus;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.UserRepository;
import com.neelastack.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorization matrix the audit called out (item #2): client-to-client isolation,
 * anonymous access, and role escalation attempts, all exercised through real HTTP requests
 * with real issued JWTs rather than mocked SecurityContext objects.
 */
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EngagementRepository engagementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PASSWORD = "Str0ngPassw0rd!";

    private User persistUser(String email, Role role) {
        User user = User.builder()
                .fullName("Test " + role)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .enabled(true)
                .emailVerified(true)
                .build();
        return userRepository.save(user);
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        String payload = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private Engagement persistEngagementFor(User client) {
        Engagement engagement = Engagement.builder()
                .client(client)
                .title("Test engagement")
                .status(EngagementStatus.ONBOARDING)
                .build();
        return engagementRepository.save(engagement);
    }

    @Test
    void clientA_cannotAccessClientBsEngagement() throws Exception {
        User clientA = persistUser("client-a@example.com", Role.CLIENT);
        User clientB = persistUser("client-b@example.com", Role.CLIENT);
        Engagement engagementOwnedByA = persistEngagementFor(clientA);

        String clientBToken = loginAndGetAccessToken(clientB.getEmail());

        mockMvc.perform(get("/api/v1/client/engagements/" + engagementOwnedByA.getId())
                        .header("Authorization", "Bearer " + clientBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void owningClient_canAccessTheirOwnEngagement() throws Exception {
        User clientA = persistUser("owner@example.com", Role.CLIENT);
        Engagement engagement = persistEngagementFor(clientA);

        String token = loginAndGetAccessToken(clientA.getEmail());

        mockMvc.perform(get("/api/v1/client/engagements/" + engagement.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousRequest_toClientEndpoint_returns401() throws Exception {
        User clientA = persistUser("anon-target@example.com", Role.CLIENT);
        Engagement engagement = persistEngagementFor(clientA);

        mockMvc.perform(get("/api/v1/client/engagements/" + engagement.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientRole_cannotCallAdminEndpoint_returns403() throws Exception {
        persistUser("plain-client@example.com", Role.CLIENT);
        String token = loginAndGetAccessToken("plain-client@example.com");

        mockMvc.perform(get("/api/v1/admin/engagements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRole_canCallAdminEndpoint_returns200() throws Exception {
        persistUser("real-admin@example.com", Role.ADMIN);
        String token = loginAndGetAccessToken("real-admin@example.com");

        mockMvc.perform(get("/api/v1/admin/engagements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminRole_canAccessAnyClientsEngagement() throws Exception {
        User clientA = persistUser("client-for-admin-check@example.com", Role.CLIENT);
        Engagement engagement = persistEngagementFor(clientA);
        persistUser("admin-checking-in@example.com", Role.ADMIN);

        String adminToken = loginAndGetAccessToken("admin-checking-in@example.com");

        mockMvc.perform(get("/api/v1/client/engagements/" + engagement.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void invalidBearerToken_isTreatedAsUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/engagements")
                        .header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
