package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.CostMilestone;
import com.next2me.next2cash.model.ForecastAssumption;
import com.next2me.next2cash.model.PayrollPerson;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.CostMilestoneRepository;
import com.next2me.next2cash.repository.ForecastAssumptionRepository;
import com.next2me.next2cash.repository.PayrollPersonRepository;
import com.next2me.next2cash.support.PolarisTestSupport;
import com.next2me.next2cash.support.TestDataBuilder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/polaris/forecast/export (30 Oct 2026).
 *
 *   actor                         | export
 *   ------------------------------+--------
 *   Polaris admin                 | 200 .xlsx
 *   Polaris editor                | 200 .xlsx
 *   Polaris viewer (global user)  | 403
 *   global VIEWER + viewer row    | 403   (class-level Spring gate)
 *   no entity_roles row           | 403
 *
 * Content check: the workbook is parsed back with POI and the EBITDA / OPEX /
 * contributions cells match a hand-computed fixture that does not depend on the
 * wall clock (no raises, flat growth year 1, a target dated in the past).
 *
 *   16,800 × 24.432 = 410,457.60 − payroll 46,800 − contributions 20% × 46,800 = 9,360
 *   − OPEX Consultants 24,000 (target 2020-01-01, in force from month 0) = 330,297.60
 */
class PolarisForecastExportControllerTest extends BaseIntegrationTest {

    private static final String EXPORT = "/api/polaris/forecast/export";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired private TestDataBuilder tdb;
    @Autowired private PolarisTestSupport pts;
    @Autowired private PayrollPersonRepository payrollPersonRepository;
    @Autowired private ForecastAssumptionRepository forecastAssumptionRepository;
    @Autowired private CostMilestoneRepository costMilestoneRepository;

    private CompanyEntity polaris;
    private User admin, editor, viewer, globalViewer, none;

    @BeforeEach
    void setup() {
        polaris = pts.createPolaris();
        admin = tdb.createAdmin("apostolos");   pts.assignWithRole(admin, polaris, "admin");
        editor = tdb.createUser("editor");      pts.assignWithRole(editor, polaris, "editor");
        viewer = tdb.createUser("pviewer");     pts.assignWithRole(viewer, polaris, "viewer");
        globalViewer = tdb.createViewer("simos"); pts.assignWithRole(globalViewer, polaris, "viewer");
        none = tdb.createUser("none");          tdb.assignEntities(none, polaris);

        for (String[] kv : new String[][] { { "members", "16800" }, { "member_growth_pct", "10" },
                { "network_fee", "20" }, { "monthly_fee", "1.5" }, { "audit_fee_rate", "0.2" }, { "dental_fee", "2" },
                { "network_payable", "15" }, { "audit_payable_rate", "0.1" }, { "dental_payable", "1.65" },
                { "employer_contrib_pct", "20" } }) {
            ForecastAssumption a = new ForecastAssumption(); a.setEntityId(polaris.getId()); a.setKey(kv[0]); a.setValue(new BigDecimal(kv[1]));
            forecastAssumptionRepository.save(a);
        }
        PayrollPerson p = new PayrollPerson();
        p.setEntityId(polaris.getId()); p.setOffice("ATHENS"); p.setName("Accounting Manager Secret");
        p.setSalary(new BigDecimal("46800")); p.setRaisePct(BigDecimal.ZERO); p.setEffectiveFromYear(2027); p.setIsOfficeCost(false);
        payrollPersonRepository.save(p);
        CostMilestone c = new CostMilestone(); c.setEntityId(polaris.getId()); c.setCategory("Consultants");
        c.setTargetDate(LocalDate.of(2020, 1, 1)); c.setTargetValue(new BigDecimal("24000"));
        costMilestoneRepository.save(c);
    }

    private MockHttpServletResponse export(User u, String years) throws Exception {
        return mockMvc.perform(get(EXPORT).param("entityId", polaris.getId().toString()).param("years", years)
                .header("Authorization", tdb.bearerToken(u)))
            .andReturn().getResponse();
    }

