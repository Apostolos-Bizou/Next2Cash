package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.PayrollPerson;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.PayrollPersonRepository;
import com.next2me.next2cash.support.PolarisTestSupport;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Polaris P&L Phase 1 -- /api/polaris/payroll security + CRUD.
 *
 * Scenarios exercised here (the full per-endpoint role matrix lives in
 * PolarisPreAuthorizeInvariantTest):
 *   global ADMIN + entity_roles admin   -> full CRUD
 *   global USER  + entity_roles admin   -> full CRUD (no global-ADMIN gate)
 *   global ADMIN + entity_roles editor  -> people GET 200, people writes 403, milestones 403
 *   global ADMIN, no entity_roles row   -> 403 (no bypass)
 *   global VIEWER + entity_roles viewer -> 403 everywhere (Spring gate)
 *   Simos-style viewer (next2megroup)   -> 403 even with a Polaris role row
 *   non-Polaris entityId                -> 400
 */
class PolarisPayrollControllerTest extends BaseIntegrationTest {

    private static final String PEOPLE = "/api/polaris/payroll/people";
    private static final String MILESTONES = "/api/polaris/payroll/milestones";

    @Autowired private TestDataBuilder tdb;
    @Autowired private PolarisTestSupport pts;
    @Autowired private PayrollPersonRepository payrollPersonRepository;

    private CompanyEntity polaris;
    private CompanyEntity group;
    private User admin;

    @BeforeEach
    void setup() {
        group = tdb.createEntity("next2megroup", "Next2Me Group");
        polaris = pts.createPolaris();
        admin = tdb.createAdmin("apostolos");
        pts.assignWithRole(admin, polaris, "admin");
    }

    private String personJson(String office, String name) {
        return "{\"office\":\"" + office + "\",\"name\":\"" + name + "\",\"roleTitle\":\"Engineer\","
            + "\"salary\":2500.00,\"raisePct\":5.00,\"effectiveFromYear\":2027,\"isOfficeCost\":false}";
    }

