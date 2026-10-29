package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.PayrollPerson;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.CostMilestoneRepository;
import com.next2me.next2cash.repository.PayrollPersonRaiseRepository;
import com.next2me.next2cash.repository.PayrollPersonRepository;
import com.next2me.next2cash.support.PolarisTestSupport;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cost_milestones + payroll_person_raises endpoints (Oct 2026).
 *
 *   actor                         | GET lists | writes
 *   ------------------------------+-----------+--------
 *   Polaris admin                 | 200       | 200
 *   Polaris editor                | 200       | 403
 *   Polaris viewer (global user)  | 403       | 403
 *   global VIEWER + viewer row    | 403       | 403   (class-level Spring gate)
 *   no entity_roles row           | 403       | 403
 */
class PolarisOpexAndRaisesControllerTest extends BaseIntegrationTest {

    private static final String COST = "/api/polaris/forecast/cost-milestones";
    private static final String RAISES = "/api/polaris/payroll/raises";
    private static final String PEOPLE = "/api/polaris/payroll/people";

    @Autowired private TestDataBuilder tdb;
    @Autowired private PolarisTestSupport pts;
    @Autowired private PayrollPersonRepository payrollPersonRepository;
    @Autowired private PayrollPersonRaiseRepository payrollPersonRaiseRepository;
    @Autowired private CostMilestoneRepository costMilestoneRepository;

    private CompanyEntity polaris;
    private User admin, editor, viewer, globalViewer, none;
    private UUID personId;

    @BeforeEach
    void setup() {
        polaris = pts.createPolaris();
        admin = tdb.createAdmin("apostolos");   pts.assignWithRole(admin, polaris, "admin");
        editor = tdb.createUser("editor");      pts.assignWithRole(editor, polaris, "editor");
        viewer = tdb.createUser("pviewer");     pts.assignWithRole(viewer, polaris, "viewer");
        globalViewer = tdb.createViewer("simos"); pts.assignWithRole(globalViewer, polaris, "viewer");
        none = tdb.createUser("none");          tdb.assignEntities(none, polaris);

        PayrollPerson p = new PayrollPerson();
        p.setEntityId(polaris.getId()); p.setOffice("ATHENS"); p.setName("Accounting Manager");
        p.setSalary(new BigDecimal("46800")); p.setRaisePct(BigDecimal.ZERO); p.setEffectiveFromYear(2027); p.setIsOfficeCost(false);
        personId = payrollPersonRepository.save(p).getId();
    }

    private MockHttpServletRequestBuilder as(User u, MockHttpServletRequestBuilder req) {
        return req.param("entityId", polaris.getId().toString())
            .header("Authorization", tdb.bearerToken(u))
            .contentType(MediaType.APPLICATION_JSON);
    }