    /** label (column A) -> numeric value in column B (year 1) for every row that has both. */
    private static Map<String, Double> yearOneByLabel(byte[] xlsx) throws Exception {
        Map<String, Double> out = new LinkedHashMap<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                Cell a = row.getCell(0), b = row.getCell(1);
                if (a == null || b == null || a.getCellType() != CellType.STRING || b.getCellType() != CellType.NUMERIC) continue;
                out.put(a.getStringCellValue(), b.getNumericCellValue());
            }
        }
        return out;
    }

    @Test
    @DisplayName("admin: 200, xlsx content type + attachment; EBITDA Y1 = 330,297.60 (hand), OPEX Consultants 24,000, contributions 9,360")
    void admin_getsWorkbookWithHandVerifiedFigures() throws Exception {
        MockHttpServletResponse res = export(admin, "1");
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentType().startsWith(XLSX), res.getContentType());   // server.servlet.encoding.force adds ;charset=UTF-8
        assertTrue(res.getHeader("Content-Disposition").contains("Polaris_PL_Forecast_1y_"), res.getHeader("Content-Disposition"));
        assertTrue(res.getHeader("Content-Disposition").endsWith(".xlsx\""), res.getHeader("Content-Disposition"));

        byte[] bytes = res.getContentAsByteArray();
        assertTrue(bytes.length > 2000, "workbook is not empty");
        // keep a sample for manual inspection (target/ is gitignored)
        java.nio.file.Files.write(java.nio.file.Path.of("target", "polaris-forecast-sample-1y.xlsx"), bytes);
        Map<String, Double> y1 = yearOneByLabel(bytes);

        // independent hand figure
        assertEquals(330297.60, 16800 * 24.432 - 46800 - 9360 - 24000, 1e-6);

        assertEquals(16800.0, y1.get("Μέλη"), 1e-6);
        assertEquals(16800 * 47.0, y1.get("ΣΥΝΟΛΟ ΕΣΟΔΩΝ"), 1e-6);
        assertEquals(16800 * 22.568, y1.get("ΣΥΝΟΛΟ THIRD-PARTY"), 1e-6);
        assertEquals(410457.60, y1.get("CONTRIBUTION"), 1e-6);
        assertEquals(46800.0, y1.get("Αθήνα (μισθοδοσία + ενοίκιο/λειτουργικά, USD)"), 1e-6);
        assertEquals(0.0, y1.get("Μανίλα (μισθοδοσία + ενοίκιο/λειτουργικά, PHP ÷ FX)"), 1e-6);
        assertEquals(46800.0, y1.get("ΣΥΝΟΛΟ ΓΡΑΦΕΙΩΝ"), 1e-6);
        assertEquals(9360.0, y1.get("Εργοδοτικές εισφορές (20% επί μισθών προσωπικού)"), 1e-6);
        assertEquals(56160.0, y1.get("ΣΥΝΟΛΟ ΛΕΙΤΟΥΡΓΙΚΩΝ ΚΟΣΤΩΝ"), 1e-6);
        assertEquals(24000.0, y1.get("Consultants"), 1e-6);
        for (String zero : new String[] { "Audit", "Legal", "IT / Computer", "Marketing / Διαφήμιση", "Ταξίδια", "PR", "Λοιπά", "Σύνδεσμοι" }) {
            assertEquals(0.0, y1.get(zero), 1e-6, zero);
        }
        assertEquals(24000.0, y1.get("ΣΥΝΟΛΟ OPEX"), 1e-6);
        assertEquals(330297.60, y1.get("EBITDA"), 1e-6);
        assertEquals(330297.60 / (16800 * 47.0), y1.get("Περιθώριο EBITDA"), 1e-9);   // stored as a ratio, formatted 0.0%

        // 3-year horizon -> header has 1 label column + 3 year columns
        MockHttpServletResponse res3 = export(admin, "3");
        assertEquals(200, res3.getStatus());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(res3.getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("P&L Forecast", sheet.getSheetName());
            Row head = sheet.getRow(3);
            assertEquals("Γραμμή", head.getCell(0).getStringCellValue());
            assertEquals(4, head.getLastCellNum());
            assertTrue(head.getCell(1).getStringCellValue().startsWith("Έτος 1 ("));
            assertTrue(head.getCell(3).getStringCellValue().startsWith("Έτος 3 ("));
        }
    }

    @Test
    @DisplayName("editor: 200; Polaris viewer, global VIEWER and no-role: 403; years=2: 400")
    void accessMatrix_andValidation() throws Exception {
        assertEquals(200, export(editor, "1").getStatus());
        assertTrue(export(editor, "5").getContentType().startsWith(XLSX));
        for (User u : new User[] { viewer, globalViewer, none }) {
            MockHttpServletResponse r = export(u, "1");
            assertEquals(403, r.getStatus(), u.getUsername());
            assertFalse(String.valueOf(r.getContentType()).startsWith(XLSX), u.getUsername() + " must not receive a workbook");
        }
        assertEquals(400, export(admin, "2").getStatus());
        assertEquals(400, export(admin, "0").getStatus());
        // default years = 1
        MockHttpServletResponse def = mockMvc.perform(get(EXPORT).param("entityId", polaris.getId().toString())
                .header("Authorization", tdb.bearerToken(admin))).andReturn().getResponse();
        assertEquals(200, def.getStatus());
        assertTrue(def.getHeader("Content-Disposition").contains("_1y_"));
    }
}
