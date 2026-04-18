package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baseline tests for TransactionController.
 *
 * Captures the CURRENT behavior: auth required, but NO entity filtering yet.
 * Phase B will change the entity-filtering behavior; these baseline tests
 * still have to pass (they don't test the filtering).
 */
class TransactionControllerTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder tdb;

    @Test
    void getTransactions_withValidJwtAndEntityId_returns200() throws Exception {
        // Arrange: admin user + one entity (empty transactions is fine)
        User admin = tdb.createAdmin("apostolos");
        CompanyEntity entity = tdb.createEntity("TEST", "Test Entity");
        String bearerToken = tdb.bearerToken(admin);

        // Act + Assert: expect successful response with empty data array
        mockMvc.perform(get("/api/transactions")
                .param("entityId", entity.getId().toString())
                .header("Authorization", bearerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void getTransactions_withoutAuth_returns401or403() throws Exception {
        CompanyEntity entity = tdb.createEntity("TEST", "Test Entity");

        // No Authorization header -> Spring Security rejects
        // Spring Security typically returns 401 or 403 depending on config
        mockMvc.perform(get("/api/transactions")
                .param("entityId", entity.getId().toString()))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("Expected 401 or 403 but got " + status);
                }
            });
    }
}