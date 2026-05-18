package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for GET /api/projects/{id}/detail (S71-D).
 */
class ProjectDetailControllerTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder tdb;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void getProjectDetail_withoutAuth_returns401or403() throws Exception {
        UUID someId = UUID.randomUUID();
        mockMvc.perform(get("/api/projects/" + someId + "/detail"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("Expected 401 or 403 but got " + status);
                }
            });
    }

    @Test
    void getProjectDetail_nonExistentProject_returns404() throws Exception {
        User admin = tdb.createAdmin("apostolos");
        String bearer = tdb.bearerToken(admin);
        UUID nonExistent = UUID.randomUUID();

        mockMvc.perform(get("/api/projects/" + nonExistent + "/detail")
                .header("Authorization", bearer))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getProjectDetail_existingProjectWithNoRevenue_returns200WithNullRoi() throws Exception {
        User admin = tdb.createAdmin("apostolos");
        String bearer = tdb.bearerToken(admin);
        // S77: Project must belong to an entity admin can access
        CompanyEntity testEntity = tdb.createEntity("TESTENT1", "Test Entity 1");

        Project p = new Project();
        p.setName("Test Project S74");
        p.setOwnerEntityId(testEntity.getId());
        p.setStatus("PLANNING");
        p.setTotalBudget(new BigDecimal("50000"));
        p.setExpectedMonthlyRevenue(BigDecimal.ZERO);
        p.setIsActive(true);
        Project saved = projectRepository.save(p);

        mockMvc.perform(get("/api/projects/" + saved.getId() + "/detail")
                .header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.project.id").value(saved.getId().toString()))
            .andExpect(jsonPath("$.data.project.name").value("Test Project S74"))
            .andExpect(jsonPath("$.data.totals.planned").value(50000))
            .andExpect(jsonPath("$.data.totals.spent").value(0))
            .andExpect(jsonPath("$.data.totals.remaining").value(50000))
            .andExpect(jsonPath("$.data.budgetBreakdown").isArray())
            .andExpect(jsonPath("$.data.linkedTransactions.count").value(0))
            .andExpect(jsonPath("$.data.revenueStreams").isArray())
            .andExpect(jsonPath("$.data.roi").doesNotExist());
    }

    @Test
    void getProjectDetail_existingProjectWithRevenue_returnsRoiCalculations() throws Exception {
        User admin = tdb.createAdmin("apostolos");
        String bearer = tdb.bearerToken(admin);
        // S77: Project must belong to an entity admin can access
        CompanyEntity testEntity = tdb.createEntity("TESTENT2", "Test Entity 2");

        Project p = new Project();
        p.setName("Test Project With ROI");
        p.setOwnerEntityId(testEntity.getId());
        p.setStatus("ACTIVE");
        p.setTotalBudget(new BigDecimal("80000"));
        p.setExpectedMonthlyRevenue(new BigDecimal("10000"));
        p.setIsActive(true);
        Project saved = projectRepository.save(p);

        mockMvc.perform(get("/api/projects/" + saved.getId() + "/detail")
                .header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.weightedMonthlyRevenue").value(10000))
            .andExpect(jsonPath("$.data.revenueStreams[0].amount").value(10000))
            .andExpect(jsonPath("$.data.revenueStreams[0].confidencePct").value(100))
            .andExpect(jsonPath("$.data.roi.totalInvestment").value(80000))
            .andExpect(jsonPath("$.data.roi.monthlyRevenueWeighted").value(10000))
            .andExpect(jsonPath("$.data.roi.breakEvenMonthsWeighted").value(8.0));
    }
}
