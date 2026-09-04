package com.neelastack.support;

import com.neelastack.security.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.Date;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected TokenRevocationService tokenRevocationService;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {

        // PostgreSQL
        registry.add(
                "spring.datasource.url",
                IntegrationTestContainers.POSTGRES::getJdbcUrl);

        registry.add(
                "spring.datasource.username",
                IntegrationTestContainers.POSTGRES::getUsername);

        registry.add(
                "spring.datasource.password",
                IntegrationTestContainers.POSTGRES::getPassword);

        // Redis
        registry.add(
                "spring.data.redis.host",
                IntegrationTestContainers.REDIS::getHost);

        registry.add(
                "spring.data.redis.port",
                () -> IntegrationTestContainers.REDIS.getMappedPort(6379));
    }

    @BeforeEach
    void defaultRevocationStub() {
        lenient()
                .when(tokenRevocationService.isRevoked(anyString()))
                .thenReturn(false);

        lenient()
                .when(tokenRevocationService.isFamilyRevoked(anyString()))
                .thenReturn(false);

        lenient()
                .when(tokenRevocationService.tryClaim(anyString(), any(Date.class)))
                .thenReturn(true);
    }
}