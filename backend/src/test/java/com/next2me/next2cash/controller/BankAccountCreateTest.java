package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S106 — POST /api/bank-accounts (the endpoint whose absence was masked into
 * empty 403s by the unauthenticated /error dispatch) + the /error permitAll rule.
 */
class BankAccountCreateTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;

    private CompanyEntity next2me;
    private CompanyEntity house;
    private User admin;

    @BeforeEach
    void setup() {
        CompanyEntity[] ents = tdb.createStandardEntities();
        next2me = ents[0];
        house = ents[1];
        admin = tdb.createAdmin("apostolos");
    }

    private String body(String entityId, String label) {
        StringBuilder sb = new StringBuilder("{");
        if (entityId != null) sb.append("\"entityId\":\"").append(entityId).append("\",");
        if (label != null) sb.append("\"accountLabel\":\"").append(label).append("\",");
        sb.append("\"accountType\":\"checking\",\"currentBalance\":100.50}");
        return sb.toString();
    }

    // ─── happy paths ────────────────────────────────────────────────────────

    @Test
    void admin_create_201_withDefaultsAndServerStampedDate() throws Exception {
        mockMvc.perform(post("/api/bank-accounts")
                .header("Authorization", tdb.bearerToken(admin))
                .contentType("application/json")
                .content(body(next2me.getId().toString(), "Piraeus Business")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.accountLabel").value("Piraeus Business"))
            .andExpect(jsonPath("$.data.bankName").value("Piraeus Business")) // falls back to label
            .andExpect(jsonPath("$.data.currency").value("EUR"))              // default when omitted
            .andExpect(jsonPath("$.data.isActive").value(true))
            .andExpect(jsonPath("$.data.currentBalance").value(100.50))
            .andExpect(jsonPath("$.data.balanceDate").value(LocalDate.now().toString())); // server-stamped
    }

    @Test
    void user_assignedEntity_create_201() throws Exception {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house);
        mockMvc.perform(post("/api/bank-accounts")
                .header("Authorization", tdb.bearerToken(sissy))
                .contentType("application/json")
                .content(body(house.getId().toString(), "HSBC House")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.currency").value("EUR"));
    }

    // ─── role matrix ────────────────────────────────────────────────────────

    @Test
    void viewer_create_403() throws Exception {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, next2me);
        mockMvc.perform(post("/api/bank-accounts")
                .header("Authorization", tdb.bearerToken(simos))
                .contentType("application/json")
                .content(body(next2me.getId().toString(), "X")))
            .andExpect(status().isForbidden());
    }

    @Test
    void accountant_create_403() throws Exception {
        User leonidas = tdb.createAccountant("leonidas");
        tdb.assignEntities(leonidas, next2me);
        mockMvc.perform(post("/api/bank-accounts")
                .header("Authorization", tdb.bearerToken(leonidas))
                .contentType("application/json")
                .content(body(next2me.getId().toString(), "X")))
            .andExpect(status().isForbidden());
    }

    @Test
    void user_crossEntity_create_403() throws Exception {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house);   // only house
        mockMvc.perform(post("/api/bank-accounts")
                .header("Authorization", tdb.bearerToken(sissy))
                .contentType("application/json")
                .content(body(next2me.getId().toString(), "X")))  // other entity
            .andExpect(status().isForbidden());
    }

    // ─── validation ─────────────────────────────────────────────────────────

    @Test
    void missingLabel_400() throws Exception {
        mockMvc.perform(post("/api/bank-accounts")
                .header("Authorization", tdb.bearerToken(admin))
                .contentType("application/json")
                .content(body(next2me.getId().toString(), null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("accountLabel is required"));
    }

    @Test
    void missingEntityId_400() throws Exception {
        mockMvc.perform(post("/api/bank-accounts")
                .header("Authorization", tdb.bearerToken(admin))
                .contentType("application/json")
                .content(body(null, "X")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("entityId is required"));
    }

    // ─── /error is permitAll (403-unmask guard) ─────────────────────────────

    @Test
    void errorEndpoint_isNotSecured() throws Exception {
        // Anonymous /error must NOT be answered by the security layer (401/403);
        // whatever Boot's error controller returns for a bare hit is fine.
        int status = mockMvc.perform(get("/error"))
            .andReturn().getResponse().getStatus();
        assertNotEquals(401, status, "/error must not require authentication");
        assertNotEquals(403, status, "/error must not be masked by the security layer");
    }
}