    private String idOf(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    // ─── cost_milestones ─────────────────────────────────────────────────

    @Test
    @DisplayName("cost-milestones: admin CRUD; category validated; targetDate normalised to the 1st")
    void costMilestones_adminCrud() throws Exception {
        String id = idOf(mockMvc.perform(as(admin, post(COST))
                .content("{\"category\":\"IT / Computer\",\"targetDate\":\"2027-06-15\",\"targetValue\":24000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.category").value("IT / Computer"))
            .andExpect(jsonPath("$.data.targetDate").value("2027-06-01"))
            .andExpect(jsonPath("$.data.targetValue").value(24000))
            .andReturn());
        mockMvc.perform(as(admin, get(COST))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(as(admin, get(COST + "/" + id))).andExpect(status().isOk());
        mockMvc.perform(as(admin, put(COST + "/" + id)).content("{\"targetValue\":36000}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetValue").value(36000))
            .andExpect(jsonPath("$.data.category").value("IT / Computer"));   // partial update keeps the rest
        mockMvc.perform(as(admin, delete(COST + "/" + id))).andExpect(status().isOk());
        assertEquals(0, costMilestoneRepository.count());

        // not one of the agreed labels -> 400; negative value -> 400; missing date -> 400
        mockMvc.perform(as(admin, post(COST)).content("{\"category\":\"Ενοίκιο / utilities\",\"targetDate\":\"2027-06-01\",\"targetValue\":1}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, post(COST)).content("{\"category\":\"IT / Computer\",\"targetDate\":\"2027-06-01\",\"targetValue\":-1}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, post(COST)).content("{\"category\":\"IT / Computer\",\"targetValue\":1}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cost-milestones: editor reads, cannot write; Polaris viewer, global VIEWER and no-role -> 403 on everything")
    void costMilestones_accessMatrix() throws Exception {
        String id = idOf(mockMvc.perform(as(admin, post(COST))
                .content("{\"category\":\"Marketing / Διαφήμιση\",\"targetDate\":\"2027-06-01\",\"targetValue\":1000}"))
            .andExpect(status().isOk()).andReturn());

        mockMvc.perform(as(editor, get(COST))).andExpect(status().isOk());
        mockMvc.perform(as(editor, get(COST + "/" + id))).andExpect(status().isOk());
        mockMvc.perform(as(editor, post(COST)).content("{\"category\":\"IT / Computer\",\"targetDate\":\"2027-06-01\",\"targetValue\":1}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(as(editor, put(COST + "/" + id)).content("{\"targetValue\":2}")).andExpect(status().isForbidden());
        mockMvc.perform(as(editor, delete(COST + "/" + id))).andExpect(status().isForbidden());

        for (User u : new User[] { viewer, globalViewer, none }) {
            mockMvc.perform(as(u, get(COST))).andExpect(status().isForbidden());
            mockMvc.perform(as(u, get(COST + "/" + id))).andExpect(status().isForbidden());
            mockMvc.perform(as(u, post(COST)).content("{\"category\":\"IT / Computer\",\"targetDate\":\"2027-06-01\",\"targetValue\":1}"))
                .andExpect(status().isForbidden());
            mockMvc.perform(as(u, put(COST + "/" + id)).content("{\"targetValue\":2}")).andExpect(status().isForbidden());
            mockMvc.perform(as(u, delete(COST + "/" + id))).andExpect(status().isForbidden());
        }
        assertEquals(1, costMilestoneRepository.count());
        assertEquals(0, new BigDecimal("1000").compareTo(costMilestoneRepository.findAll().get(0).getTargetValue()), "value unchanged by the forbidden writes");
    }

    // ─── payroll_person_raises ───────────────────────────────────────────

    @Test
    @DisplayName("raises: admin CRUD under /people/{id}/raises; list is per entity; foreign person -> 404")
    void raises_adminCrud() throws Exception {
        String r1 = idOf(mockMvc.perform(as(admin, post(PEOPLE + "/" + personId + "/raises"))
                .content("{\"effectiveDate\":\"2027-01-01\",\"raisePct\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.payrollPersonId").value(personId.toString()))
            .andExpect(jsonPath("$.data.raisePct").value(5))
            .andReturn());
        String r2 = idOf(mockMvc.perform(as(admin, post(PEOPLE + "/" + personId + "/raises"))
                .content("{\"effectiveDate\":\"2029-01-01\",\"raisePct\":3}"))
            .andExpect(status().isOk()).andReturn());

        mockMvc.perform(as(admin, get(RAISES))).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.data[0].effectiveDate").value("2027-01-01"))
            .andExpect(jsonPath("$.data[1].effectiveDate").value("2029-01-01"));

        mockMvc.perform(as(admin, put(RAISES + "/" + r2)).content("{\"raisePct\":4}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.raisePct").value(4))
            .andExpect(jsonPath("$.data.effectiveDate").value("2029-01-01"));
        mockMvc.perform(as(admin, delete(RAISES + "/" + r1))).andExpect(status().isOk());
        assertEquals(1, payrollPersonRaiseRepository.count());

        // validation
        mockMvc.perform(as(admin, post(PEOPLE + "/" + personId + "/raises")).content("{\"raisePct\":5}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, post(PEOPLE + "/" + personId + "/raises")).content("{\"effectiveDate\":\"2027-01-01\",\"raisePct\":5000}"))
            .andExpect(status().isBadRequest());
        // a person of another entity -> 404, nothing created
        CompanyEntity house = tdb.createEntity("house", "House");
        PayrollPerson other = new PayrollPerson();
        other.setEntityId(house.getId()); other.setOffice("ATHENS"); other.setName("Other");
        other.setSalary(BigDecimal.TEN); other.setRaisePct(BigDecimal.ZERO); other.setEffectiveFromYear(2027); other.setIsOfficeCost(false);
        UUID otherId = payrollPersonRepository.save(other).getId();
        mockMvc.perform(as(admin, post(PEOPLE + "/" + otherId + "/raises")).content("{\"effectiveDate\":\"2027-01-01\",\"raisePct\":5}"))
            .andExpect(status().isNotFound());
        assertEquals(1, payrollPersonRaiseRepository.count());
    }

    @Test
    @DisplayName("raises: editor reads, cannot write; Polaris viewer, global VIEWER and no-role -> 403 on everything")
    void raises_accessMatrix() throws Exception {
        String rid = idOf(mockMvc.perform(as(admin, post(PEOPLE + "/" + personId + "/raises"))
                .content("{\"effectiveDate\":\"2027-01-01\",\"raisePct\":5}"))
            .andExpect(status().isOk()).andReturn());

        mockMvc.perform(as(editor, get(RAISES))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(as(editor, post(PEOPLE + "/" + personId + "/raises")).content("{\"effectiveDate\":\"2028-01-01\",\"raisePct\":2}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(as(editor, put(RAISES + "/" + rid)).content("{\"raisePct\":9}")).andExpect(status().isForbidden());
        mockMvc.perform(as(editor, delete(RAISES + "/" + rid))).andExpect(status().isForbidden());

        for (User u : new User[] { viewer, globalViewer, none }) {
            mockMvc.perform(as(u, get(RAISES))).andExpect(status().isForbidden());
            mockMvc.perform(as(u, post(PEOPLE + "/" + personId + "/raises")).content("{\"effectiveDate\":\"2028-01-01\",\"raisePct\":2}"))
                .andExpect(status().isForbidden());
            mockMvc.perform(as(u, put(RAISES + "/" + rid)).content("{\"raisePct\":9}")).andExpect(status().isForbidden());
            mockMvc.perform(as(u, delete(RAISES + "/" + rid))).andExpect(status().isForbidden());
        }
        assertEquals(1, payrollPersonRaiseRepository.count());
        assertEquals(0, new BigDecimal("5").compareTo(payrollPersonRaiseRepository.findAll().get(0).getRaisePct()), "raise unchanged by the forbidden writes");
    }
}
