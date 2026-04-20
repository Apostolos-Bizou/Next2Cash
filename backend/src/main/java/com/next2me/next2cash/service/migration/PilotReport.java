package com.next2me.next2cash.service.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Accumulates {@link PilotReportEntry} rows during the pilot run and
 * produces two output artifacts:
 *
 * <ol>
 *   <li><b>CSV report</b> - one row per Drive file processed, with
 *       status + error + duration. RFC 4180 compliant escaping.</li>
 *   <li><b>Rollback SQL</b> - one UPDATE statement per transaction whose
 *       {@code blob_file_ids} was modified, restoring the pre-migration
 *       value. Never executed automatically - requires manual review + psql.</li>
 * </ol>
 *
 * <p>This class is a pure in-memory builder. It does no I/O except in
 * {@link #writeCsv(Path)} and {@link #writeRollbackSql(Path)}.
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
public class PilotReport {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    /** CSV header row (matches PilotReportEntry fields). */
    private static final String CSV_HEADER =
            "transaction_id,drive_id,file_name,azure_path,status,error_message,duration_ms";

    private final List<PilotReportEntry> entries = new ArrayList<>();

    /**
     * Records of pre-UPDATE blob_file_ids values, keyed by transaction ID.
     * Used to generate the rollback SQL.
     */
    private final Map<Integer, String> preUpdateSnapshots = new HashMap<>();

    /**
     * Transactions that actually had their blob_file_ids column updated.
     * Keyed by transaction ID, value is the new (post-update) value.
     */
    private final Map<Integer, String> postUpdateValues = new HashMap<>();

    public void addEntry(PilotReportEntry entry) {
        entries.add(entry);
    }

    /**
     * Capture the current blob_file_ids value for a transaction before we
     * modify it. Must be called exactly once per transaction that will be
     * updated. The rollback SQL depends on this snapshot.
     */
    public void recordPreUpdateSnapshot(int transactionId, String originalValue) {
        preUpdateSnapshots.put(transactionId, originalValue);
    }

    /**
     * Record the successful post-update value for a transaction. Used in
     * the CSV summary section only.
     */
    public void recordPostUpdateValue(int transactionId, String newValue) {
        postUpdateValues.put(transactionId, newValue);
    }

    public List<PilotReportEntry> getEntries() {
        return List.copyOf(entries);
    }

    public int getTotalEntries() {
        return entries.size();
    }

    public long countByStatus(String status) {
        return entries.stream().filter(e -> status.equals(e.status())).count();
    }

    public long countSuccessful() {
        return entries.stream().filter(PilotReportEntry::isSuccess).count();
    }

    public int getTransactionsUpdated() {
        return postUpdateValues.size();
    }

    /**
     * Human-readable summary for logging.
     */
    public String summary() {
        long success = countByStatus(PilotReportEntry.STATUS_SUCCESS);
        long skipped = countByStatus(PilotReportEntry.STATUS_SKIPPED_EXISTS);
        long fail404 = countByStatus(PilotReportEntry.STATUS_FAILED_DRIVE_404);
        long fail403 = countByStatus(PilotReportEntry.STATUS_FAILED_DRIVE_403);
        long failDriveOther = countByStatus(PilotReportEntry.STATUS_FAILED_DRIVE_OTHER);
        long failAzure = countByStatus(PilotReportEntry.STATUS_FAILED_AZURE);
        long failUnknown = countByStatus(PilotReportEntry.STATUS_FAILED_UNKNOWN);

        StringBuilder sb = new StringBuilder();
        sb.append("Pilot Report Summary\n");
        sb.append("--------------------\n");
        sb.append(String.format("Total files processed:        %d%n", entries.size()));
        sb.append(String.format("  SUCCESS:                    %d%n", success));
        sb.append(String.format("  SKIPPED (already in Azure): %d%n", skipped));
        sb.append(String.format("  FAILED Drive 404:           %d%n", fail404));
        sb.append(String.format("  FAILED Drive 403:           %d%n", fail403));
        sb.append(String.format("  FAILED Drive (other):       %d%n", failDriveOther));
        sb.append(String.format("  FAILED Azure:               %d%n", failAzure));
        sb.append(String.format("  FAILED unknown:             %d%n", failUnknown));
        sb.append(String.format("Transactions with DB update:  %d%n", postUpdateValues.size()));
        if (!entries.isEmpty()) {
            sb.append(String.format("Success rate:                 %.1f%%%n",
                    100.0 * countSuccessful() / entries.size()));
        }
        return sb.toString();
    }

    /**
     * Writes the CSV report to {dir}/pilot-{timestamp}.csv.
     * The {@code dir} is created if it does not exist.
     *
     * @return the Path of the written file
     */
    public Path writeCsv(Path dir) throws IOException {
        Files.createDirectories(dir);
        String filename = "pilot-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".csv";
        Path csvPath = dir.resolve(filename);

        StringBuilder sb = new StringBuilder(4096);
        sb.append(CSV_HEADER).append('\n');
        for (PilotReportEntry e : entries) {
            sb.append(e.transactionId()).append(',');
            sb.append(csvEscape(e.driveId())).append(',');
            sb.append(csvEscape(e.fileName())).append(',');
            sb.append(csvEscape(e.azurePath())).append(',');
            sb.append(csvEscape(e.status())).append(',');
            sb.append(csvEscape(e.errorMessage())).append(',');
            sb.append(e.durationMs()).append('\n');
        }

        Files.writeString(csvPath, sb.toString(), StandardCharsets.UTF_8);
        return csvPath;
    }

    /**
     * Writes the rollback SQL to {dir}/rollback-{timestamp}.sql.
     * The SQL is never executed automatically - it must be reviewed
     * and run manually with psql if a rollback is needed.
     *
     * @return the Path of the written file, or null if there are no
     *         updates to roll back
     */
    public Path writeRollbackSql(Path dir) throws IOException {
        if (postUpdateValues.isEmpty()) {
            return null;
        }
        Files.createDirectories(dir);
        String filename = "rollback-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".sql";
        Path sqlPath = dir.resolve(filename);

        StringBuilder sb = new StringBuilder(4096);
        sb.append("-- Next2Cash Pilot Migration Rollback Script\n");
        sb.append("-- Generated: ").append(LocalDateTime.now()).append('\n');
        sb.append("-- Total transactions to revert: ").append(postUpdateValues.size()).append('\n');
        sb.append("--\n");
        sb.append("-- REVIEW MANUALLY BEFORE RUNNING.\n");
        sb.append("-- To execute:\n");
        sb.append("--   psql \"$NEXT2CASH_DB_URL\" -f ").append(filename).append('\n');
        sb.append("--\n\n");
        sb.append("BEGIN;\n\n");

        for (Map.Entry<Integer, String> e : preUpdateSnapshots.entrySet()) {
            Integer txId = e.getKey();
            String original = e.getValue();

            if (!postUpdateValues.containsKey(txId)) {
                continue; // skip transactions we didn't actually update
            }

            sb.append("-- tx #").append(txId).append('\n');
            sb.append("UPDATE transactions SET blob_file_ids = ");
            if (original == null) {
                sb.append("NULL");
            } else {
                sb.append("'").append(sqlEscape(original)).append("'");
            }
            sb.append(" WHERE id = ").append(txId).append(";\n\n");
        }

        sb.append("-- After review:\n");
        sb.append("-- COMMIT;   -- or ROLLBACK; to discard\n");

        Files.writeString(sqlPath, sb.toString(), StandardCharsets.UTF_8);
        return sqlPath;
    }

    /**
     * RFC 4180 CSV field escaping: wrap in quotes if the value contains
     * comma, quote, CR, or LF; double any embedded quote.
     * Null becomes the empty string.
     */
    static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * PostgreSQL single-quoted string escaping (double any embedded apostrophe).
     */
    static String sqlEscape(String value) {
        return value.replace("'", "''");
    }
}
