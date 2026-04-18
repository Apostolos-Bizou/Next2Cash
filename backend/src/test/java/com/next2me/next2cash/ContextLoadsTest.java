package com.next2me.next2cash;

import org.junit.jupiter.api.Test;

/**
 * Verifies that the full Spring Boot application context boots successfully
 * with the "test" profile (H2 in-memory database).
 *
 * If this test fails, the Spring configuration has an issue:
 *   - Missing bean, circular dependency, or misconfiguration
 *   - application-test.properties has a problem
 *   - H2 schema doesn't match the JPA entities
 *
 * This is the second level of verification after SmokeTest.
 */
class ContextLoadsTest extends BaseIntegrationTest {

    @Test
    void contextLoads() {
        // If we reach here, Spring context booted successfully.
        // mockMvc is injected and the test "test" profile is active.
    }
}