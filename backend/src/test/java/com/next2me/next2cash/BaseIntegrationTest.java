package com.next2me.next2cash;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for all Spring Boot integration tests in Next2Cash.
 *
 * Provides:
 *   - Full Spring application context (@SpringBootTest)
 *   - MockMvc for simulating HTTP calls to controllers (@AutoConfigureMockMvc)
 *   - "test" profile active -> uses H2 database (application-test.properties)
 *   - Transactional rollback after each test -> clean DB state between tests
 *
 * Usage:
 *   class MyControllerTest extends BaseIntegrationTest {
 *       @Test
 *       void testSomething() throws Exception {
 *           mockMvc.perform(get("/api/...")).andExpect(status().isOk());
 *       }
 *   }
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}