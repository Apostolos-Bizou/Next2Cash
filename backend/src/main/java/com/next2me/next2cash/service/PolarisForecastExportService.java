package com.next2me.next2cash.service;

import com.next2me.next2cash.service.PolarisForecastSummaryService.ForecastDetail;
import com.next2me.next2cash.service.PolarisForecastSummaryService.YearDetail;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import static com.next2me.next2cash.service.PolarisForecastMath.OPEX_CATEGORIES;

/**
 * Polaris P&L -- Excel export of the forecast (30 Oct 2026).
 *
 * One sheet, years as columns, the lines of the reference xlsx model in order:
 * REVENUE (per fee line) -> THIRD-PARTY (per payable line) -> CONTRIBUTION ->
 * OPERATING COSTS by office (+ employer contributions) -> OPEX by category
 * (the 9 PolarisForecastMath.OPEX_CATEGORIES) -> EBITDA. Numbers come from
 * PolarisForecastSummaryService#detail, i.e. exactly what the Forecast page
 * computes for the selected horizon and the admin's persisted members mode.
 *
 * EXPORT only -- there is no import path (the UI forms are the import).
 * Same Apache POI pattern as CardExportService. Access control is NOT done
 * here: PolarisForecastController admits admin/editor only (never a viewer).
 */
