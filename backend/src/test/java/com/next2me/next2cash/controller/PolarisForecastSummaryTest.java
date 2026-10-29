package com.next2me.next2cash.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.ForecastAssumption;
import com.next2me.next2cash.model.PayrollMilestone;
import com.next2me.next2cash.model.PayrollPerson;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ForecastAssumptionRepository;
import com.next2me.next2cash.repository.PayrollMilestoneRepository;
import com.next2me.next2cash.repository.PayrollPersonRepository;
import com.next2me.next2cash.service.PolarisForecastSummaryService;
import com.next2me.next2cash.service.PolarisForecastSummaryService.ForecastSummary;
import com.next2me.next2cash.support.PolarisTestSupport;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/polaris/forecast/summary (Phase 4, Oct 2026).
 *
 *  1. Access: the Simos-style global VIEWER with a Polaris viewer row gets 200;
 *     the body contains no 'name' / 'salary' / raw assumption fields anywhere.
 *  2. Parity: the numbers match the frontend math for the polarisMath.test.mjs
 *     fixture (14 payroll rows, PHP 56.5): payroll 2026 = 395,800 and
 *     2027 = 406,808, so EBITDA = contribution − payroll per year.
 *  3. Validation: years outside 1/3/5 -> 400; milestones mode interpolates.
 */
class PolarisForecastSummaryTest extends BaseIntegrationTest {

    private static final String SUMMARY = "/api/polaris/forecast/summary";
    private static final double EPS = 1e-6;

    @Autowired private TestDataBuilder tdb;
    @Autowired private PolarisTestSupport pts;
    @Autowired private PayrollPersonRepository payrollPersonRepository;
    @Autowired private PayrollMilestoneRepository payrollMilestoneRepository;
    @Autowired private ForecastAssumptionRepository forecastAssumptionRepository;
    @Autowired private PolarisForecastSummaryService summaryService;

    private CompanyEntity polaris;
    private User simos;   // global viewer + entity_roles viewer

    @BeforeEach
    void setup() {
        polaris = pts.createPolaris();
        simos = tdb.createViewer("simos");
        pts.assignWithRole(simos, polaris, "viewer");
        seedFixture();
    }

    // ─── fixture = polarisMath.test.mjs PEOPLE + simple assumptions ─────────

    private void person(String office, String name, String salary, String raise, int effFrom, boolean officeCost) {
        PayrollPerson p = new PayrollPerson();
        p.setEntityId(polaris.getId());
        p.setOffice(office); p.setName(name); p.setRoleTitle("Secret Title");
        p.setSalary(new BigDecimal(salary)); p.setRaisePct(new BigDecimal(raise));
        p.setEffectiveFromYear(effFrom); p.setIsOfficeCost(officeCost);
        payrollPersonRepository.save(p);
    }

    private void assumption(String key, String value) {
        ForecastAssumption a = new ForecastAssumption();
        a.setEntityId(polaris.getId()); a.setKey(key); a.setValue(new BigDecimal(value));
        forecastAssumptionRepository.save(a);
    }

