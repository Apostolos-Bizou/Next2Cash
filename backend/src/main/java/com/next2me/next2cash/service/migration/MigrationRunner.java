package com.next2me.next2cash.service.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Spring Boot CLI entry point for the migration dry-run.
 *
 * Activated ONLY when the property migration.dry-run=true is set.
 * In normal production runs this bean is never instantiated, so it has
 * ZERO impact on the running backend.
 *
 * Usage (local only):
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--migration.dry-run=true
 *
 * Or:
 *   java -jar target/next2cash-1.0.0.jar --migration.dry-run=true
 */
@Component
@ConditionalOnProperty(name = "migration.dry-run", havingValue = "true", matchIfMissing = false)
public class MigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final BlobMigrationService service;

    public MigrationRunner(BlobMigrationService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("============================================");
        log.info(" MIGRATION DRY-RUN ACTIVATED");
        log.info(" (no data will be modified — read-only scan)");
        log.info("============================================");

        MigrationReport report = service.runDryRun();

        Path outputDir = service.resolveReportDir();
        Path csvFile = report.writeCsv(outputDir);

        log.info("");
        log.info("✓ CSV report written to: {}", csvFile);
        log.info("  Entries: {}", report.getEntryCount());
        log.info("");
        log.info("Next step: review the CSV, then proceed to Session #31 (pilot migration).");
    }
}
