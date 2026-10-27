package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.*;
import com.next2me.next2cash.repository.ActualsRowRepository;
import com.next2me.next2cash.repository.ForecastAssumptionRepository;
import com.next2me.next2cash.repository.PayrollMilestoneRepository;
import com.next2me.next2cash.repository.PayrollPersonRepository;
import com.next2me.next2cash.support.PolarisTestSupport;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invariant 1 + per-endpoint access matrix for the Polaris controllers.
 *
 * Part A (reflection): every Polaris endpoint is gated by the class-level
 * @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT')") -- no method-level
 * override, and VIEWER never appears (so it can never reach a write).
 *
 * Part B (HTTP matrix): 4 resources x 5 operations x 5 actors, through MockMvc.
 *
 *   resource     | GET (list/one)  | POST / PUT / DELETE
 *   -------------+-----------------+--------------------
 *   people       | admin, editor   | admin
 *   milestones   | admin           | admin
 *   assumptions  | admin           | admin
 *   actuals      | admin, editor, viewer | admin, editor
 *
 * Part C: my-role, Part D: payroll summary (GET-only, aggregate, any role).
 *
 * Actors: Polaris admin / editor / viewer and a user with NO role all hold the
 * global role 'user', so only entity_roles decides. global-viewer holds a
 * Polaris admin row but the global role 'viewer' -> 403 everywhere at the
 * Spring gate.
 */
class PolarisPreAuthorizeInvariantTest extends BaseIntegrationTest {

    private static final String SPRING_GATE = "hasAnyRole('ADMIN','USER','ACCOUNTANT')";
    private static final List<Class<?>> CONTROLLERS =
        List.of(PolarisPayrollController.class, PolarisForecastController.class, PolarisAccessController.class);

    // ─── Part A: reflection ──────────────────────────────────────────────

    @Test
    @DisplayName("A1: class-level gate is ADMIN/USER/ACCOUNTANT on every Polaris controller (never VIEWER)")
    void classLevelGate() {
        for (Class<?> c : CONTROLLERS) {
            PreAuthorize pa = c.getAnnotation(PreAuthorize.class);
            assertNotNull(pa, c.getSimpleName() + " needs class-level @PreAuthorize");
            assertEquals(SPRING_GATE, pa.value(), c.getSimpleName());
            assertFalse(pa.value().contains("VIEWER"), c.getSimpleName() + " admits VIEWER");
        }
    }

