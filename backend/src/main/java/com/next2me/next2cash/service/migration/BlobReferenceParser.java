package com.next2me.next2cash.service.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses and classifies the blob_file_ids column from the transactions table.
 *
 * The column historically stores three kinds of references, sometimes mixed
 * inside a single comma-separated string:
 *
 *   1. Google Drive file IDs (25-50 chars, base64url alphabet, no slashes)
 *      e.g. "1CeRVGZRT3gs1GxQyF_ylbX6RMQ4zT1Yh"
 *
 *   2. Azure Blob paths (contain a forward slash, start with entity UUID)
 *      e.g. "58202b71-4ddb-45c9-8e3c-39e816bde972/2026/04/4777/invoice.pdf"
 *
 *   3. Mixed: comma-separated combination of the above (partial migration state)
 *      e.g. "1kOHabc...,58202b71-.../2017/06/file.pdf"
 *
 * This parser is pure (no I/O) and is the single source of truth for the
 * dry-run classification report and for Phase 3 idempotency checks.
 *
 * Based on live data sampling (ATLAS + House legacy DBs, 20 Apr 2026):
 *   - 2.704 transactions have attachments
 *   - 3.683 total Drive file references
 *   - Max 3 files per transaction
 *   - Zero mixed or Azure-path entries in current data (all still on Drive)
 */
public final class BlobReferenceParser {

    /**
     * Drive file IDs use the base64url alphabet (A-Z, a-z, 0-9, underscore, dash).
     * Length 25-50 covers both legacy (v1, ~28 chars) and current (v3, ~33 chars) formats.
     * No forward slash permitted — that would be an Azure path.
     */
    private static final Pattern DRIVE_ID_PATTERN =
        Pattern.compile("^[A-Za-z0-9_-]{25,50}$");

    /**
     * Azure Blob path must start with a canonical UUID segment followed by
     * at least one more path segment. Full structural validation (year/month/
     * entity_number/filename) happens in Phase 3 on actual upload.
     */
    private static final Pattern AZURE_PATH_PATTERN =
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/.+");

    private BlobReferenceParser() {
        // static utility class
    }

    /**
     * Classify a raw blob_file_ids value into exactly one category.
     * Never returns null. Handles null, empty, whitespace-only, and malformed input.
     */
    public static ClassificationResult classify(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ClassificationResult(Category.EMPTY, List.of(), List.of(), List.of());
        }

        List<String> tokens = splitAndClean(raw);
        if (tokens.isEmpty()) {
            return new ClassificationResult(Category.EMPTY, List.of(), List.of(), List.of());
        }

        List<String> driveIds = new ArrayList<>();
        List<String> azurePaths = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        for (String token : tokens) {
            if (AZURE_PATH_PATTERN.matcher(token).matches()) {
                azurePaths.add(token);
            } else if (DRIVE_ID_PATTERN.matcher(token).matches()) {
                driveIds.add(token);
            } else {
                invalid.add(token);
            }
        }

        Category cat = resolveCategory(driveIds, azurePaths, invalid);
        return new ClassificationResult(cat, driveIds, azurePaths, invalid);
    }

    private static List<String> splitAndClean(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static Category resolveCategory(List<String> drive, List<String> azure, List<String> invalid) {
        boolean hasDrive = !drive.isEmpty();
        boolean hasAzure = !azure.isEmpty();
        boolean hasInvalid = !invalid.isEmpty();

        // Any invalid token with no valid ones -> INVALID
        if (hasInvalid && !hasDrive && !hasAzure) return Category.INVALID;

        // Mixed valid tokens -> MIXED (partial migration)
        if (hasDrive && hasAzure) return Category.MIXED;

        // Only Azure -> already migrated, skip on real run
        if (hasAzure) return Category.AZURE_PATH_ONLY;

        // Only Drive -> primary migration target
        if (hasDrive) return Category.DRIVE_ID_ONLY;

        return Category.INVALID;
    }

    public enum Category {
        EMPTY,              // null, blank, or whitespace only
        DRIVE_ID_ONLY,      // one or more Drive IDs, no Azure paths
        AZURE_PATH_ONLY,    // one or more Azure paths, no Drive IDs (already migrated)
        MIXED,              // at least one of each - partial migration state
        INVALID             // tokens present but none match expected patterns
    }

    /**
     * Immutable result of classification. Exposed lists are defensive copies.
     */
    public static final class ClassificationResult {
        private final Category category;
        private final List<String> driveIds;
        private final List<String> azurePaths;
        private final List<String> invalidTokens;

        public ClassificationResult(Category category,
                                    List<String> driveIds,
                                    List<String> azurePaths,
                                    List<String> invalidTokens) {
            this.category = category;
            this.driveIds = List.copyOf(driveIds);
            this.azurePaths = List.copyOf(azurePaths);
            this.invalidTokens = List.copyOf(invalidTokens);
        }

        public Category getCategory() { return category; }
        public List<String> getDriveIds() { return driveIds; }
        public List<String> getAzurePaths() { return azurePaths; }
        public List<String> getInvalidTokens() { return invalidTokens; }

        public int totalValidTokens() { return driveIds.size() + azurePaths.size(); }

        @Override
        public String toString() {
            return "ClassificationResult{" + category +
                   ", drive=" + driveIds.size() +
                   ", azure=" + azurePaths.size() +
                   ", invalid=" + invalidTokens.size() + "}";
        }
    }
}
