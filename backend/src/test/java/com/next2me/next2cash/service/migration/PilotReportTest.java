package com.next2me.next2cash.service.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.next2me.next2cash.service.migration.PilotReport.csvEscape;
import static com.next2me.next2cash.service.migration.PilotReport.sqlEscape;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PilotReport} CSV escaping, SQL escaping,
 * and file writing.
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
class PilotReportTest {

    // ========================================================================
    // csvEscape
    // ========================================================================

    @Test
    @DisplayName("csvEscape returns null as empty string")
    void csvEscapeNull() {
        assertEquals("", csvEscape(null));
    }

    @Test
    @DisplayName("csvEscape passes through simple values")
    void csvEscapeSimple() {
        assertEquals("hello", csvEscape("hello"));
        assertEquals("123", csvEscape("123"));
        assertEquals("file.pdf", csvEscape("file.pdf"));
    }

    @Test
    @DisplayName("csvEscape wraps values containing commas in quotes")
    void csvEscapeComma() {
        assertEquals("\"hello, world\"", csvEscape("hello, world"));
    }

    @Test
    @DisplayName("csvEscape doubles embedded quotes and wraps")
    void csvEscapeQuote() {
        assertEquals("\"say \"\"hi\"\" back\"", csvEscape("say \"hi\" back"));
    }

    @Test
    @DisplayName("csvEscape handles Greek characters without changes")
    void csvEscapeGreek() {
        assertEquals("Τιμολόγιο", csvEscape("Τιμολόγιο"));
    }

    @Test
    @DisplayName("csvEscape wraps values with newlines")
    void csvEscapeNewline() {
        assertEquals("\"line1\nline2\"", csvEscape("line1\nline2"));
    }

    // ========================================================================
    // sqlEscape
    // ========================================================================

    @Test
    @DisplayName("sqlEscape doubles single quotes")
    void sqlEscapeSingleQuote() {
        assertEquals("O''Brien", sqlEscape("O'Brien"));
    }

    @Test
    @DisplayName("sqlEscape passes through regular text")
    void sqlEscapePlain() {
        assertEquals("hello world", sqlEscape("hello world"));
    }

    // ========================================================================
    // End-to-end: write CSV
    // ========================================================================

    @Test
    @DisplayName("writeCsv produces valid file with header + rows")
    void writeCsvEndToEnd(@TempDir Path tempDir) throws IOException {
        PilotReport report = new PilotReport();
        report.addEntry(new PilotReportEntry(
                123, "driveABC", "file.pdf",
                "uuid/2024/03/456/file.pdf",
                PilotReportEntry.STATUS_SUCCESS, "", 1234));
        report.addEntry(new PilotReportEntry(
                124, "driveXYZ", "Τιμολόγιο, σαββάτου.pdf",
                "uuid/2024/03/457/Timologio.pdf",
                PilotReportEntry.STATUS_SKIPPED_EXISTS, "", 42));

        Path csvFile = report.writeCsv(tempDir);

        assertTrue(Files.exists(csvFile));
        String content = Files.readString(csvFile);

        // Header
        assertTrue(content.startsWith("transaction_id,drive_id,"),
                "Content should start with header");

        // Row 1
        assertTrue(content.contains("123,driveABC,file.pdf,uuid/2024/03/456/file.pdf,SUCCESS,,1234"),
                "Row 1 should be present unquoted: " + content);

        // Row 2: comma in filename triggers quoting
        assertTrue(content.contains("\"Τιμολόγιο, σαββάτου.pdf\""),
                "Greek filename with comma should be quoted: " + content);
    }

    // ========================================================================
    // End-to-end: write rollback SQL
    // ========================================================================

    @Test
    @DisplayName("writeRollbackSql returns null when no updates recorded")
    void writeRollbackSqlEmpty(@TempDir Path tempDir) throws IOException {
        PilotReport report = new PilotReport();
        // No updates recorded
        Path result = report.writeRollbackSql(tempDir);
        assertNull(result, "Should return null when nothing to rollback");
    }

