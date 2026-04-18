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
 * Security tests for entity-level access control on /api/bank-accounts.
 *
 * BankAccount permission matrix (Phase D, Session #6):
 *   admin       -> 200 any entity (hardcoded bypass)
 *   user        -> 200 assigned / 403 unassigned / 200 all if no assignments (legacy)
 *   viewer      -> 200 assigned (read-only) / 403 unassigned
 *   accountant  -> 403 always (blocked at class level; ZIP export is their only path)
 *
 * Same aggregate-filter pattern as Dashboard: when entityId is omitted, the
 * response is filtered to accessible entities only. We don't assert on body
 * contents here (controller already has that logic) -- we just verify that the
 * endpoint responds with the correct HTTP status for each role.
 */
class BankAccountSecurityTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder tdb;

    @Test
    @DisplayName("admin can access bank accounts of ANY entity")
    void admin_seesAnyEntity_returns200() throws Exception {
        User admin = tdb.createAdmin("apostolos");
        CompanyEntity[] entities = tdb.createStandardEntities();
        String bearer = tdb.bearerToken(admin);

        for (CompanyEntity e : entities) {
            mockMvc.perform(get("/api/bank-accounts")
                    .param("entityId", e.getId().toString())
                    .header("Authorization", bearer))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("user assigned to House can access House bank accounts (200)")
    void userAssignedToHouse_accessingHouse_returns200() throws Exception {
        User user = tdb.createUser("sissy");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(user, house);

        mockMvc.perform(get("/api/bank-accounts")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("user assigned to House is BLOCKED from Next2Me bank accounts (403)")
    void userAssignedToHouse_accessingNext2Me_returns403() throws Exception {
        // Phase D leak: before the fix, any authenticated non-accountant user could
        // fetch bank accounts of ANY entity by passing its entityId. Now: 403.
        User user = tdb.createUser("sissy");
        CompanyEntity house   = tdb.createEntity("HOUSE",   "House");
        CompanyEntity next2me = tdb.createEntity("NEXT2ME", "Next2Me");
        tdb.assignEntities(user, house);

        mockMvc.perform(get("/api/bank-accounts")
                .param("entityId", next2me.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("user with NO explicit assignment sees all bank accounts (legacy compat)")
    void userWithoutAssignment_seesAll_legacyCompat() throws Exception {
        User legacyUser = tdb.createUser("legacy-user");
        CompanyEntity[] entities = tdb.createStandardEntities();
        // No tdb.assignEntities() call -> empty assignments (apostolos-style legacy)

        String bearer = tdb.bearerToken(legacyUser);
        for (CompanyEntity e : entities) {
            mockMvc.perform(get("/api/bank-accounts")
                    .param("entityId", e.getId().toString())
                    .header("Authorization", bearer))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("viewer assigned to House CAN access House bank accounts (200, read-only)")
    void viewerAssignedToHouse_accessingHouse_returns200() throws Exception {
        // Per CEO decision (Session #6): viewer has read-only access to bank accounts.
        User viewer = tdb.createViewer("sophia");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(viewer, house);

        mockMvc.perform(get("/api/bank-accounts")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(viewer)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("accountant is BLOCKED from GET /api/bank-accounts (403)")
    void accountant_gettingBankAccounts_returns403() throws Exception {
        User accountant = tdb.createAccountant("george");
        CompanyEntity house = tdb.createEntity("HOUSE", "House");
        tdb.assignEntities(accountant, house);

        mockMvc.perform(get("/api/bank-accounts")
                .param("entityId", house.getId().toString())
                .header("Authorization", tdb.bearerToken(accountant)))
            .andExpect(status().isForbidden());
    }
}