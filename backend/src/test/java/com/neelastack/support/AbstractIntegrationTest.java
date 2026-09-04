package com.neelastack.support;

import com.neelastack.security.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.Mockito.lenient;

/**
 * Shared setup for the HTTP-boundary integration tests (see AuthIntegrationTest and
 * AuthorizationIntegrationTest). These load the real Spring context — real security filter
 * chain, real JWT issuing/validation, real password hashing — against a real, ephemeral
 * PostgreSQL container (via Testcontainers), with real Flyway migrations and
 * hibernate.ddl-auto=validate, exactly as production runs. That's what pure service-layer
 * unit tests (see the *ServiceTest classes) and an H2-based substitute can't exercise:
 * Flyway migration correctness, Postgres-specific constraint/index/query behavior, and actual
 * schema-vs-entity agreement.
 *
 * @ServiceConnection wires the container's JDBC URL/username/password into the Spring context
 * automatically — no manual @DynamicPropertySource needed. The container is a static field, so
 * one instance is shared across every test class extending this one for the whole JVM run
 * (Testcontainers' default "singleton container" reuse for a @Testcontainers test suite),
 * rather than paying container-startup cost per test class.
 *
 * TokenRevocationService is the one dependency mocked out here: it talks to Redis, and pulling
 * in a real or embedded Redis just to test HTTP-level auth/authorization behavior would make
 * these tests slower and flakier for no benefit — token revocation itself isn't what's under
 * test in this class. Tests that specifically need "this refresh token was revoked" behavior
 * stub isRevoked() (or isFamilyRevoked()) to return true for the token/family in question.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected TokenRevocationService tokenRevocationService;

    @BeforeEach
    void defaultRevocationStub() {
        // Default: nothing is revoked. Individual tests override this for the specific jti (or
        // session family) under test when they need to simulate a signed-out session.
        lenient().when(tokenRevocationService.isRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        lenient().when(tokenRevocationService.isFamilyRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    }
}