    private String createPerson(User user) throws Exception {
        MvcResult res = mockMvc.perform(post(PEOPLE)
                .param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(personJson("athens", "Maria P.")))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    // ─── Happy path ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Polaris admin: people create -> list -> get -> update -> delete")
    void admin_peopleCrud() throws Exception {
        String bearer = tdb.bearerToken(admin);
        String pid = polaris.getId().toString();

        String id = createPerson(admin);

        mockMvc.perform(get(PEOPLE).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.data[0].office").value("ATHENS"))
            .andExpect(jsonPath("$.data[0].entityId").value(pid));

        mockMvc.perform(put(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"salary\":3000.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.salary").value(3000.00))
            .andExpect(jsonPath("$.data.name").value("Maria P."));

        mockMvc.perform(delete(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(id));

        mockMvc.perform(get(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("people: single-field PUT leaves salary / raisePct / isOfficeCost / effectiveFromYear untouched")
    void admin_peoplePartialUpdate_keepsOmittedFields() throws Exception {
        // Regression for the 28 Oct 2026 incident: PUT {office} zeroed salary,
        // raisePct and isOfficeCost on every Polaris row because the entity's
        // field initializers made those fields non-null in the request body.
        String bearer = tdb.bearerToken(admin);
        String pid = polaris.getId().toString();
        String id = createPerson(admin);   // salary 2500.00, raisePct 5.00, effectiveFromYear 2027

        // make isOfficeCost true first so a silent reset to false is detectable
        mockMvc.perform(put(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"isOfficeCost\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isOfficeCost").value(true))
            .andExpect(jsonPath("$.data.salary").value(2500.00));

        mockMvc.perform(put(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"office\":\"MANILA\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.office").value("MANILA"))
            .andExpect(jsonPath("$.data.salary").value(2500.00))
            .andExpect(jsonPath("$.data.raisePct").value(5.00))
            .andExpect(jsonPath("$.data.isOfficeCost").value(true))
            .andExpect(jsonPath("$.data.effectiveFromYear").value(2027))
            .andExpect(jsonPath("$.data.name").value("Maria P."));

        // and the stored row agrees with the response
        mockMvc.perform(get(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.salary").value(2500.00))
            .andExpect(jsonPath("$.data.raisePct").value(5.00))
            .andExpect(jsonPath("$.data.isOfficeCost").value(true));
    }

    @Test
    @DisplayName("Polaris admin: milestone targetMonth is normalized to the 1st of the month")
    void admin_milestoneNormalized() throws Exception {
        mockMvc.perform(post(MILESTONES).param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetMonth\":\"2027-03-15\",\"membersTarget\":1200}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetMonth").value("2027-03-01"))
            .andExpect(jsonPath("$.data.membersTarget").value(1200));
    }

    // ─── Validation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("missing name -> 400 with {success:false}")
    void create_missingName_returns400() throws Exception {
        mockMvc.perform(post(PEOPLE).param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"office\":\"ATHENS\",\"effectiveFromYear\":2027}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("name is required"));
    }

    @Test
    @DisplayName("negative salary / missing effectiveFromYear / negative membersTarget -> 400")
    void create_invalidValues_return400() throws Exception {
        String bearer = tdb.bearerToken(admin);
        String pid = polaris.getId().toString();

        mockMvc.perform(post(PEOPLE).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"office\":\"ATHENS\",\"name\":\"X\",\"salary\":-1,\"effectiveFromYear\":2027}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post(PEOPLE).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"office\":\"ATHENS\",\"name\":\"X\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post(MILESTONES).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetMonth\":\"2027-03-01\",\"membersTarget\":-5}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unknown id -> 404")
    void unknownId_returns404() throws Exception {
        mockMvc.perform(get(PEOPLE + "/" + UUID.randomUUID()).param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("row of another entity is invisible through the Polaris scope (404)")
    void foreignEntityRow_returns404() throws Exception {
        PayrollPerson foreign = new PayrollPerson();
        foreign.setEntityId(group.getId());
        foreign.setOffice("ATHENS");
        foreign.setName("Foreign Row");
        foreign.setSalary(BigDecimal.TEN);
        foreign.setRaisePct(BigDecimal.ZERO);
        foreign.setIsOfficeCost(false);
        foreign.setEffectiveFromYear(2027);
        foreign = payrollPersonRepository.save(foreign);

        mockMvc.perform(get(PEOPLE + "/" + foreign.getId()).param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete(PEOPLE + "/" + foreign.getId()).param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("non-Polaris entityId -> 400, even for a global admin with a role row on it")
    void nonPolarisEntity_returns400() throws Exception {
        pts.grantRole(admin, group, "admin");
        mockMvc.perform(get(PEOPLE).param("entityId", group.getId().toString())
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("missing entityId -> 400")
    void missingEntityId_returns400() throws Exception {
        mockMvc.perform(get(PEOPLE).header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isBadRequest());
    }

    // ─── Access control ──────────────────────────────────────────────────

    @Test
    @DisplayName("Simos-style viewer (next2megroup only) -> 403 on Polaris, even with a role row")
    void simosStyleViewer_forbidden() throws Exception {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, group);
        String bearer = tdb.bearerToken(simos);

        mockMvc.perform(get(PEOPLE).param("entityId", polaris.getId().toString())
                .header("Authorization", bearer))
            .andExpect(status().isForbidden());

        pts.grantRole(simos, polaris, "viewer");  // role row without user_entities
        mockMvc.perform(get(MILESTONES).param("entityId", polaris.getId().toString())
                .header("Authorization", bearer))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("global VIEWER, even with a Polaris viewer or admin row: 403 on every payroll endpoint")
    void globalViewer_forbiddenEverywhere() throws Exception {
        String id = createPerson(admin);
        User viewer = tdb.createViewer("investor");
        pts.assignWithRole(viewer, polaris, "admin");
        String bearer = tdb.bearerToken(viewer);
        String pid = polaris.getId().toString();

        mockMvc.perform(get(PEOPLE).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(PEOPLE).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(personJson("ATHENS", "X")))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"salary\":1}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(PEOPLE + "/" + id).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(MILESTONES).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Polaris 'viewer' role (global user): 403 on people and milestones")
    void polarisViewerRole_forbidden() throws Exception {
        User viewer = tdb.createUser("polaris-viewer");
        pts.assignWithRole(viewer, polaris, "viewer");
        String bearer = tdb.bearerToken(viewer);
        String pid = polaris.getId().toString();

        mockMvc.perform(get(PEOPLE).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(MILESTONES).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Polaris 'editor': people GET 200, people writes 403, milestones GET/POST 403")
    void polarisEditor_peopleReadOnly_milestonesForbidden() throws Exception {
        User editor = tdb.createAdmin("editor-admin");
        pts.assignWithRole(editor, polaris, "editor");
        String bearer = tdb.bearerToken(editor);
        String pid = polaris.getId().toString();

        mockMvc.perform(get(PEOPLE).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isOk());
        mockMvc.perform(post(PEOPLE).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(personJson("ATHENS", "X")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(MILESTONES).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(MILESTONES).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetMonth\":\"2027-01-01\",\"membersTarget\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("global admin WITHOUT an entity_roles row -> 403 (no admin bypass)")
    void globalAdminWithoutRole_forbidden() throws Exception {
        User other = tdb.createAdmin("other-admin");
        mockMvc.perform(get(PEOPLE).param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(other)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("global USER with Polaris 'admin' role: can read AND write (no global-ADMIN gate)")
    void globalUserWithPolarisAdmin_canWrite() throws Exception {
        User sissy = tdb.createUser("sissy");
        pts.assignWithRole(sissy, polaris, "admin");
        String bearer = tdb.bearerToken(sissy);
        String pid = polaris.getId().toString();

        mockMvc.perform(post(PEOPLE).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(personJson("ATHENS", "X")))
            .andExpect(status().isOk());
        mockMvc.perform(post(MILESTONES).param("entityId", pid).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetMonth\":\"2027-01-01\",\"membersTarget\":1}"))
            .andExpect(status().isOk());
        mockMvc.perform(get(PEOPLE).param("entityId", pid).header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("no token -> 403")
    void noToken_forbidden() throws Exception {
        mockMvc.perform(get(PEOPLE).param("entityId", polaris.getId().toString()))
            .andExpect(status().isForbidden());
    }
}
