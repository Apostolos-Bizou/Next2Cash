package com.next2me.next2cash.service.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core migration service.
 *
 * <p>Supports two distinct modes:
 * <ul>
 *   <li>{@link #runDryRun()} - Phase 1 (Session #30): read-only scan of all
 *       transactions, classify blob_file_ids, write a CSV report. No I/O
 *       against Drive or Azure. Safe to re-run anytime.</li>
 *   <li>{@link #runPilot(boolean)} - Phase 2 (Session #31): select 100 most
 *       recent DRIVE_ID_ONLY transactions, download each file from Drive,
 *       upload to Azure Blob Storage, and update blob_file_ids atomically
 *       per transaction. A rollback SQL script is always written before
 *       any DB modification.</li>
 * </ul>
 */
@Service
public class BlobMigrationService {

    private static final Logger log = LoggerFactory.getLogger(BlobMigrationService.class);

    private static final String SCAN_SQL =
        "SELECT id, entity_id, entity_number, blob_file_ids " +
        "FROM transactions " +
        "ORDER BY entity_id, id";

    /**
     * Pilot selection query: most recent DRIVE_ID_ONLY transactions first.
     * Classification is re-done in Java to avoid duplicating the regex logic
     * in SQL; the WHERE clause just filters for non-empty blob_file_ids.
     */
    private static final String PILOT_SELECT_SQL =
        "SELECT id, entity_id, entity_number, doc_date, blob_file_ids " +
        "FROM transactions " +
        "WHERE blob_file_ids IS NOT NULL AND blob_file_ids <> '' " +
        "ORDER BY doc_date DESC, id DESC";

    private static final int PROGRESS_INTERVAL = 500;
    private static final int PILOT_SIZE = 100;
    private static final long DRIVE_RATE_LIMIT_DELAY_MS = 200L;

    private final JdbcTemplate jdbc;

    // Pilot-mode dependencies (lazy so tests that only exercise dry-run
    // don't need to wire a full migration profile with GCP/Azure clients).
    private final DriveDownloadService driveService;
    private final AzureBlobUploadService azureService;

    @Autowired
    public BlobMigrationService(
            JdbcTemplate jdbc,
            @Lazy(true) DriveDownloadService driveService,
            @Lazy(true) AzureBlobUploadService azureService) {
        this.jdbc = jdbc;
        this.driveService = driveService;
        this.azureService = azureService;
    }

    // ========================================================================
    // Phase 1 (Session #30) - dry-run - UNCHANGED
    // ========================================================================

    /**
     * Execute the dry-run: stream all transactions, classify each, build report.
     * Does NOT write CSV - caller is responsible for that (see MigrationRunner).
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

    // ========================================================================
    // Phase 2 (Session #31) - pilot
    // ========================================================================

    /**
     * Execute the pilot migration: select 100 most-recent DRIVE_ID_ONLY
     * transactions and migrate each Drive file to Azure Blob Storage.
     *
     * @param execute {@code true} to perform real downloads, uploads, and DB
     *                updates; {@code false} to simulate (select + classify only,
     *                no network I/O or DB writes)
     * @return a {@link PilotReport} with per-file results and rollback info
     */
    public PilotReport runPilot(boolean execute) {
        log.info("=== Starting pilot migration ({}) ===", execute ? "EXECUTE" : "DRY-RUN");
        long started = System.currentTimeMillis();

        PilotReport report = new PilotReport();

        // Step 1: select candidate transactions (more than PILOT_SIZE to allow
        // skipping of non-DRIVE_ID_ONLY rows that slip through the WHERE clause)
        List<PilotCandidate> candidates = selectPilotCandidates(PILOT_SIZE * 2);
        log.info("Selected {} candidate transactions (pre-classification)", candidates.size());

        // Step 2: filter to actually-DRIVE_ID_ONLY, cap at PILOT_SIZE
        List<PilotCandidate> pilotSet = filterDriveIdOnly(candidates, PILOT_SIZE);
        log.info("Pilot target: {} transactions with DRIVE_ID_ONLY classification", pilotSet.size());

        if (pilotSet.isEmpty()) {
            log.warn("No DRIVE_ID_ONLY transactions found. Nothing to migrate.");
            return report;
        }

        // Step 3: process each transaction
        int txCounter = 0;
        for (PilotCandidate candidate : pilotSet) {
            txCounter++;
            log.info("[{}/{}] Processing tx #{} (entity={}, doc_date={}, {} Drive IDs)",
                    txCounter, pilotSet.size(), candidate.txId, candidate.entityNumber,
                    candidate.docDate, candidate.driveIds.size());

            if (execute) {
                processTransaction(candidate, report);
            } else {
                // Dry-run: just record that we would process these files
                for (String driveId : candidate.driveIds) {
                    report.addEntry(new PilotReportEntry(
                            candidate.txId, driveId, "(dry-run, not downloaded)",
                            "(dry-run, not uploaded)", "DRY_RUN", "", 0));
                }
            }
        }

        long elapsed = System.currentTimeMillis() - started;
        log.info("=== Pilot complete in {} ms ===", elapsed);
        log.info("\n{}", report.summary());

        return report;
    }

    /**
     * Processes one transaction end-to-end: download each Drive file, upload
     * to Azure, then update blob_file_ids in the DB (atomic per-transaction).
     */
    private void processTransaction(PilotCandidate candidate, PilotReport report) {
        List<String> newBlobPaths = new ArrayList<>(candidate.driveIds.size());
        boolean allSucceeded = true;

        for (String driveId : candidate.driveIds) {
            long fileStart = System.currentTimeMillis();
            try {
                DriveFileContent content = driveService.download(driveId);

                AzureBlobUploadResult result = azureService.upload(
                        candidate.entityId,
                        candidate.docDate.getYear(),
                        candidate.docDate.getMonthValue(),
                        candidate.entityNumber,
                        content.fileName(),
                        content.bytes(),
                        content.mimeType());

                newBlobPaths.add(result.blobPath());

                String status = result.skipped()
                        ? PilotReportEntry.STATUS_SKIPPED_EXISTS
                        : PilotReportEntry.STATUS_SUCCESS;

                report.addEntry(new PilotReportEntry(
                        candidate.txId, driveId, content.fileName(), result.blobPath(),
                        status, "", System.currentTimeMillis() - fileStart));

                // Rate limit between Drive calls
                sleepQuietly(DRIVE_RATE_LIMIT_DELAY_MS);

            } catch (DriveFileNotFoundException ex) {
                allSucceeded = false;
                report.addEntry(new PilotReportEntry(
                        candidate.txId, driveId, "", "",
                        PilotReportEntry.STATUS_FAILED_DRIVE_404, ex.getMessage(),
                        System.currentTimeMillis() - fileStart));
                log.warn("  Drive 404 for {}: {}", driveId, ex.getMessage());

            } catch (DrivePermissionDeniedException ex) {
                allSucceeded = false;
                report.addEntry(new PilotReportEntry(
                        candidate.txId, driveId, "", "",
                        PilotReportEntry.STATUS_FAILED_DRIVE_403, ex.getMessage(),
                        System.currentTimeMillis() - fileStart));
                log.warn("  Drive 403 for {}: {}", driveId, ex.getMessage());

            } catch (DriveDownloadException ex) {
                allSucceeded = false;
                report.addEntry(new PilotReportEntry(
                        candidate.txId, driveId, "", "",
                        PilotReportEntry.STATUS_FAILED_DRIVE_OTHER, ex.getMessage(),
                        System.currentTimeMillis() - fileStart));
                log.warn("  Drive failure for {}: {}", driveId, ex.getMessage());

            } catch (AzureUploadException ex) {
                allSucceeded = false;
                report.addEntry(new PilotReportEntry(
                        candidate.txId, driveId, "", "",
                        PilotReportEntry.STATUS_FAILED_AZURE, ex.getMessage(),
                        System.currentTimeMillis() - fileStart));
                log.warn("  Azure upload failure for {}: {}", driveId, ex.getMessage());

            } catch (RuntimeException ex) {
                allSucceeded = false;
                report.addEntry(new PilotReportEntry(
                        candidate.txId, driveId, "", "",
                        PilotReportEntry.STATUS_FAILED_UNKNOWN,
                        ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                        System.currentTimeMillis() - fileStart));
                log.error("  Unexpected failure for {}: ", driveId, ex);
            }
        }

        // Only update DB if ALL files for this transaction succeeded (all-or-nothing)
        if (allSucceeded && !newBlobPaths.isEmpty()) {
            String newBlobFileIds = String.join(",", newBlobPaths);
            report.recordPreUpdateSnapshot(candidate.txId, candidate.originalBlobFileIds);
            try {
                updateBlobFileIds(candidate.txId, newBlobFileIds);
                report.recordPostUpdateValue(candidate.txId, newBlobFileIds);
                log.info("  Updated blob_file_ids for tx #{}", candidate.txId);
            } catch (RuntimeException ex) {
                log.error("  DB update failed for tx #{}: {}", candidate.txId, ex.getMessage());
                // Report the DB failure as a pseudo-entry tied to the transaction
                report.addEntry(new PilotReportEntry(
                        candidate.txId, "(db-update)", "", "",
                        PilotReportEntry.STATUS_FAILED_UNKNOWN,
                        "DB UPDATE failed: " + ex.getMessage(), 0));
            }
        } else if (!allSucceeded) {
            log.warn("  Skipping DB update for tx #{} (some files failed)", candidate.txId);
        }
    }

    /**
     * Transactional update of blob_file_ids for a single transaction.
     * Fault isolation per row - one failure does not corrupt the whole pilot batch.
     */
    @Transactional
    public void updateBlobFileIds(int transactionId, String newBlobFileIds) {
        int updated = jdbc.update(
                "UPDATE transactions SET blob_file_ids = ? WHERE id = ?",
                newBlobFileIds, transactionId);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Expected 1 row updated for tx #" + transactionId + ", got " + updated);
        }
    }

    /**
     * Fetch the top-N most recent transactions with non-empty blob_file_ids.
     * Returns a materialized list; we classify in a second pass.
     */
    private List<PilotCandidate> selectPilotCandidates(int limit) {
        return jdbc.query(PILOT_SELECT_SQL + " LIMIT ?", (rs, rowNum) -> {
            int txId = rs.getInt("id");
            UUID entityId = (UUID) rs.getObject("entity_id");
            Integer entityNumber = (Integer) rs.getObject("entity_number");
            Date docDate = rs.getDate("doc_date");
            String blobFileIds = rs.getString("blob_file_ids");
            return new PilotCandidate(
                    txId,
                    entityId,
                    entityNumber == null ? 0 : entityNumber,
                    docDate == null ? null : docDate.toLocalDate(),
                    blobFileIds,
                    null); // driveIds filled after classification
        }, limit);
    }

    /**
     * Filters candidates to only DRIVE_ID_ONLY, populates their driveIds list,
     * and caps the returned list at {@code maxCount}.
     */
    private List<PilotCandidate> filterDriveIdOnly(List<PilotCandidate> candidates, int maxCount) {
        List<PilotCandidate> filtered = new ArrayList<>(maxCount);
        for (PilotCandidate c : candidates) {
            BlobReferenceParser.ClassificationResult cls =
                    BlobReferenceParser.classify(c.originalBlobFileIds);
            if (cls.getCategory() == BlobReferenceParser.Category.DRIVE_ID_ONLY) {
                filtered.add(c.withDriveIds(cls.getDriveIds()));
                if (filtered.size() >= maxCount) {
                    break;
                }
            }
        }
        return filtered;
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
        return Paths.get(System.getProperty("user.dir"), "migration-reports");
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal holder for a transaction being considered for pilot migration.
     * Uses a package-private static class instead of a record because we want
     * a mutable-style "with" builder for the driveIds field (set after
     * classification, which happens in a separate pass).
     */
    static final class PilotCandidate {
        final int txId;
        final UUID entityId;
        final int entityNumber;
        final LocalDate docDate;
        final String originalBlobFileIds;
        final List<String> driveIds;

        PilotCandidate(int txId, UUID entityId, int entityNumber, LocalDate docDate,
                       String originalBlobFileIds, List<String> driveIds) {
            this.txId = txId;
            this.entityId = entityId;
            this.entityNumber = entityNumber;
            this.docDate = docDate;
            this.originalBlobFileIds = originalBlobFileIds;
            this.driveIds = driveIds;
        }

        PilotCandidate withDriveIds(List<String> ids) {
            return new PilotCandidate(txId, entityId, entityNumber, docDate,
                    originalBlobFileIds, ids);
        }
    }
}
