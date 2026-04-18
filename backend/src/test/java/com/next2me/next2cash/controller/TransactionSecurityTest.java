package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for entity-level access control on /api/transactions.
 *
 * Verifies that:
 *   - admin sees ALL entities (hardcoded bypass)
 *   - user with assignments sees ONLY those entities (cross-entity blocked)
 *   - user WITHOUT assignments sees ALL (legacy compatibility)
 *   - accountant is fully blocked (role-level 403 via @PreAuthorize)
 *   - viewer is fully blocked (role-level 403 via @PreAuthorize)
 *
 * All 6 tests should PASS against the current code (Phase B ported + UserEntityLink).
 */
class TransactionSecurityTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder tdb;

    @Test
    @DisplayName("admin can access transactions of ANY entity")
    void admin_seesAnyEntity_returns200() throws Exception {
        User admin = tdb.createAdmin("apostolos");
        CompanyEntity[] entities = tdb.createStandardEntities();
        String bearer = tdb.bearerToken(admin);

        for (CompanyEntity e : entities) {
            mockMvc.perform(get("/api/transactions")
                    .param("entityId", e.getId().toString())
                    .header("Authorization", bearer))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("user assigned to House can access House transactions")
    void userAssignedToHouse_accessingHouse_returns200() throws Exception {
        User user = tdb.createUser("sissy");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(user, house);

        mockMvc.perform(get("/api/transactions")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("user assigned to House is BLOCKED from Next2Me transactions (403)")
    void userAssignedToHouse_accessingNext2Me_returns403() throws Exception {
        // The critical bug that Phase B fixes.
        // Before Phase B: returned 200 (leak). After Phase B: returns 403 (forbidden).
        User user = tdb.createUser("sissy");
        CompanyEntity house   = tdb.createEntity("HOUSE",   "House");
        CompanyEntity next2me = tdb.createEntity("NEXT2ME", "Next2Me");
        tdb.assignEntities(user, house);

        mockMvc.perform(get("/api/transactions")
                .param("entityId", next2me.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("user with NO explicit assignment sees all (legacy compat)")
    void userWithoutAssignment_seesAll_legacyCompat() throws Exception {
        User legacyUser = tdb.createUser("legacy-user");
        CompanyEntity[] entities = tdb.createStandardEntities();
        // No tdb.assignEntities() call -> intentionally empty

        String bearer = tdb.bearerToken(legacyUser);
        for (CompanyEntity e : entities) {
            mockMvc.perform(get("/api/transactions")
                    .param("entityId", e.getId().toString())
                    .header("Authorization", bearer))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("accountant is BLOCKED from GET /api/transactions (403)")
    void accountant_gettingTransactions_returns403() throws Exception {
        User accountant = tdb.createAccountant("george");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(accountant, house);

        mockMvc.perform(get("/api/transactions")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(accountant)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("viewer is BLOCKED from GET /api/transactions (403)")
    void viewer_gettingTransactions_returns403() throws Exception {
        User viewer = tdb.createViewer("sophia");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(viewer, house);

        mockMvc.perform(get("/api/transactions")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(viewer)))
            .andExpect(status().isForbidden());
    }
}