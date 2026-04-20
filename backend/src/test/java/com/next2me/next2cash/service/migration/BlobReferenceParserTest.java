package com.next2me.next2cash.service.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlobReferenceParser.
 *
 * Test fixtures use REAL Drive file IDs sampled from the ATLAS legacy DB
 * (transactions #2, #11, #14, #101 — all from 2017 historical imports) and
 * a REAL Azure path format using the Next2me entity UUID
 * (58202b71-4ddb-45c9-8e3c-39e816bde972).
 */
class BlobReferenceParserTest {

    // Real Drive IDs from ATLAS transactions table
    private static final String DRIVE_ID_1 = "1PVL6ran_7k8aQoMupwIZhy0Cc-Lk6-6y";
    private static final String DRIVE_ID_2 = "1IGl0y8Uj5NcOwkWJOoK-fGXhY4Uaw7GN";
    private static final String DRIVE_ID_3 = "1nc_cW3kyOL4TraJ-sjgM8Ca2EIpQC_Si";

    // Real entity UUID (Next2me)
    private static final String AZURE_PATH_1 =
        "58202b71-4ddb-45c9-8e3c-39e816bde972/2017/06/4777/invoice.pdf";
    private static final String AZURE_PATH_2 =
        "58202b71-4ddb-45c9-8e3c-39e816bde972/2026/04/90071/receipt.pdf";

    @Test
    @DisplayName("null input returns EMPTY")
    void nullReturnsEmpty() {
        BlobReferenceParser.ClassificationResult r = BlobReferenceParser.classify(null);
        assertEquals(BlobReferenceParser.Category.EMPTY, r.getCategory());
        assertTrue(r.getDriveIds().isEmpty());
        assertTrue(r.getAzurePaths().isEmpty());
    }

    @Test
    @DisplayName("empty string returns EMPTY")
    void emptyReturnsEmpty() {
        assertEquals(BlobReferenceParser.Category.EMPTY,
            BlobReferenceParser.classify("").getCategory());
    }

    @Test
    @DisplayName("whitespace-only returns EMPTY")
    void whitespaceReturnsEmpty() {
        assertEquals(BlobReferenceParser.Category.EMPTY,
            BlobReferenceParser.classify("   \t\n  ").getCategory());
    }

    @Test
    @DisplayName("commas only returns EMPTY")
    void commasOnlyReturnsEmpty() {
        assertEquals(BlobReferenceParser.Category.EMPTY,
            BlobReferenceParser.classify(" , , , ").getCategory());
    }

    @Test
    @DisplayName("single real Drive ID -> DRIVE_ID_ONLY")
    void singleDriveId() {
        BlobReferenceParser.ClassificationResult r = BlobReferenceParser.classify(DRIVE_ID_1);
        assertEquals(BlobReferenceParser.Category.DRIVE_ID_ONLY, r.getCategory());
        assertEquals(1, r.getDriveIds().size());
        assertEquals(DRIVE_ID_1, r.getDriveIds().get(0));
        assertTrue(r.getAzurePaths().isEmpty());
    }

    @Test
    @DisplayName("two Drive IDs comma-separated (real sample from tx #2) -> DRIVE_ID_ONLY")
    void twoDriveIds() {
        String raw = DRIVE_ID_1 + "," + DRIVE_ID_2;
        BlobReferenceParser.ClassificationResult r = BlobReferenceParser.classify(raw);
        assertEquals(BlobReferenceParser.Category.DRIVE_ID_ONLY, r.getCategory());
        assertEquals(2, r.getDriveIds().size());
    }

    @Test
    @DisplayName("three Drive IDs with whitespace padding -> DRIVE_ID_ONLY")
    void threeDriveIdsWithWhitespace() {
        String raw = " " + DRIVE_ID_1 + " , " + DRIVE_ID_2 + " , " + DRIVE_ID_3 + " ";
        BlobReferenceParser.ClassificationResult r = BlobReferenceParser.classify(raw);
        assertEquals(BlobReferenceParser.Category.DRIVE_ID_ONLY, r.getCategory());
        assertEquals(3, r.getDriveIds().size());
        // Verify trim worked
        assertEquals(DRIVE_ID_1, r.getDriveIds().get(0));
    }

    @Test
    @DisplayName("single Azure path -> AZURE_PATH_ONLY (already migrated)")
    void singleAzurePath() {
        BlobReferenceParser.ClassificationResult r = BlobReferenceParser.classify(AZURE_PATH_1);
        assertEquals(BlobReferenceParser.Category.AZURE_PATH_ONLY, r.getCategory());
        assertEquals(1, r.getAzurePaths().size());
        assertTrue(r.getDriveIds().isEmpty());
    }

    @Test
    @DisplayName("Drive ID + Azure path -> MIXED (partial migration)")
    void mixedDriveAndAzure() {
        String raw = DRIVE_ID_1 + "," + AZURE_PATH_1;
        BlobReferenceParser.ClassificationResult r = BlobReferenceParser.classify(raw);
        assertEquals(BlobReferenceParser.Category.MIXED, r.getCategory());
        assertEquals(1, r.getDriveIds().size());
        assertEquals(1, r.getAzurePaths().size());
        assertEquals(2, r.totalValidTokens());
    }

    @Test
    @DisplayName("too-short garbage token -> INVALID")
    void tooShortGarbage() {
        BlobReferenceParser.ClassificationResult r = BlobReferenceParser.classify("abc123");
        assertEquals(BlobReferenceParser.Category.INVALID, r.getCategory());
        assertEquals(1, r.getInvalidTokens().size());
    }

    @Test
    @DisplayName("invalid tokens mixed with valid Drive ID -> DRIVE_ID_ONLY (skip invalid)")
    void invalidMixedWithValidDrive() {
        String raw = "abc," + DRIVE_ID_1 + ",xyz";
        BlobReferenceParser.ClassificationResult r = BlobReferenceParser.classify(raw);
        // Has valid Drive + invalid tokens -> category is DRIVE_ID_ONLY
        // but invalidTokens list captures the junk for reporting
        assertEquals(BlobReferenceParser.Category.DRIVE_ID_ONLY, r.getCategory());
        assertEquals(1, r.getDriveIds().size());
        assertEquals(2, r.getInvalidTokens().size());
    }

    @Test
    @DisplayName("Drive ID pattern rejects forward slash (prevents path confusion)")
    void driveIdWithSlashIsInvalid() {
        // A fake "Drive ID" containing a slash — must NOT match DRIVE_ID_PATTERN
        // and also NOT match AZURE_PATH_PATTERN (no leading UUID) -> INVALID
        BlobReferenceParser.ClassificationResult r =
            BlobReferenceParser.classify("1ABCDEF/GHIJKLMNOPQRSTUVWXYZ");
        assertEquals(BlobReferenceParser.Category.INVALID, r.getCategory());
    }
}
