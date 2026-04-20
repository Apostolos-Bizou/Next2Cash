package com.next2me.next2cash.service.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Spring Boot CLI entry point for the Session #31 pilot migration.
 *
 * <p>Activated only when the property {@code migration.pilot} is set to
 * either "dry-run" or "execute". In normal production runs this bean is
 * never instantiated, so it has zero impact on the running backend.
 *
 * <p>Runs under the "migration" Spring profile so the Drive and Azure
 * services are loaded.
 *
 * <h3>Usage (local only)</h3>
 * <pre>
 * # Dry-run (select + classify 100 transactions, NO network I/O, NO DB writes)
 * mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=migration --migration.pilot=dry-run"
 *
 * # Real run (download + upload + update DB)
 * mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=migration --migration.pilot=execute"
 * </pre>
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
@Component
@Profile("migration")
@ConditionalOnProperty(name = "migration.pilot")
public class PilotRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PilotRunner.class);

    private static final String MODE_DRY_RUN = "dry-run";
    private static final String MODE_EXECUTE = "execute";

    private final BlobMigrationService service;
    private final String mode;

    public PilotRunner(
            BlobMigrationService service,
            @Value("${migration.pilot}") String mode) {
        this.service = service;
        this.mode = mode;
    }

    @Override
    public void run(String... args) throws Exception {
        if (mode == null || mode.isBlank()) {
            log.error("migration.pilot must be set to '{}' or '{}', got empty",
                    MODE_DRY_RUN, MODE_EXECUTE);
            return;
        }

        boolean execute;
        if (MODE_DRY_RUN.equalsIgnoreCase(mode)) {
            execute = false;
        } else if (MODE_EXECUTE.equalsIgnoreCase(mode)) {
            execute = true;
        } else {
            log.error("Invalid migration.pilot value: '{}'. Must be '{}' or '{}'.",
                    mode, MODE_DRY_RUN, MODE_EXECUTE);
            return;
        }

        log.info("================================================");
        log.info(" PILOT MIGRATION ACTIVATED - mode: {}", mode.toUpperCase());
        if (!execute) {
            log.info(" (dry-run: no network I/O, no DB writes)");
        } else {
            log.info(" (execute: Drive -> Azure, DB updates ENABLED)");
            log.info(" A rollback SQL will be written before any DB update.");
        }
        log.info("================================================");

        PilotReport report = service.runPilot(execute);

        Path outputDir = service.resolveReportDir();
        Path csvFile = report.writeCsv(outputDir);

        log.info("");
        log.info("CSV report: {}", csvFile);
        log.info("Total entries: {}", report.getTotalEntries());

        if (execute && report.getTransactionsUpdated() > 0) {
            Path rollbackFile = report.writeRollbackSql(outputDir);
            log.info("Rollback SQL: {}", rollbackFile);
            log.info("Transactions updated: {}", report.getTransactionsUpdated());
        }

        log.info("");
        log.info("Pilot complete. Review the CSV before proceeding to Phase 3.");
    }
}