    @Test
    @DisplayName("writeRollbackSql produces UPDATE statements for recorded tx")
    void writeRollbackSqlWithUpdates(@TempDir Path tempDir) throws IOException {
        PilotReport report = new PilotReport();

        // Record a pre-update snapshot AND post-update for tx #100
        report.recordPreUpdateSnapshot(100, "1ABC,1DEF");
        report.recordPostUpdateValue(100, "uuid/2024/03/50/a.pdf,uuid/2024/03/50/b.pdf");

        // Also record with apostrophe to test sqlEscape
        report.recordPreUpdateSnapshot(200, "O'Brien_invoice");
        report.recordPostUpdateValue(200, "uuid/2024/03/51/file.pdf");

        Path sqlFile = report.writeRollbackSql(tempDir);

        assertNotNull(sqlFile);
        assertTrue(Files.exists(sqlFile));
        String content = Files.readString(sqlFile);

        assertTrue(content.contains("BEGIN;"), "Should start with BEGIN");
        assertTrue(content.contains("UPDATE transactions SET blob_file_ids = '1ABC,1DEF' WHERE id = 100;"),
                "Should contain tx 100 rollback: " + content);
        assertTrue(content.contains("'O''Brien_invoice'"),
                "Should escape apostrophe for tx 200: " + content);
        assertTrue(content.contains("-- COMMIT;"),
                "Should end with commented COMMIT for safety");
    }

    @Test
    @DisplayName("writeRollbackSql skips transactions without post-update record")
    void writeRollbackSqlSkipsNonExecuted(@TempDir Path tempDir) throws IOException {
        PilotReport report = new PilotReport();

        // Recorded snapshot but NOT post-update - means we didn't actually update this tx
        report.recordPreUpdateSnapshot(100, "1ABC");
        // (no recordPostUpdateValue call for 100)

        report.recordPreUpdateSnapshot(200, "1DEF");
        report.recordPostUpdateValue(200, "new-value");

        Path sqlFile = report.writeRollbackSql(tempDir);

        String content = Files.readString(sqlFile);
        assertFalse(content.contains("WHERE id = 100"),
                "Tx 100 should NOT be in rollback (not actually updated)");
        assertTrue(content.contains("WHERE id = 200;"),
                "Tx 200 should be in rollback");
    }

    // ========================================================================
    // Summary + counts
    // ========================================================================

    @Test
    @DisplayName("summary includes totals and per-status breakdown")
    void summaryHasBreakdown() {
        PilotReport report = new PilotReport();
        report.addEntry(new PilotReportEntry(1, "d1", "f.pdf", "p1", PilotReportEntry.STATUS_SUCCESS, "", 100));
        report.addEntry(new PilotReportEntry(2, "d2", "f.pdf", "p2", PilotReportEntry.STATUS_SUCCESS, "", 150));
        report.addEntry(new PilotReportEntry(3, "d3", "", "", PilotReportEntry.STATUS_FAILED_DRIVE_404, "not found", 50));

        String summary = report.summary();

        assertTrue(summary.contains("Total files processed:"));
        assertTrue(summary.contains("SUCCESS:"));
        assertTrue(summary.contains("FAILED Drive 404:"));
        assertEquals(2, report.countByStatus(PilotReportEntry.STATUS_SUCCESS));
        assertEquals(1, report.countByStatus(PilotReportEntry.STATUS_FAILED_DRIVE_404));
    }

    @Test
    @DisplayName("isSuccess returns true for SUCCESS and SKIPPED_EXISTS")
    void isSuccessCoversBoth() {
        PilotReportEntry succ = new PilotReportEntry(
                1, "d", "f.pdf", "p",
                PilotReportEntry.STATUS_SUCCESS, "", 100);
        PilotReportEntry skip = new PilotReportEntry(
                2, "d", "f.pdf", "p",
                PilotReportEntry.STATUS_SKIPPED_EXISTS, "", 0);
        PilotReportEntry fail = new PilotReportEntry(
                3, "d", "", "",
                PilotReportEntry.STATUS_FAILED_DRIVE_404, "", 50);

        assertTrue(succ.isSuccess());
        assertTrue(skip.isSuccess());
        assertFalse(fail.isSuccess());
    }
}