package com.next2me.next2cash.service.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Core migration service. Phase 1 scope: DRY-RUN ONLY — reads transactions,
 * classifies blob_file_ids, writes CSV report. Zero writes to DB, zero
 * network I/O to Google Drive or Azure Blob.
 *
 * Phase 3 (real migration) will add: Drive download + Azure upload +
 * blob_file_ids column update. That logic lives in a separate method
 * to keep dry-run surgically read-only.
 */
@Service
public class BlobMigrationService {

    private static final Logger log = LoggerFactory.getLogger(BlobMigrationService.class);

    private static final String SCAN_SQL =
        "SELECT id, entity_id, entity_number, blob_file_ids " +
        "FROM transactions " +
        "ORDER BY entity_id, id";

    private static final int PROGRESS_INTERVAL = 500;

    private final JdbcTemplate jdbc;

    public BlobMigrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Execute the dry-run: stream all transactions, classify each, build report.
     * Does NOT write CSV — caller is responsible for that (see MigrationRunner).
     */
    public MigrationReport runDryRun() {
        log.info("=== Starting migration dry-run ===");
        long started = System.currentTimeMillis();

        MigrationReport report = new MigrationReport();

        jdbc.query(SCAN_SQL, rs -> {
            Integer txId = (Integer) rs.getObject("id");
            Object entityIdObj = rs.getObject("entity_id");
            String entityId = entityIdObj == null ? null : entityIdObj.toString();
            Integer entityNumber = (Integer) rs.getObject("entity_number");
            String rawBlobFileIds = rs.getString("blob_file_ids");

            BlobReferenceParser.ClassificationResult result =
                BlobReferenceParser.classify(rawBlobFileIds);

            report.addEntry(txId, entityId, entityNumber, rawBlobFileIds, result);

            int scanned = report.getTotalTransactionsScanned();
            if (scanned % PROGRESS_INTERVAL == 0) {
                log.info("  scanned {} transactions...", scanned);
            }
        });

        long elapsed = System.currentTimeMillis() - started;
        log.info("=== Dry-run complete in {} ms ===", elapsed);
        log.info("\n{}", report.summary());

        return report;
    }

    /**
     * Resolve the output directory for migration reports.
     * Defaults to {project_root}/migration-reports/ but respects an override
     * via the system property 'migration.report.dir' for CI/testing.
     */
    public Path resolveReportDir() {
        String override = System.getProperty("migration.report.dir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        // Fallback: current working directory + /migration-reports
        return Paths.get(System.getProperty("user.dir"), "migration-reports");
    }
}
