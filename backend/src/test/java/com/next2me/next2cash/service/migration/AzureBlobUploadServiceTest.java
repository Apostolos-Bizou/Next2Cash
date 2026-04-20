package com.next2me.next2cash.service.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.next2me.next2cash.service.migration.AzureBlobUploadService.buildBlobPath;
import static com.next2me.next2cash.service.migration.AzureBlobUploadService.sanitizeFilename;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AzureBlobUploadService}.
 *
 * <p>Strategy: test the pure static helpers ({@link AzureBlobUploadService#sanitizeFilename}
 * and {@link AzureBlobUploadService#buildBlobPath}) directly. The real
 * {@code upload()} method requires mocking the full Azure SDK (BlobClient,
 * BlobContainerClient, BlobServiceClient, BlobStorageException with internal
 * HttpResponse state), which is significantly more boilerplate than the logic
 * it covers. That path is exercised by the pilot dry-run (Block #7) and pilot
 * execution (Block #8) against the real Azure Storage account.
 *
 * <p>The tests here focus on the logic we wrote: filename sanitization rules
 * and blob path construction, plus input validation on the public API surface.
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
class AzureBlobUploadServiceTest {

    private AzureBlobUploadService service;

    private static final UUID NEXT2ME = UUID.fromString("58202b71-4ddb-45c9-8e3c-39e816bde972");

    @BeforeEach
    void setUp() {
        // Instantiate without init() - we only test argument validation on upload()
        // and static helpers; no BlobClient is needed.
        service = new AzureBlobUploadService(
                "DefaultEndpointsProtocol=https;AccountName=fake;AccountKey=fake;EndpointSuffix=core.windows.net",
                "next2cash-documents");
    }

    // ========================================================================
    // sanitizeFilename - basic cleanup
    // ========================================================================

    @Test
    @DisplayName("sanitizeFilename preserves a clean PDF name unchanged")
    void sanitizeCleanPdfName() {
        assertEquals("invoice_2024_03.pdf", sanitizeFilename("invoice_2024_03.pdf"));
    }

    @Test
    @DisplayName("sanitizeFilename preserves Greek characters")
    void sanitizePreservesGreek() {
        String input = "Τιμολόγιο_Μαρτίου_2024.pdf";
        String result = sanitizeFilename(input);
        assertEquals(input, result, "Greek letters should survive sanitization");
    }

    @Test
    @DisplayName("sanitizeFilename preserves Greek with spaces")
    void sanitizePreservesGreekWithSpaces() {
        String input = "Απόδειξη πληρωμής 12-06-2017.pdf";
        String result = sanitizeFilename(input);
        assertEquals(input, result);
    }

    // ========================================================================
    // sanitizeFilename - illegal character removal
    // ========================================================================

    @Test
    @DisplayName("sanitizeFilename replaces forward slashes with underscore")
    void sanitizeRemovesSlashes() {
        assertEquals("folder_subfolder_file.pdf", sanitizeFilename("folder/subfolder/file.pdf"));
    }

    @Test
    @DisplayName("sanitizeFilename replaces Azure-illegal chars (\\ : * ? \" < > |)")
    void sanitizeRemovesAzureIllegalChars() {
        String input = "file:name*with?illegal\"chars<here>.pdf";
        String result = sanitizeFilename(input);
        assertEquals("file_name_with_illegal_chars_here_.pdf", result);
        // No illegal chars remain
        for (char c : new char[]{':', '*', '?', '"', '<', '>', '|', '\\'}) {
            assertFalse(result.indexOf(c) >= 0,
                    "Illegal char '" + c + "' should have been removed, got: " + result);
        }
    }

    @Test
    @DisplayName("sanitizeFilename removes control characters")
    void sanitizeRemovesControlChars() {
        String input = "file\u0000with\u0001control\u001Fchars.pdf";
        String result = sanitizeFilename(input);
        assertEquals("file_with_control_chars.pdf", result);
    }

    // ========================================================================
    // sanitizeFilename - extension handling
    // ========================================================================

    @Test
    @DisplayName("sanitizeFilename adds .pdf extension when missing")
    void sanitizeAddsPdfExtension() {
        assertEquals("document.pdf", sanitizeFilename("document"));
        assertEquals("no_extension_here.pdf", sanitizeFilename("no_extension_here"));
    }

    @Test
    @DisplayName("sanitizeFilename preserves non-pdf extensions")
    void sanitizePreservesOtherExtensions() {
        assertEquals("receipt.jpg", sanitizeFilename("receipt.jpg"));
        assertEquals("scan.tiff", sanitizeFilename("scan.tiff"));
    }

    // ========================================================================
    // sanitizeFilename - edge cases
    // ========================================================================

    @Test
    @DisplayName("sanitizeFilename trims leading dots")
    void sanitizeTrimsLeadingDots() {
        assertEquals("hidden.pdf", sanitizeFilename("...hidden.pdf"));
    }

    @Test
    @DisplayName("sanitizeFilename trims trailing whitespace and dots")
    void sanitizeTrimsTrailingJunk() {
        assertEquals("trailing.pdf", sanitizeFilename("trailing.pdf   ..."));
    }

    @Test
    @DisplayName("sanitizeFilename caps length at 200 chars, keeps extension")
    void sanitizeCapsLength() {
        String longBase = "a".repeat(300);
        String input = longBase + ".pdf";

        String result = sanitizeFilename(input);

        assertTrue(result.length() <= 200, "Result length " + result.length() + " should be <= 200");
        assertTrue(result.endsWith(".pdf"), "Extension should be preserved");
    }

    @Test
    @DisplayName("sanitizeFilename rejects null and blank")
    void sanitizeRejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> sanitizeFilename(null));
        assertThrows(IllegalArgumentException.class, () -> sanitizeFilename(""));
        assertThrows(IllegalArgumentException.class, () -> sanitizeFilename("   "));
    }

    @Test
    @DisplayName("sanitizeFilename handles string of only illegal chars with fallback")
    void sanitizeHandlesAllIllegal() {
        // After replacing all illegal chars and trimming dots, nothing remains
        // The sanitizer substitutes "unnamed" and appends .pdf
        String result = sanitizeFilename("...");
        assertEquals("unnamed.pdf", result);
    }

    // ========================================================================
    // buildBlobPath
    // ========================================================================

    @Test
    @DisplayName("buildBlobPath uses canonical {uuid}/{YYYY}/{MM}/{num}/{name} format")
    void buildBlobPathBasic() {
        String path = buildBlobPath(NEXT2ME, 2017, 6, 4777, "invoice.pdf");
        assertEquals("58202b71-4ddb-45c9-8e3c-39e816bde972/2017/06/4777/invoice.pdf", path);
    }

    @Test
    @DisplayName("buildBlobPath zero-pads single-digit months")
    void buildBlobPathZeroPadsMonth() {
        String path = buildBlobPath(NEXT2ME, 2024, 1, 100, "file.pdf");
        assertTrue(path.contains("/2024/01/"), "Month should be zero-padded: " + path);
    }

    @Test
    @DisplayName("buildBlobPath does not zero-pad entity number")
    void buildBlobPathDoesNotPadEntityNumber() {
        String path = buildBlobPath(NEXT2ME, 2024, 12, 5, "file.pdf");
        assertTrue(path.contains("/12/5/"), "Entity number should not be padded: " + path);
    }

    // ========================================================================
    // upload() input validation
    // ========================================================================

    @Test
    @DisplayName("upload rejects null entityId")
    void uploadRejectsNullEntityId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(null, 2024, 1, 100, "file.pdf", new byte[]{1}, "application/pdf"));
    }

    @Test
    @DisplayName("upload rejects invalid year")
    void uploadRejectsInvalidYear() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 1800, 1, 100, "file.pdf", new byte[]{1}, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2500, 1, 100, "file.pdf", new byte[]{1}, null));
    }

    @Test
    @DisplayName("upload rejects invalid month")
    void uploadRejectsInvalidMonth() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2024, 0, 100, "file.pdf", new byte[]{1}, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2024, 13, 100, "file.pdf", new byte[]{1}, null));
    }

    @Test
    @DisplayName("upload rejects negative entityNumber")
    void uploadRejectsNegativeEntityNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2024, 1, -1, "file.pdf", new byte[]{1}, null));
    }

    @Test
    @DisplayName("upload rejects null or blank filename")
    void uploadRejectsBadFilename() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2024, 1, 100, null, new byte[]{1}, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2024, 1, 100, "", new byte[]{1}, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2024, 1, 100, "   ", new byte[]{1}, null));
    }

    @Test
    @DisplayName("upload rejects null or empty content")
    void uploadRejectsBadContent() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2024, 1, 100, "file.pdf", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(NEXT2ME, 2024, 1, 100, "file.pdf", new byte[0], null));
    }

    @Test
    @DisplayName("upload without init throws IllegalStateException")
    void uploadWithoutInitThrows() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.upload(NEXT2ME, 2024, 1, 100, "file.pdf", new byte[]{1, 2, 3}, null));
        assertTrue(ex.getMessage().toLowerCase().contains("not initialized"),
                "Error message should mention initialization: " + ex.getMessage());
    }
}