@Service
@RequiredArgsConstructor
public class PolarisForecastExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PolarisForecastSummaryService summaryService;

    public byte[] generateExcel(UUID entityId, int years) {
        ForecastDetail d = summaryService.detail(entityId, years);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            buildSheet(wb, d);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Excel generation failed: " + e.getMessage(), e);
        }
    }

    /** Suggested download name, e.g. Polaris_PL_Forecast_3y_20261030.xlsx */
    public static String filename(int years) {
        return "Polaris_PL_Forecast_" + years + "y_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
    }

    // ------------------------------------------------------------------

    private static final class Styles {
        CellStyle title, meta, header, section, label, total, money, moneyBold, pct, integer;
    }

    private void buildSheet(XSSFWorkbook wb, ForecastDetail d) {
        Sheet sheet = wb.createSheet("P&L Forecast");
        Styles st = styles(wb);
        List<YearDetail> rows = d.rows();
        int cols = rows.size() + 1;

        int r = 0;
        Row title = sheet.createRow(r++);
        cell(title, 0, "Polaris P&L — Πρόβλεψη Κερδοφορίας", st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));

        Row meta = sheet.createRow(r++);
        String mode = PolarisForecastSummaryService.MODE_MILESTONES.equals(d.membersMode()) ? "χειροκίνητοι στόχοι" : "ποσοστό %";
        cell(meta, 0, "Ορίζοντας: " + d.years() + (d.years() == 1 ? " έτος" : " έτη")
            + " · Πρόβλεψη μελών: " + mode
            + " · PHP ανά USD: " + d.phpPerUsd()
            + " · Ποσά σε USD/έτος · Εξαγωγή: " + LocalDateTime.now().format(STAMP), st.meta);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, cols - 1));
        r++;

        Row head = sheet.createRow(r++);
        cell(head, 0, "Γραμμή", st.header);
        for (int i = 0; i < rows.size(); i++) {
            YearDetail y = rows.get(i);
            cell(head, i + 1, "Έτος " + y.year() + " (" + y.calendarYear() + ")", st.header);
        }

        // ---- REVENUE ----
        r = section(sheet, r, "REVENUE", cols, st);
        r = line(sheet, r, rows, "Μέλη", YearDetail::members, st.label, st.integer);
        r = line(sheet, r, rows, "Network access fee", YearDetail::revenueNetworkFee, st.label, st.money);
        r = line(sheet, r, rows, "Μηνιαίο fee (×12)", YearDetail::revenueMonthlyFee, st.label, st.money);
        r = line(sheet, r, rows, "Audit fee (% επί $35 claims)", YearDetail::revenueAuditFee, st.label, st.money);
        r = line(sheet, r, rows, "Dental fee", YearDetail::revenueDentalFee, st.label, st.money);
        r = line(sheet, r, rows, "ΣΥΝΟΛΟ ΕΣΟΔΩΝ", YearDetail::revenue, st.total, st.moneyBold);

        // ---- THIRD-PARTY ----
        r = section(sheet, r, "THIRD-PARTY", cols, st);
        r = line(sheet, r, rows, "Network payable (+12% ΦΠΑ)", YearDetail::thirdPartyNetwork, st.label, st.money);
        r = line(sheet, r, rows, "Audit payable (×$35, +12% ΦΠΑ)", YearDetail::thirdPartyAudit, st.label, st.money);
        r = line(sheet, r, rows, "Dental payable (+12% ΦΠΑ)", YearDetail::thirdPartyDental, st.label, st.money);
        r = line(sheet, r, rows, "ΣΥΝΟΛΟ THIRD-PARTY", YearDetail::thirdParty, st.total, st.moneyBold);

        // ---- CONTRIBUTION ----
        r = section(sheet, r, "CONTRIBUTION", cols, st);
        r = line(sheet, r, rows, "CONTRIBUTION", YearDetail::contribution, st.total, st.moneyBold);
        r = line(sheet, r, rows, "Contribution margin", y -> ratio(y.contribution(), y.revenue()), st.label, st.pct);

        // ---- OPERATING COSTS (by office) ----
        r = section(sheet, r, "OPERATING COSTS (ανά γραφείο)", cols, st);
        r = line(sheet, r, rows, "Αθήνα (μισθοδοσία + ενοίκιο/λειτουργικά, USD)", YearDetail::payrollAthens, st.label, st.money);
        r = line(sheet, r, rows, "Μανίλα (μισθοδοσία + ενοίκιο/λειτουργικά, PHP ÷ FX)", YearDetail::payrollManila, st.label, st.money);
        if (rows.stream().anyMatch(y -> y.payrollOther() != 0)) {
            r = line(sheet, r, rows, "Λοιπά γραφεία", YearDetail::payrollOther, st.label, st.money);
        }
        r = line(sheet, r, rows, "ΣΥΝΟΛΟ ΓΡΑΦΕΙΩΝ", YearDetail::payroll, st.total, st.moneyBold);
        double contribPct = rows.isEmpty() ? 0 : rows.get(0).employerContribPct();
        r = line(sheet, r, rows, "Εργοδοτικές εισφορές (" + fmtPct(contribPct) + " επί μισθών προσωπικού)", YearDetail::employerContrib, st.label, st.money);
        r = line(sheet, r, rows, "ΣΥΝΟΛΟ ΛΕΙΤΟΥΡΓΙΚΩΝ ΚΟΣΤΩΝ", y -> y.payroll() + y.employerContrib(), st.total, st.moneyBold);

        // ---- OPEX (by category) ----
        r = section(sheet, r, "OPEX (Λοιπά OPEX Διοίκησης, ανά κατηγορία)", cols, st);
        for (String cat : OPEX_CATEGORIES) {
            r = line(sheet, r, rows, cat, y -> y.opexByCategory().getOrDefault(cat, 0.0), st.label, st.money);
        }
        r = line(sheet, r, rows, "ΣΥΝΟΛΟ OPEX", YearDetail::opex, st.total, st.moneyBold);

        // ---- EBITDA ----
        r = section(sheet, r, "EBITDA", cols, st);
        r = line(sheet, r, rows, "EBITDA", YearDetail::ebitda, st.total, st.moneyBold);
        r = line(sheet, r, rows, "Περιθώριο EBITDA", y -> ratio(y.ebitda(), y.revenue()), st.label, st.pct);
        r = line(sheet, r, rows, "EBITDA ανά μέλος", y -> y.members() != 0 ? y.ebitda() / y.members() : 0, st.label, st.money);

        r++;
        Row note = sheet.createRow(r);
        cell(note, 0, "Έτος 1 = τρέχον ημερολογιακό έτος, κάθε έτος αποτιμάται την 1η Ιανουαρίου. "
            + "OPEX: βηματική συνάρτηση από $0 — ισχύει ο τελευταίος στόχος με ημερομηνία ≤ της ημερομηνίας πρόβλεψης. "
            + "Αθήνα σε USD, Μανίλα σε PHP ÷ FX. Οι αυξήσεις μισθών είναι βήματα (σύνθετα, μία φορά η καθεμία).", st.meta);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, Math.max(cols - 1, 4)));

        sheet.setColumnWidth(0, 52 * 256);
        for (int i = 1; i < cols; i++) sheet.setColumnWidth(i, 20 * 256);
        sheet.createFreezePane(1, 4);
    }

    private static int section(Sheet sheet, int r, String name, int cols, Styles st) {
        Row row = sheet.createRow(r);
        cell(row, 0, name, st.section);
        for (int i = 1; i < cols; i++) cell(row, i, "", st.section);
        return r + 1;
    }

    private static int line(Sheet sheet, int r, List<YearDetail> rows, String label, ToDoubleFunction<YearDetail> f,
                            CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(r);
        cell(row, 0, label, labelStyle);
        for (int i = 0; i < rows.size(); i++) {
            Cell c = row.createCell(i + 1);
            c.setCellValue(f.applyAsDouble(rows.get(i)));
            c.setCellStyle(valueStyle);
        }
        return r + 1;
    }

    private static void cell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        c.setCellStyle(style);
    }

    private static double ratio(double part, double whole) { return whole != 0 ? part / whole : 0; }

    private static String fmtPct(double pct) {
        String s = String.valueOf(pct);
        return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + "%";
    }

    private static Styles styles(XSSFWorkbook wb) {
        CreationHelper ch = wb.getCreationHelper();
        Styles st = new Styles();

        Font titleFont = wb.createFont(); titleFont.setFontName("Calibri"); titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16); titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        Font metaFont = wb.createFont(); metaFont.setFontName("Calibri"); metaFont.setFontHeightInPoints((short) 9);
        metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        Font headerFont = wb.createFont(); headerFont.setFontName("Calibri"); headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 10); headerFont.setColor(IndexedColors.WHITE.getIndex());
        Font sectionFont = wb.createFont(); sectionFont.setFontName("Calibri"); sectionFont.setBold(true);
        sectionFont.setFontHeightInPoints((short) 11); sectionFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        Font plain = wb.createFont(); plain.setFontName("Calibri"); plain.setFontHeightInPoints((short) 10);
        Font bold = wb.createFont(); bold.setFontName("Calibri"); bold.setFontHeightInPoints((short) 10); bold.setBold(true);

        st.title = wb.createCellStyle(); st.title.setFont(titleFont);
        st.meta = wb.createCellStyle(); st.meta.setFont(metaFont);

        st.header = wb.createCellStyle(); st.header.setFont(headerFont);
        st.header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        st.header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        st.header.setAlignment(HorizontalAlignment.CENTER);
        st.header.setBorderBottom(BorderStyle.THIN);

        st.section = wb.createCellStyle(); st.section.setFont(sectionFont);
        st.section.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        st.section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        st.section.setBorderTop(BorderStyle.THIN);

        st.label = wb.createCellStyle(); st.label.setFont(plain);
        st.total = wb.createCellStyle(); st.total.setFont(bold); st.total.setBorderTop(BorderStyle.THIN);

        st.money = wb.createCellStyle(); st.money.setFont(plain);
        st.money.setDataFormat(ch.createDataFormat().getFormat("$#,##0.00;[Red]-$#,##0.00"));
        st.money.setAlignment(HorizontalAlignment.RIGHT);
        st.moneyBold = wb.createCellStyle(); st.moneyBold.cloneStyleFrom(st.money); st.moneyBold.setFont(bold);
        st.moneyBold.setBorderTop(BorderStyle.THIN);

        st.pct = wb.createCellStyle(); st.pct.setFont(plain);
        st.pct.setDataFormat(ch.createDataFormat().getFormat("0.0%"));
        st.pct.setAlignment(HorizontalAlignment.RIGHT);

        st.integer = wb.createCellStyle(); st.integer.setFont(plain);
        st.integer.setDataFormat(ch.createDataFormat().getFormat("#,##0"));
        st.integer.setAlignment(HorizontalAlignment.RIGHT);
        return st;
    }
}
