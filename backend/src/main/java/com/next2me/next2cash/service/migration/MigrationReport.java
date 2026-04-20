package com.next2me.next2cash.service.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory report collected during a migration dry-run (or real run).
 *
 * Holds per-transaction classification entries plus aggregate counters,
 * and can serialize itself to a CSV file for CEO review.
 *
 * Not thread-safe. Dry-run is single-threaded by design (see bootstrap §Processing mode).
 */
public class MigrationReport {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final LocalDateTime startedAt = LocalDateTime.now();
    private final List<Entry> entries = new ArrayList<>();
    private final Map<BlobReferenceParser.Category, Integer> categoryCounts =
        new EnumMap<>(BlobReferenceParser.Category.class);

    private int totalTransactionsScanned = 0;
    private int totalDriveIds = 0;
    private int totalAzurePaths = 0;
    private int totalInvalidTokens = 0;

    public MigrationReport() {
        for (BlobReferenceParser.Category c : BlobReferenceParser.Category.values()) {
            categoryCounts.put(c, 0);
        }
    }

    /**
     * Record one transaction's classification result.
     */
    public void addEntry(Integer transactionId,
                         String entityId,
                         Integer entityNumber,
                         String rawBlobFileIds,
                         BlobReferenceParser.ClassificationResult result) {
        entries.add(new Entry(
            transactionId,
            entityId,
            entityNumber,
            rawBlobFileIds,
            result.getCategory(),
            result.getDriveIds().size(),
            result.getAzurePaths().size(),
            result.getInvalidTokens().size()
        ));

        totalTransactionsScanned++;
        categoryCounts.merge(result.getCategory(), 1, Integer::sum);
        totalDriveIds += result.getDriveIds().size();
        totalAzurePaths += result.getAzurePaths().size();
        totalInvalidTokens += result.getInvalidTokens().size();
    }

    /**
     * Write the report to a CSV file at the given directory.
     * Filename is auto-generated: migration-dryrun-YYYYMMDD-HHmmss.csv
     *
     * @return the absolute path of the written file
     */
    public Path writeCsv(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        String filename = "migration-dryrun-" + TS_FMT.format(startedAt) + ".csv";
        Path outFile = outputDir.resolve(filename);

        StringBuilder sb = new StringBuilder(entries.size() * 150);
        // Header
        sb.append("transaction_id,entity_id,entity_number,category,drive_count,azure_count,invalid_count,raw_blob_file_ids\n");

        for (Entry e : entries) {
            sb.append(nullSafe(e.transactionId)).append(',');
            sb.append(nullSafe(e.entityId)).append(',');
            sb.append(nullSafe(e.entityNumber)).append(',');
            sb.append(e.category).append(',');
            sb.append(e.driveCount).append(',');
            sb.append(e.azureCount).append(',');
            sb.append(e.invalidCount).append(',');
            sb.append(csvEscape(e.rawBlobFileIds));
            sb.append('\n');
        }

        Files.writeString(outFile, sb.toString(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return outFile.toAbsolutePath();
    }

    /**
     * Human-readable summary for console output at end of dry-run.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Migration Dry-Run Summary ===\n");
        sb.append("Started:                ").append(startedAt).append('\n');
        sb.append("Transactions scanned:   ").append(totalTransactionsScanned).append('\n');
        sb.append("\n-- Classification breakdown --\n");
        for (Map.Entry<BlobReferenceParser.Category, Integer> e : categoryCounts.entrySet()) {
            sb.append(String.format("  %-18s %d%n", e.getKey() + ":", e.getValue()));
        }
        sb.append("\n-- Token totals --\n");
        sb.append("  Drive IDs to migrate: ").append(totalDriveIds).append('\n');
        sb.append("  Azure paths (done):   ").append(totalAzurePaths).append('\n');
        sb.append("  Invalid tokens:       ").append(totalInvalidTokens).append('\n');
        return sb.toString();
    }

    // Getters for testing / future reuse
    public int getTotalTransactionsScanned() { return totalTransactionsScanned; }
    public int getTotalDriveIds() { return totalDriveIds; }
    public int getTotalAzurePaths() { return totalAzurePaths; }
    public int getTotalInvalidTokens() { return totalInvalidTokens; }
    public int getCount(BlobReferenceParser.Category c) { return categoryCounts.getOrDefault(c, 0); }
    public int getEntryCount() { return entries.size(); }

    // ───────────────────────────────────────────────────────────────
    // Helpers

    private static String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }

    /**
     * CSV escape: wrap in double quotes if value contains comma, quote, or newline.
     * Double any embedded quotes per RFC 4180.
     */
    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needsQuoting = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                            || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needsQuoting) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ───────────────────────────────────────────────────────────────
    // Entry DTO

    private static final class Entry {
        final Integer transactionId;
        final String entityId;
        final Integer entityNumber;
        final String rawBlobFileIds;
        final BlobReferenceParser.Category category;
        final int driveCount;
        final int azureCount;
        final int invalidCount;

        Entry(Integer transactionId, String entityId, Integer entityNumber,
              String rawBlobFileIds, BlobReferenceParser.Category category,
              int driveCount, int azureCount, int invalidCount) {
            this.transactionId = transactionId;
            this.entityId = entityId;
            this.entityNumber = entityNumber;
            this.rawBlobFileIds = rawBlobFileIds;
            this.category = category;
            this.driveCount = driveCount;
            this.azureCount = azureCount;
            this.invalidCount = invalidCount;
        }
    }
}
