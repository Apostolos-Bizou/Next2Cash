package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.support.PolarisTestSupport;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Polaris P&L Phase 1 -- /api/polaris/forecast (assumptions + actuals).
 *
 * Access rules are shared with the payroll endpoints (see
 * PolarisPayrollControllerTest); this class focuses on CRUD + validation,
 * plus the read-only checks for viewer and the no-role accountant.
 */
class PolarisForecastControllerTest extends BaseIntegrationTest {

    private static final String ASSUMPTIONS = "/api/polaris/forecast/assumptions";
    private static final String ACTUALS = "/api/polaris/forecast/actuals";

    @Autowired private TestDataBuilder tdb;
    @Autowired private PolarisTestSupport pts;

    private CompanyEntity polaris;
    private User admin;

    @BeforeEach
    void setup() {
        polaris = pts.createPolaris();
        admin = tdb.createAdmin("apostolos");
        pts.assignWithRole(admin, polaris, "admin");
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder req) {
        return req.param("entityId", polaris.getId().toString())
            .header("Authorization", tdb.bearerToken(user))
            .contentType(MediaType.APPLICATION_JSON);
    }

    private String idOf(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    // ─── forecast_assumptions ────────────────────────────────────────────

    @Test
    @DisplayName("assumptions: create -> list (ordered by key) -> update -> delete")
    void assumptions_crud() throws Exception {
        String fx = idOf(mockMvc.perform(as(admin, post(ASSUMPTIONS))
                .content("{\"key\":\"fx_usd_eur\",\"value\":0.9215}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").value("fx_usd_eur"))
            .andExpect(jsonPath("$.data.value").value(0.9215))
            .andReturn());
        mockMvc.perform(as(admin, post(ASSUMPTIONS)).content("{\"key\":\"card_fee_pct\",\"value\":2.5}"))
            .andExpect(status().isOk());

        mockMvc.perform(as(admin, get(ASSUMPTIONS)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.data[0].key").value("card_fee_pct"))
            .andExpect(jsonPath("$.data[1].key").value("fx_usd_eur"));

        mockMvc.perform(as(admin, put(ASSUMPTIONS + "/" + fx)).content("{\"value\":0.93}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").value(0.93))
            .andExpect(jsonPath("$.data.key").value("fx_usd_eur"));

        mockMvc.perform(as(admin, delete(ASSUMPTIONS + "/" + fx)))
            .andExpect(status().isOk());
        mockMvc.perform(as(admin, get(ASSUMPTIONS + "/" + fx)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("assumptions: duplicate key on create or rename -> 409")
    void assumptions_duplicateKey_returns409() throws Exception {
        mockMvc.perform(as(admin, post(ASSUMPTIONS)).content("{\"key\":\"fx_usd_eur\",\"value\":0.92}"))
            .andExpect(status().isOk());
        mockMvc.perform(as(admin, post(ASSUMPTIONS)).content("{\"key\":\"fx_usd_eur\",\"value\":0.95}"))
            .andExpect(status().isConflict());

        String other = idOf(mockMvc.perform(as(admin, post(ASSUMPTIONS)).content("{\"key\":\"fx_php_usd\",\"value\":0.017}"))
            .andExpect(status().isOk()).andReturn());
        mockMvc.perform(as(admin, put(ASSUMPTIONS + "/" + other)).content("{\"key\":\"fx_usd_eur\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("assumptions: missing key or value -> 400")
    void assumptions_validation() throws Exception {
        mockMvc.perform(as(admin, post(ASSUMPTIONS)).content("{\"value\":1}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, post(ASSUMPTIONS)).content("{\"key\":\"x\"}"))
            .andExpect(status().isBadRequest());
    }

    // ─── actuals_rows ────────────────────────────────────────────────────

    @Test
    @DisplayName("actuals: create (defaults amounts to 0) -> update -> delete")
    void actuals_crud() throws Exception {
        String id = idOf(mockMvc.perform(as(admin, post(ACTUALS)).content(
                "{\"groupKey\":\"revenue\",\"label\":\"Membership fees\","
                + "\"changeAmount\":15400.50,\"periodStart\":\"2026-09-01\",\"periodEnd\":\"2026-09-30\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.beginAmount").value(0))
            .andExpect(jsonPath("$.data.changeAmount").value(15400.50))
            .andExpect(jsonPath("$.data.periodEnd").value("2026-09-30"))
            .andReturn());

        mockMvc.perform(as(admin, put(ACTUALS + "/" + id)).content("{\"label\":\"Membership fees (net)\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.label").value("Membership fees (net)"))
            .andExpect(jsonPath("$.data.groupKey").value("revenue"));

        mockMvc.perform(as(admin, get(ACTUALS)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(as(admin, delete(ACTUALS + "/" + id)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("actuals: single-field PUT leaves beginAmount / changeAmount untouched")
    void actuals_partialUpdate_keepsAmounts() throws Exception {
        // Same latent defect as payroll_people (28 Oct 2026): ZERO field
        // initializers on the entity made a label-only PUT reset both amounts.
        String id = idOf(mockMvc.perform(as(admin, post(ACTUALS)).content(
                "{\"groupKey\":\"revenue\",\"label\":\"Membership fees\",\"beginAmount\":401606.00,"
                + "\"changeAmount\":364913.00,\"periodStart\":\"2026-01-01\",\"periodEnd\":\"2026-10-27\"}"))
            .andExpect(status().isOk()).andReturn());

        mockMvc.perform(as(admin, put(ACTUALS + "/" + id)).content("{\"label\":\"Membership fees (net)\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.label").value("Membership fees (net)"))
            .andExpect(jsonPath("$.data.beginAmount").value(401606.00))
            .andExpect(jsonPath("$.data.changeAmount").value(364913.00));

        mockMvc.perform(as(admin, get(ACTUALS + "/" + id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.beginAmount").value(401606.00))
            .andExpect(jsonPath("$.data.changeAmount").value(364913.00));
    }

    @Test
    @DisplayName("actuals: periodEnd before periodStart -> 400 (create and update)")
    void actuals_invalidPeriod_returns400() throws Exception {
        mockMvc.perform(as(admin, post(ACTUALS)).content(
                "{\"groupKey\":\"revenue\",\"label\":\"X\",\"periodStart\":\"2026-09-30\",\"periodEnd\":\"2026-09-01\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("periodEnd must be on or after periodStart"));

        String id = idOf(mockMvc.perform(as(admin, post(ACTUALS)).content(
                "{\"groupKey\":\"revenue\",\"label\":\"X\",\"periodStart\":\"2026-09-01\",\"periodEnd\":\"2026-09-30\"}"))
            .andExpect(status().isOk()).andReturn());
        mockMvc.perform(as(admin, put(ACTUALS + "/" + id)).content("{\"periodEnd\":\"2026-08-31\"}"))
            .andExpect(status().isBadRequest());
    }

    // ─── Access control ──────────────────────────────────────────────────

    @Test
    @DisplayName("Polaris editor: assumptions 403 (GET and writes), actuals full CRUD 200")
    void polarisEditor_assumptionsForbidden_actualsCrud() throws Exception {
        String aid = idOf(mockMvc.perform(as(admin, post(ASSUMPTIONS)).content("{\"key\":\"fx\",\"value\":1}"))
            .andExpect(status().isOk()).andReturn());
        User editor = tdb.createUser("polaris-editor");
        pts.assignWithRole(editor, polaris, "editor");

        mockMvc.perform(as(editor, get(ASSUMPTIONS))).andExpect(status().isForbidden());
        mockMvc.perform(as(editor, get(ASSUMPTIONS + "/" + aid))).andExpect(status().isForbidden());
        mockMvc.perform(as(editor, post(ASSUMPTIONS)).content("{\"key\":\"y\",\"value\":1}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(as(editor, put(ASSUMPTIONS + "/" + aid)).content("{\"value\":2}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(as(editor, delete(ASSUMPTIONS + "/" + aid))).andExpect(status().isForbidden());

        String rid = idOf(mockMvc.perform(as(editor, post(ACTUALS)).content(
                "{\"groupKey\":\"g\",\"label\":\"l\",\"periodStart\":\"2026-09-01\",\"periodEnd\":\"2026-09-30\"}"))
            .andExpect(status().isOk()).andReturn());
        mockMvc.perform(as(editor, get(ACTUALS))).andExpect(status().isOk());
        mockMvc.perform(as(editor, put(ACTUALS + "/" + rid)).content("{\"label\":\"l2\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(as(editor, delete(ACTUALS + "/" + rid))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Polaris viewer: actuals GET 200, actuals writes + assumptions 403; global VIEWER: 403 everywhere")
    void viewers_readActualsOnly() throws Exception {
        User polarisViewer = tdb.createUser("polaris-viewer");
        pts.assignWithRole(polarisViewer, polaris, "viewer");
        User globalViewer = tdb.createViewer("investor");
        pts.assignWithRole(globalViewer, polaris, "admin");

        mockMvc.perform(as(polarisViewer, get(ACTUALS))).andExpect(status().isOk());
        mockMvc.perform(as(globalViewer, get(ACTUALS))).andExpect(status().isForbidden());

        for (User u : new User[] { polarisViewer, globalViewer }) {
            mockMvc.perform(as(u, get(ASSUMPTIONS))).andExpect(status().isForbidden());
            mockMvc.perform(as(u, post(ACTUALS)).content(
                    "{\"groupKey\":\"g\",\"label\":\"l\",\"periodStart\":\"2026-09-01\",\"periodEnd\":\"2026-09-30\"}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("accountant without a Polaris role -> 403 (Leonidas-style)")
    void accountantWithoutRole_forbidden() throws Exception {
        User leonidas = tdb.createAccountant("leonidas");
        tdb.assignEntities(leonidas, polaris);
        mockMvc.perform(as(leonidas, get(ASSUMPTIONS))).andExpect(status().isForbidden());
        mockMvc.perform(as(leonidas, get(ACTUALS))).andExpect(status().isForbidden());
    }
}