    private void seedFixture() {
        // Athens (USD, no FX)
        person("ATHENS", "Γραφείο Αθηνών", "10000", "0", 2027, true);
        person("ATHENS", "General Manager Secret", "62400", "0", 2027, false);
        person("ATHENS", "Business Director Secret", "62400", "0", 2027, false);
        person("ATHENS", "Accounting Manager Secret", "46800", "0", 2027, false);
        person("ATHENS", "Marketing Manager Secret", "23400", "0", 2027, false);
        person("ATHENS", "IT Developer Secret", "46800", "0", 2027, false);
        person("ATHENS", "Legal Compliance Secret", "20000", "0", 2027, false);
        // Manila (PHP ÷ 56.5)
        person("MANILA", "Γραφείο Μανίλα", "1130000", "0", 2027, true);
        person("MANILA", "Pia Secret", "1130000", "5", 2027, false);
        person("MANILA", "Carmen Secret", "2034000", "0", 2027, false);
        person("MANILA", "Margilyn Secret", "678000", "41.7", 2027, false);
        person("MANILA", "Victoria Secret", "678000", "41.7", 2027, false);
        person("MANILA", "Stella Secret", "678000", "0", 2027, false);
        person("MANILA", "Jinky Secret", "678000", "0", 2027, false);

        // assumptions: the production per-member rates of the Polaris xlsx model
        // (16,800 members today, +10%/yr) -- revenue/member $47.00, third-party/member
        // $22.568, contribution/member $24.432, so Y1 contribution = $410,457.60 and
        // Y1 EBITDA = 410,457.60 − 395,800 = $14,657.60 (the figure verified against the xlsx).
        assumption("members", "16800");
        assumption("member_growth_pct", "10");
        assumption("network_fee", "20");          // $/member/yr                     20.00
        assumption("monthly_fee", "1.5");         // $/member/month × 12            18.00
        assumption("audit_fee_rate", "0.2");      // × $35 claims                    7.00
        assumption("dental_fee", "2");            // $/member/yr                      2.00  => revenue/member 47.00
        assumption("network_payable", "15");      // × 1.12                          16.80
        assumption("audit_payable_rate", "0.1");  // × 35 × 1.12                      3.92
        assumption("dental_payable", "1.65");     // × 1.12                           1.848 => third-party/member 22.568
        assumption("fx_php_per_usd", "56.5");
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final double MEMBERS_TODAY = 16800.0;
    private static final double REV_PER_MEMBER = 47.0;
    private static final double TP_PER_MEMBER = 22.568;
    private static final double CONTRIB_PER_MEMBER = 24.432;
    private static final double PAYROLL_2026 = 395800.0;
    private static final double PAYROLL_2027 = 406808.0;
    // hand-verified figures (independent of service and formula constants)
    private static final double HAND_Y1_CONTRIBUTION = 410457.60;
    private static final double HAND_Y1_EBITDA = 14657.60;
    private static final double HAND_MILESTONE_Y3_EBITDA = 326152.00;   // 30,000 × 24.432 − 406,808

    // ─── 1. access + leak check (HTTP, as Simos) ─────────────────────────────

    @Test
    @DisplayName("Simos-style global VIEWER: GET summary 200, body has no name/salary/assumption fields")
    void viewer_getsAggregatesOnly() throws Exception {
        String body = mockMvc.perform(get(SUMMARY)
                .param("entityId", polaris.getId().toString())
                .param("years", "3")
                .header("Authorization", tdb.bearerToken(simos)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.years").value(3))
            .andExpect(jsonPath("$.data.series.length()").value(3))
            .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        List<String> fieldNames = new ArrayList<>();
        collectFieldNames(root, fieldNames);
        for (String forbidden : List.of("name", "salary", "salaryTotal", "raisePct", "roleTitle", "office", "key", "value",
                                        "network_fee", "monthly_fee", "audit_fee_rate", "dental_fee",
                                        "network_payable", "audit_payable_rate", "dental_payable", "fx_php_per_usd", "member_growth_pct",
                                        "members_mode")) {
            assertFalse(fieldNames.contains(forbidden), "response leaks field '" + forbidden + "': " + fieldNames);
        }
        assertFalse(body.contains("Secret"), "response must not contain person names: " + body);
        assertFalse(body.contains("62400") || body.contains("2034000"), "response must not contain per-person salaries: " + body);
        assertFalse(root.path("data").has("revenue") || root.path("data").has("payroll"),
            "totals that are not on the hero/KPI block are not echoed");
    }

    @Test
    @DisplayName("years must be 1, 3 or 5 -> otherwise 400; membersMode must be growth|milestones")
    void validation() throws Exception {
        for (String bad : List.of("2", "0", "10", "-1")) {
            mockMvc.perform(get(SUMMARY).param("entityId", polaris.getId().toString()).param("years", bad)
                    .header("Authorization", tdb.bearerToken(simos)))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(SUMMARY).param("entityId", polaris.getId().toString()).param("years", "1").param("membersMode", "weird")
                .header("Authorization", tdb.bearerToken(simos)))
            .andExpect(status().isBadRequest());
        // default years = 1
        mockMvc.perform(get(SUMMARY).param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(simos)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.years").value(1))
            .andExpect(jsonPath("$.data.membersMode").value("growth"));
    }

    // ─── 2. parity with the frontend math (service, fixed "today") ───────────

    @Test
    @DisplayName("parity (growth): Y1 = 16,800 members, contribution $410,457.60, EBITDA $14,657.60 (xlsx); Y2/Y3 compound 10%, payroll flat at 406,808")
    void parity_growthMode() {
        ForecastSummary s1 = summaryService.summary(polaris.getId(), 1, null, TODAY);
        assertEquals(2026, s1.baseYear());
        assertEquals(MEMBERS_TODAY, s1.members().current(), EPS);
        assertEquals(MEMBERS_TODAY, s1.members().horizon(), EPS);
        // per-member rates are independent of member count
        assertEquals(REV_PER_MEMBER, s1.revenuePerMember(), EPS);
        assertEquals(TP_PER_MEMBER, s1.thirdPartyPerMember(), EPS);
        assertEquals(CONTRIB_PER_MEMBER, s1.revenuePerMember() - s1.thirdPartyPerMember(), EPS);
        // hand check: 16,800 × 24.432 = 410,457.60; − 395,800 = 14,657.60
        assertEquals(HAND_Y1_CONTRIBUTION, MEMBERS_TODAY * CONTRIB_PER_MEMBER, EPS);
        assertEquals(HAND_Y1_EBITDA, s1.ebitda(), EPS);
        assertEquals(CONTRIB_PER_MEMBER / REV_PER_MEMBER * 100, s1.contributionMarginPct(), EPS);
        assertEquals(PAYROLL_2026 / (MEMBERS_TODAY * REV_PER_MEMBER) * 100, s1.payrollToRevenuePct(), EPS);
        assertEquals(HAND_Y1_EBITDA / (MEMBERS_TODAY * REV_PER_MEMBER) * 100, s1.ebitdaMarginPct(), EPS);
        assertEquals(HAND_Y1_EBITDA / MEMBERS_TODAY, s1.ebitdaPerMember(), EPS);
        // break-even at year-1 cost base: payroll / contribution-per-member = 395,800 / 24.432 = 16,200.07
        assertEquals(PAYROLL_2026 / CONTRIB_PER_MEMBER, s1.breakEvenMembers(), EPS);
        assertEquals(MEMBERS_TODAY - PAYROLL_2026 / CONTRIB_PER_MEMBER, s1.breakEvenDistance(), EPS);
        assertEquals(1, s1.series().size());
        assertEquals(s1.ebitda(), s1.series().get(0).ebitda(), EPS);

        ForecastSummary s3 = summaryService.summary(polaris.getId(), 3, "growth", TODAY);
        assertEquals(2028, s3.horizonCalendarYear());
        assertEquals(3, s3.series().size());
        // year 2: members 18,480, payroll 406,808 (raises kicked in at 2027) -> 18,480 × 24.432 − 406,808 = 44,695.36
        var y2 = s3.series().get(1);
        assertEquals(2027, y2.calendarYear());
        assertEquals(18480.0, y2.members(), EPS);
        assertEquals(44695.36, y2.ebitda(), 1e-6);
        // year 3: members 20,328, payroll flat at 406,808 (one-time step, not compounding) -> 89,845.70
        var y3 = s3.series().get(2);
        assertEquals(20328.0, y3.members(), EPS);
        assertEquals(89845.696, y3.ebitda(), 1e-6);
        assertEquals(y3.ebitda(), s3.ebitda(), EPS);
        assertEquals(20328.0, s3.members().horizon(), EPS);
        assertEquals("Έτος 3", y3.label());
    }

    @Test
    @DisplayName("milestones mode: piecewise-linear from today's members to the targets, flat after the last")
    void parity_milestonesMode() {
        milestone(LocalDate.of(2027, 10, 1), 2000);   // +12 months -> year 2 lands exactly on it
        milestone(LocalDate.of(2029, 10, 1), 3000);   // +36 months -> year 4 lands on it; year 5 flat
        ForecastSummary s = summaryService.summary(polaris.getId(), 5, "milestones", TODAY);
        assertEquals("milestones", s.membersMode());
        List<Double> members = s.series().stream().map(p -> p.members()).toList();
        assertEquals(MEMBERS_TODAY, members.get(0), EPS);   // y1: today (16,800)
        assertEquals(2000.0, members.get(1), EPS);   // y2: first milestone
        assertEquals(2500.0, members.get(2), EPS);   // y3: halfway between 2000 and 3000
        assertEquals(3000.0, members.get(3), EPS);   // y4: second milestone
        assertEquals(3000.0, members.get(4), EPS);   // y5: flat after the last milestone
        // growth mode ignores the milestones
        ForecastSummary g = summaryService.summary(polaris.getId(), 5, "growth", TODAY);
        assertEquals(MEMBERS_TODAY * Math.pow(1.1, 4), g.members().horizon(), EPS);
    }

    @Test
    @DisplayName("persisted members_mode=1 (milestones 20k@2027-06, 30k@2028-03): Y3 = 30,000 members, EBITDA $326,152.00 (hand-verified)")
    void persistedMembersMode_drivesTheSummary() throws Exception {
        milestone(LocalDate.of(2027, 6, 1), 20000);   // +8 months from 2026-10-01
        milestone(LocalDate.of(2028, 3, 1), 30000);   // +17 months
        assumption("members_mode", "1");

        // no membersMode argument -> the stored mode applies
        ForecastSummary s = summaryService.summary(polaris.getId(), 3, null, TODAY);
        assertEquals("milestones", s.membersMode());
        var y1 = s.series().get(0); var y2 = s.series().get(1); var y3 = s.series().get(2);
        assertEquals(MEMBERS_TODAY, y1.members(), EPS);                    // y1 = today = 16,800 (the "members" assumption)
        assertEquals(20000 + 10000 * (12 - 8) / 9.0, y2.members(), EPS);  // 12 months: 24,444.44 between the two targets
        assertEquals(30000.0, y3.members(), EPS);                          // 24 months: past the last target -> flat
        assertEquals(30000.0, s.members().horizon(), EPS);

        // per-member rates do not change with member count
        assertEquals(REV_PER_MEMBER, s.revenuePerMember(), EPS);
        assertEquals(TP_PER_MEMBER, s.thirdPartyPerMember(), EPS);

        // hand check, independent of the service: contribution = 30,000 × 24.432 = 732,960.00;
        // payroll 2028 = 406,808 (raises are a flat step after 2027); EBITDA = 326,152.00
        double payroll2028 = PolarisForecastSummaryService.payrollUsdInYear(
            payrollPersonRepository.findByEntityIdOrderByOfficeAscNameAsc(polaris.getId()), 2028, 56.5);
        assertEquals(PAYROLL_2027, payroll2028, EPS);
        assertEquals(732960.00, 30000 * CONTRIB_PER_MEMBER, EPS);
        assertEquals(HAND_MILESTONE_Y3_EBITDA, 732960.00 - payroll2028, EPS);
        assertEquals(HAND_MILESTONE_Y3_EBITDA, y3.ebitda(), EPS);
        assertEquals(HAND_MILESTONE_Y3_EBITDA, s.ebitda(), EPS);
        // Y1 in milestones mode is the growth-mode Y1 (same members, same payroll): 14,657.60
        assertEquals(HAND_Y1_EBITDA, y1.ebitda(), EPS);
        // Y2: 24,444.44 × 24.432 − 406,808 = 190,418.67
        assertEquals(190418.666667, y2.ebitda(), 1e-5);
        assertEquals(30000 - PAYROLL_2027 / CONTRIB_PER_MEMBER, s.breakEvenDistance(), EPS);

        // the HTTP endpoint (as Simos, no membersMode param) returns the same mode and figure
        // (doubles compared with a tolerance: 30,000 × 24.432 carries ~1e-10 of binary rounding)
        String body = mockMvc.perform(get(SUMMARY).param("entityId", polaris.getId().toString()).param("years", "3")
                .header("Authorization", tdb.bearerToken(simos)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.membersMode").value("milestones"))
            .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(30000.0, data.path("members").path("horizon").asDouble(), EPS);
        assertEquals(HAND_MILESTONE_Y3_EBITDA, data.path("ebitda").asDouble(), EPS);
        assertEquals(HAND_MILESTONE_Y3_EBITDA, data.path("series").get(2).path("ebitda").asDouble(), EPS);

        // an explicit argument still overrides the stored mode
        assertEquals("growth", summaryService.summary(polaris.getId(), 3, "growth", TODAY).membersMode());

        // members_mode=0 -> growth again
        forecastAssumptionRepository.findByEntityIdAndKey(polaris.getId(), "members_mode")
            .ifPresent(a -> { a.setValue(BigDecimal.ZERO); forecastAssumptionRepository.save(a); });
        assertEquals("growth", summaryService.summary(polaris.getId(), 3, null, TODAY).membersMode());
    }

    @Test
    @DisplayName("no assumptions and no people -> all zeros, break-even null, no exception")
    void emptyEntity_isSafe() {
        CompanyEntity other = tdb.createEntity("polaris-empty", "Empty");
        ForecastSummary s = summaryService.summary(other.getId(), 5, null, TODAY);
        assertEquals(0.0, s.ebitda(), EPS);
        assertEquals(0.0, s.members().current(), EPS);
        assertNull(s.breakEvenMembers());
        assertNull(s.breakEvenDistance());
        assertEquals(5, s.series().size());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void milestone(LocalDate month, int target) {
        PayrollMilestone m = new PayrollMilestone();
        m.setEntityId(polaris.getId()); m.setTargetMonth(month); m.setMembersTarget(target);
        payrollMilestoneRepository.save(m);
    }

    private static void collectFieldNames(JsonNode node, List<String> out) {
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                out.add(e.getKey());
                collectFieldNames(e.getValue(), out);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, out));
        }
    }
}