    @Test
    @DisplayName("A2: no endpoint overrides the class-level gate (22 endpoints, 12 writes)")
    void noMethodLevelOverride() {
        List<String> violations = new ArrayList<>();
        int endpoints = 0, writes = 0;
        for (Class<?> c : CONTROLLERS) {
            for (Method m : c.getDeclaredMethods()) {
                boolean isWrite = m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(PutMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class);
                boolean isEndpoint = isWrite || m.isAnnotationPresent(GetMapping.class);
                if (!isEndpoint) continue;
                endpoints++;
                if (isWrite) writes++;
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa != null) violations.add(c.getSimpleName() + "#" + m.getName() + " -> " + pa.value());
            }
        }
        assertEquals(22, endpoints, "expected 22 endpoints (5 per resource x 4 resources + my-role + payroll summary)");
        assertEquals(12, writes, "expected 12 write endpoints (3 per resource x 4 resources)");
        assertTrue(violations.isEmpty(), "method-level @PreAuthorize overrides: " + violations);
    }

    // ─── Part B: HTTP matrix ─────────────────────────────────────────────

    enum Res {
        PEOPLE("/api/polaris/payroll/people", Set.of("admin", "editor"), Set.of("admin"),
            "{\"office\":\"ATHENS\",\"name\":\"New Person\",\"effectiveFromYear\":2027}",
            "{\"salary\":1}"),
        MILESTONES("/api/polaris/payroll/milestones", Set.of("admin"), Set.of("admin"),
            "{\"targetMonth\":\"2027-05-01\",\"membersTarget\":10}",
            "{\"membersTarget\":11}"),
        ASSUMPTIONS("/api/polaris/forecast/assumptions", Set.of("admin"), Set.of("admin"),
            "{\"key\":\"new_key\",\"value\":1}",
            "{\"value\":2}"),
        ACTUALS("/api/polaris/forecast/actuals", Set.of("admin", "editor", "viewer"), Set.of("admin", "editor"),
            "{\"groupKey\":\"g\",\"label\":\"l\",\"periodStart\":\"2026-09-01\",\"periodEnd\":\"2026-09-30\"}",
            "{\"label\":\"l2\"}");

        final String path;
        final Set<String> read;
        final Set<String> write;
        final String createBody;
        final String updateBody;

        Res(String path, Set<String> read, Set<String> write, String createBody, String updateBody) {
            this.path = path;
            this.read = read;
            this.write = write;
            this.createBody = createBody;
            this.updateBody = updateBody;
        }
    }

    enum Op {
        LIST, GET, POST, PUT, DELETE;
        boolean isWrite() { return this == POST || this == PUT || this == DELETE; }
    }

    /** Polaris role holders are global 'user'; global-viewer is global 'viewer' with a Polaris admin row. */
    static final List<String> ACTORS = List.of("admin", "editor", "viewer", "none", "global-viewer");

    static int expectedStatus(Res res, Op op, String actor) {
        Set<String> allowed = op.isWrite() ? res.write : res.read;
        return allowed.contains(actor) ? 200 : 403;
    }

    static Stream<Arguments> matrix() {
        List<Arguments> rows = new ArrayList<>();
        for (Res res : Res.values())
            for (Op op : Op.values())
                for (String actor : ACTORS)
                    rows.add(Arguments.of(res, op, actor, expectedStatus(res, op, actor)));
        return rows.stream();
    }

    @Autowired private TestDataBuilder tdb;
    @Autowired private PolarisTestSupport pts;
    @Autowired private PayrollPersonRepository payrollPersonRepository;
    @Autowired private PayrollMilestoneRepository payrollMilestoneRepository;
    @Autowired private ForecastAssumptionRepository forecastAssumptionRepository;
    @Autowired private ActualsRowRepository actualsRowRepository;

    private CompanyEntity polaris;

    @BeforeEach
    void setup() {
        polaris = pts.createPolaris();
    }

    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @MethodSource("matrix")
    @DisplayName("B: per-endpoint entity_roles matrix")
    void accessMatrix(Res res, Op op, String actor, int expected) throws Exception {
        UUID seedId = seed(res);
        User user = actor(actor);

        MockHttpServletRequestBuilder req = switch (op) {
            case LIST   -> get(res.path);
            case GET    -> get(res.path + "/" + seedId);
            case POST   -> post(res.path).content(res.createBody);
            case PUT    -> put(res.path + "/" + seedId).content(res.updateBody);
            case DELETE -> delete(res.path + "/" + seedId);
        };
        mockMvc.perform(req
                .param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(user))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is(expected));
    }

    // ─── Part C: GET /api/polaris/my-role ────────────────────────────────

    static Stream<Arguments> myRoleMatrix() {
        return Stream.of(
            Arguments.of("admin", 200, "admin"),
            Arguments.of("editor", 200, "editor"),
            Arguments.of("viewer", 200, "viewer"),
            Arguments.of("none", 403, null),
            Arguments.of("global-viewer", 403, null));
    }

    @ParameterizedTest(name = "my-role as {0} -> {1}")
    @MethodSource("myRoleMatrix")
    @DisplayName("C: my-role returns the entity_roles role; no row or global VIEWER -> 403")
    void myRoleMatrix(String actor, int expected, String role) throws Exception {
        User user = actor(actor);
        var res = mockMvc.perform(get("/api/polaris/my-role")
                .param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().is(expected));
        if (role != null) {
            res.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.role").value(role));
        }
    }

    @Test
    @DisplayName("C: my-role on a non-Polaris entity -> 400")
    void myRole_nonPolaris_returns400() throws Exception {
        CompanyEntity group = tdb.createEntity("next2megroup", "Next2Me Group");
        User user = actor("admin");
        mockMvc.perform(get("/api/polaris/my-role")
                .param("entityId", group.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().isBadRequest());
    }

    // ─── Part D: GET /api/polaris/payroll/summary ────────────────────────

    static Stream<Arguments> summaryMatrix() {
        return Stream.of(
            Arguments.of("admin", 200), Arguments.of("editor", 200), Arguments.of("viewer", 200),
            Arguments.of("none", 403), Arguments.of("global-viewer", 403));
    }

    @ParameterizedTest(name = "payroll summary as {0} -> {1}")
    @MethodSource("summaryMatrix")
    @DisplayName("D: payroll summary for any Polaris role; no row or global VIEWER -> 403")
    void summaryMatrix(String actor, int expected) throws Exception {
        seed(Res.PEOPLE);
        User user = actor(actor);
        mockMvc.perform(get("/api/polaris/payroll/summary")
                .param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().is(expected));
    }

    @Test
    @DisplayName("D: payroll summary returns totals per office only -- no names, ids or per-person rows")
    void summary_aggregatesOnly() throws Exception {
        UUID e = polaris.getId();
        for (Object[] r : new Object[][] {
                { "ATHENS", "Alice Secret", "1000.00", false }, { "ATHENS", "Bob Secret", "2500.50", false },
                { "ATHENS", "Athens Office", "300.00", true }, { "MANILA", "Carol Secret", "40000.00", false } }) {
            PayrollPerson p = new PayrollPerson();
            p.setEntityId(e); p.setOffice((String) r[0]); p.setName((String) r[1]);
            p.setSalary(new BigDecimal((String) r[2])); p.setIsOfficeCost((Boolean) r[3]); p.setEffectiveFromYear(2027);
            payrollPersonRepository.save(p);
        }
        User viewer = actor("viewer");
        String body = mockMvc.perform(get("/api/polaris/payroll/summary")
                .param("entityId", e.toString())
                .header("Authorization", tdb.bearerToken(viewer)))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(2))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].office").value("ATHENS"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].salaryTotal").value(3800.50))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].headcount").value(2))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[1].office").value("MANILA"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[1].headcount").value(1))
            .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Secret"), "summary must not contain names: " + body);
        assertFalse(body.contains("\"id\""), "summary must not contain row ids: " + body);
        assertFalse(body.contains("1000") || body.contains("2500.5"), "summary must not contain per-person amounts: " + body);
    }

    private User actor(String actor) {
        if ("global-viewer".equals(actor)) {
            User u = tdb.createViewer("global-viewer");
            pts.assignWithRole(u, polaris, "admin");
            return u;
        }
        User u = tdb.createUser("polaris-" + actor);
        if ("none".equals(actor)) {
            tdb.assignEntities(u, polaris);
        } else {
            pts.assignWithRole(u, polaris, actor);
        }
        return u;
    }

    private UUID seed(Res res) {
        UUID e = polaris.getId();
        return switch (res) {
            case PEOPLE -> {
                PayrollPerson p = new PayrollPerson();
                p.setEntityId(e);
                p.setOffice("MANILA");
                p.setName("Seed Person");
                p.setSalary(BigDecimal.TEN);
                p.setEffectiveFromYear(2027);
                yield payrollPersonRepository.save(p).getId();
            }
            case MILESTONES -> {
                PayrollMilestone m = new PayrollMilestone();
                m.setEntityId(e);
                m.setTargetMonth(LocalDate.of(2027, 1, 1));
                m.setMembersTarget(5);
                yield payrollMilestoneRepository.save(m).getId();
            }
            case ASSUMPTIONS -> {
                ForecastAssumption a = new ForecastAssumption();
                a.setEntityId(e);
                a.setKey("seed_key");
                a.setValue(BigDecimal.ONE);
                yield forecastAssumptionRepository.save(a).getId();
            }
            case ACTUALS -> {
                ActualsRow r = new ActualsRow();
                r.setEntityId(e);
                r.setGroupKey("seed");
                r.setLabel("Seed Row");
                r.setPeriodStart(LocalDate.of(2026, 8, 1));
                r.setPeriodEnd(LocalDate.of(2026, 8, 31));
                yield actualsRowRepository.save(r).getId();
            }
        };
    }
}
