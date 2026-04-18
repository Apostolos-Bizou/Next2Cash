package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ConfigRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase E — ConfigController security tests.
 *
 * Coverage matrix for GET /api/config?entityId=X:
 *   admin + any entity          -> 200 (bypass)
 *   user + assigned entity      -> 200
 *   user + unassigned entity    -> 403
 *   user + no assignments       -> 200 (legacy rule)
 *   accountant                  -> 403 (class-level @PreAuthorize)
 *   viewer                      -> 403 (class-level @PreAuthorize)
 */
class ConfigSecurityTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;
    @Autowired private ConfigRepository configRepository;

    private CompanyEntity next2me;
    private CompanyEntity house;
    private CompanyEntity polaris;

    @BeforeEach
    void setupEntitiesAndConfig() {
        CompanyEntity[] ents = tdb.createStandardEntities();
        next2me = ents[0];
        house   = ents[1];
        polaris = ents[2];

        // Seed one config row per entity so the GET has something to return.
        saveConfig(next2me, "category", "salary",  "Salary");
        saveConfig(house,   "category", "rent",    "Rent");
        saveConfig(polaris, "category", "fees",    "Fees");
    }

    private void saveConfig(CompanyEntity entity, String type, String key, String value) {
        Config c = new Config();
        setField(c, "entityId",    entity.getId());
        setField(c, "configType",  type);
        setField(c, "configKey",   key);
        setField(c, "configValue", value);
        setField(c, "sortOrder",   1);
        setField(c, "isActive",    Boolean.TRUE);
        configRepository.save(c);
    }

    // Reflection helper because Config has no setters in the current model.
    private void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + name, e);
        }
    }

    // ────────────────────────────────────────────────────────────
    // 1. admin + any entity -> 200 (bypass)
    // ────────────────────────────────────────────────────────────
    @Test
    void admin_canAccessAnyEntity() throws Exception {
        User admin = tdb.createAdmin("apostolos");
        // No assignments needed — admin bypass.
        mockMvc.perform(get("/api/config")
                .header("Authorization", tdb.bearerToken(admin))
                .param("entityId", polaris.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.categories").isArray());
    }

    // ────────────────────────────────────────────────────────────
    // 2. user + assigned entity -> 200
    // ────────────────────────────────────────────────────────────
    @Test
    void user_canAccessAssignedEntity() throws Exception {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house);

        mockMvc.perform(get("/api/config")
                .header("Authorization", tdb.bearerToken(sissy))
                .param("entityId", house.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // ────────────────────────────────────────────────────────────
    // 3. user + unassigned entity -> 403
    // ────────────────────────────────────────────────────────────
    @Test
    void user_cannotAccessUnassignedEntity() throws Exception {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house);  // only house

        mockMvc.perform(get("/api/config")
                .header("Authorization", tdb.bearerToken(sissy))
                .param("entityId", polaris.getId().toString()))  // polaris not assigned
            .andExpect(status().isForbidden());
    }

    // ────────────────────────────────────────────────────────────
    // 4. user with zero assignments -> legacy rule: see all
    // ────────────────────────────────────────────────────────────
    @Test
    void user_withNoAssignments_legacySeesAll() throws Exception {
        User legacy = tdb.createUser("legacy_user");
        // Intentionally NO tdb.assignEntities(...) — legacy apostolos-style.

        mockMvc.perform(get("/api/config")
                .header("Authorization", tdb.bearerToken(legacy))
                .param("entityId", polaris.getId().toString()))
            .andExpect(status().isOk());
    }

    // ────────────────────────────────────────────────────────────
    // 5. accountant -> 403 (class-level @PreAuthorize blocks)
    // ────────────────────────────────────────────────────────────
    @Test
    void accountant_isForbidden() throws Exception {
        User george = tdb.createAccountant("george");
        tdb.assignEntities(george, house);

        mockMvc.perform(get("/api/config")
                .header("Authorization", tdb.bearerToken(george))
                .param("entityId", house.getId().toString()))
            .andExpect(status().isForbidden());
    }

    // ────────────────────────────────────────────────────────────
    // 6. viewer -> 403 (class-level @PreAuthorize blocks)
    // ────────────────────────────────────────────────────────────
    @Test
    void viewer_canReadConfig() throws Exception {
        // Per role matrix: VIEWER has read-only access to Config
        // (Dashboard + AI + Banks + Config).
        User sophia = tdb.createViewer("sophia");
        tdb.assignEntities(sophia, house);

        mockMvc.perform(get("/api/config")
                .header("Authorization", tdb.bearerToken(sophia))
                .param("entityId", house.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // ────────────────────────────────────────────────────────────
    // 7. no JWT -> 403 (class-level @PreAuthorize blocks anonymous)
    //    Note: with method security enabled, Spring returns 403 for
    //    anonymous users who lack the required role, not 401.
    // ────────────────────────────────────────────────────────────
    @Test
    void noToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/config")
                .param("entityId", house.getId().toString()))
            .andExpect(status().isForbidden());
    }

    // ═════ Phase H v2 — Cards endpoint security ═════

    @Test
    void cards_list_viewerCanRead() throws Exception {
        User sophia = tdb.createViewer("sophia_cards_list");
        tdb.assignEntities(sophia, house);

        mockMvc.perform(get("/api/config/cards")
                .header("Authorization", tdb.bearerToken(sophia))
                .param("entityId", house.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void cards_create_viewerForbidden() throws Exception {
        User sophia = tdb.createViewer("sophia_cards_create");
        tdb.assignEntities(sophia, house);

        String body = "{\"configKey\":\"test_card\",\"configValue\":\"Test\",\"parentKey\":\"search:X\"}";

        mockMvc.perform(post("/api/config/cards")
                .header("Authorization", tdb.bearerToken(sophia))
                .param("entityId", house.getId().toString())
                .contentType("application/json")
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void cards_create_userCanCreateInAssignedEntity() throws Exception {
        User sissy = tdb.createUser("sissy_cards");
        tdb.assignEntities(sissy, house);

        String body = "{\"configKey\":\"rent_card\",\"configValue\":\"Rent\",\"parentKey\":\"category:RENT\",\"sortOrder\":5}";

        mockMvc.perform(post("/api/config/cards")
                .header("Authorization", tdb.bearerToken(sissy))
                .param("entityId", house.getId().toString())
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.configKey").value("rent_card"));
    }

    @Test
    void cards_list_noTokenForbidden() throws Exception {
        mockMvc.perform(get("/api/config/cards")
                .param("entityId", house.getId().toString()))
            .andExpect(status().isForbidden());
    }
}