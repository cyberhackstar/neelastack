package com.neelastack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: the full Spring context boots against a real PostgreSQL container, with real
 * Flyway migrations and hibernate.ddl-auto=validate (same as production) — not an in-memory
 * H2 substitute. This is what actually proves the Flyway migration set is valid and the JPA
 * entity mappings agree with the schema they produce; H2's Postgres-compatibility mode only
 * proves that against its own approximation of Postgres.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NeelastackApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
    }
}
