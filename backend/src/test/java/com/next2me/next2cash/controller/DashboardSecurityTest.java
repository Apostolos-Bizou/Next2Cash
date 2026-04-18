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
 * Security tests for entity-level access control on /api/dashboard.
 *
 * Verifies that:
 *   - admin sees ALL entities (hardcoded bypass)
 *   - user with assignments sees ONLY those entities (cross-entity blocked)
 *   - user WITHOUT assignments sees ALL (legacy compatibility)
 *   - viewer IS ALLOWED on dashboard (unlike /api/transactions where viewer is 403)
 *     but still restricted to assigned entities
 *   - accountant is fully blocked (role-level 403 via @PreAuthorize)
 *
 * Dashboard permission matrix (differs from Transactions!):
 *   admin       -> 200 any entity
 *   user        -> 200 assigned / 403 unassigned / 200 all if no assignments
 *   viewer      -> 200 assigned / 403 unassigned
 *   accountant  -> 403 always (blocked at class level)
 */
class DashboardSecurityTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder tdb;

    @Test
    @DisplayName("admin can access dashboard of ANY entity")
    void admin_seesAnyEntity_returns200() throws Exception {
        User admin = tdb.createAdmin("apostolos");
        CompanyEntity[] entities = tdb.createStandardEntities();
        String bearer = tdb.bearerToken(admin);

        for (CompanyEntity e : entities) {
            mockMvc.perform(get("/api/dashboard")
                    .param("entityId", e.getId().toString())
                    .header("Authorization", bearer))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("user assigned to House can access House dashboard")
    void userAssignedToHouse_accessingHouse_returns200() throws Exception {
        User user = tdb.createUser("sissy");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(user, house);

        mockMvc.perform(get("/api/dashboard")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("user assigned to House is BLOCKED from Next2Me dashboard (403)")
    void userAssignedToHouse_accessingNext2Me_returns403() throws Exception {
        // This is the Phase C leak: before the fix, this returned 200 (full dashboard
        // of a non-assigned entity). After the fix, returns 403.
        User user = tdb.createUser("sissy");
        CompanyEntity house   = tdb.createEntity("HOUSE",   "House");
        CompanyEntity next2me = tdb.createEntity("NEXT2ME", "Next2Me");
        tdb.assignEntities(user, house);

        mockMvc.perform(get("/api/dashboard")
                .param("entityId", next2me.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("user with NO explicit assignment sees all dashboards (legacy compat)")
    void userWithoutAssignment_seesAll_legacyCompat() throws Exception {
        User legacyUser = tdb.createUser("legacy-user");
        CompanyEntity[] entities = tdb.createStandardEntities();
        // No tdb.assignEntities() call -> intentionally empty (apostolos-style legacy)

        String bearer = tdb.bearerToken(legacyUser);
        for (CompanyEntity e : entities) {
            mockMvc.perform(get("/api/dashboard")
                    .param("entityId", e.getId().toString())
                    .header("Authorization", bearer))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("viewer assigned to House CAN access House dashboard (200)")
    void viewerAssignedToHouse_accessingHouse_returns200() throws Exception {
        // Key difference from TransactionSecurityTest: viewer IS allowed on dashboard.
        User viewer = tdb.createViewer("sophia");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(viewer, house);

        mockMvc.perform(get("/api/dashboard")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(viewer)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("accountant is BLOCKED from GET /api/dashboard (403)")
    void accountant_gettingDashboard_returns403() throws Exception {
        User accountant = tdb.createAccountant("george");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(accountant, house);

        mockMvc.perform(get("/api/dashboard")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(accountant)))
            .andExpect(status().isForbidden());
    }
}