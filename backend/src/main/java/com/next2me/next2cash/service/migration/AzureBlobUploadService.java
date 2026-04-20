package com.next2me.next2cash.service.migration;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Uploads files to Azure Blob Storage under the Next2Cash document layout.
 *
 * <p>This service is only loaded under the "migration" Spring profile,
 * parallel to {@link DriveDownloadService}. The production web app uses its
 * own {@code DocumentController}-internal Azure client and is not affected
 * by this class.
 *
 * <h3>Blob path layout</h3>
 * Every file is placed at:
 * <pre>
 *   {entity_uuid}/{YYYY}/{MM}/{entity_number}/{sanitized_filename}
 * </pre>
 * Example:
 * <pre>
 *   58202b71-4ddb-45c9-8e3c-39e816bde972/2017/06/4777/Timologio_20170612.pdf
 * </pre>
 * This matches the layout used by the production {@code DocumentController}
 * (see bootstrap section "Target Azure Storage structure").
 *
 * <h3>Idempotency</h3>
 * Before uploading, the service checks whether a blob already exists at the
 * target path. If yes, the upload is skipped and the existing blob's size is
 * returned. This makes the migration pilot safely re-runnable.
 *
 * <h3>Filename sanitization</h3>
 * Greek characters are preserved. Control characters and Azure-illegal blob
 * name characters are removed or replaced. A {@code .pdf} extension is
 * enforced if the original has none, since all Next2me attachments are PDFs.
 *
 * <h3>Authentication</h3>
 * Uses a storage account connection string, read from
 * {@code next2cash.azure.blob.connection-string} (same property as the
 * production {@code DocumentController}). Falls back to env var
 * {@code AZURE_STORAGE_CONNECTION_STRING} via Spring's {@code @Value}
 * resolver.
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
@Service
@Profile("migration")
@ConditionalOnProperty(name = "migration.azure.enabled", havingValue = "true", matchIfMissing = true)
public class AzureBlobUploadService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobUploadService.class);

    /** Characters not allowed in Azure blob names (besides control chars). */
    private static final Pattern ILLEGAL_BLOB_CHARS = Pattern.compile("[\\\\:*?\"<>|]");

    /** Control characters (0x00-0x1F and 0x7F) plus slashes that break the path structure. */
    private static final Pattern CONTROL_AND_SLASH = Pattern.compile("[\\x00-\\x1F\\x7F/]");

    /** Azure blob name hard limit (1024 UTF-8 bytes). We conservatively cap at 200 chars. */
    private static final int MAX_FILENAME_CHARS = 200;

    private final String connectionString;
    private final String containerName;

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;

    public AzureBlobUploadService(
            @Value("${next2cash.azure.blob.connection-string}") String connectionString,
            @Value("${next2cash.azure.blob.container:next2cash-documents}") String containerName) {
        this.connectionString = connectionString;
        this.containerName = containerName;
    }

    /**
     * Initializes the Azure client and verifies that the target container
     * exists. Fails fast (application refuses to start) on misconfiguration.
     */
    @PostConstruct
    public void init() {
        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalStateException(
                    "Azure Blob connection string is empty. Set "
                            + "next2cash.azure.blob.connection-string or env var "
                            + "AZURE_STORAGE_CONNECTION_STRING before starting the migration profile.");
        }
        if (!connectionString.contains("AccountName=")) {
            throw new IllegalStateException(
                    "Azure Blob connection string looks malformed (missing AccountName=). "
                            + "Expected format: DefaultEndpointsProtocol=https;AccountName=...;AccountKey=...;EndpointSuffix=core.windows.net");
        }

        this.blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);

        if (!containerClient.exists()) {
            throw new IllegalStateException(
                    "Azure Blob container '" + containerName + "' does not exist. "
                            + "The container must be created by the production DocumentController "
                            + "or the Azure admin before running migration.");
        }

        log.info("AzureBlobUploadService initialized. Container: '{}', account: {}",
                containerName, extractAccountName(connectionString));
    }

    /**
     * Uploads a file to the canonical Next2Cash path layout.
     *
     * @param entityId        the entity UUID (becomes the top-level folder)
     * @param year            the document year (from transactions.doc_date)
     * @param month           the document month (1-12, zero-padded in the path)
     * @param entityNumber    the business-number of the transaction
     *                        (from transactions.entity_number)
     * @param originalFileName the file name as returned by Drive (may contain
     *                        Greek characters and illegal chars; will be sanitized)
     * @param content         the raw bytes to upload
     * @param contentType     the MIME type from Drive (e.g. "application/pdf");
     *                        falls back to "application/pdf" if null or blank
     * @return an {@link AzureBlobUploadResult} with the final path and whether
     *         the upload was skipped due to idempotency
     * @throws AzureUploadException on any storage failure
     */
    public AzureBlobUploadResult upload(
            UUID entityId,
            int year,
            int month,
            int entityNumber,
            String originalFileName,
            byte[] content,
            String contentType) {

        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        if (year < 1900 || year > 2200) {
            throw new IllegalArgumentException("year out of range: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be 1-12, got: " + month);
        }
        if (entityNumber < 0) {
            throw new IllegalArgumentException("entityNumber must be >= 0, got: " + entityNumber);
        }
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("originalFileName must not be null or blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content must not be null or empty");
        }
        if (containerClient == null) {
            throw new IllegalStateException(
                    "AzureBlobUploadService not initialized (init() was not called or failed)");
        }

        String sanitizedName = sanitizeFilename(originalFileName);
        String blobPath = buildBlobPath(entityId, year, month, entityNumber, sanitizedName);

        BlobClient blobClient = containerClient.getBlobClient(blobPath);

        // Idempotency: skip if already exists. Re-running the pilot is safe.
        try {
            if (blobClient.exists()) {
                long existingSize = blobClient.getProperties().getBlobSize();
                log.info("Blob already exists, skipping upload: {} ({} bytes)", blobPath, existingSize);
                return new AzureBlobUploadResult(blobPath, existingSize, true);
            }
        } catch (BlobStorageException bse) {
            throw new AzureUploadException(blobPath,
                    "Failed to check blob existence: " + blobPath, bse);
        }

        String effectiveContentType = (contentType == null || contentType.isBlank())
                ? "application/pdf"
                : contentType;

        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(effectiveContentType);

        try {
            blobClient.upload(BinaryData.fromBytes(content), /* overwrite */ false);
            blobClient.setHttpHeaders(headers);
            log.info("Uploaded blob: {} ({} bytes, {})", blobPath, content.length, effectiveContentType);
            return new AzureBlobUploadResult(blobPath, content.length, false);
        } catch (BlobStorageException bse) {
            // 409 Conflict: race condition where blob was created between exists() check and upload.
            // Treat as success (idempotent) - the blob is there, which is what we wanted.
            if (bse.getStatusCode() == 409) {
                log.warn("Blob was created concurrently, treating as successful idempotent upload: {}",
                        blobPath);
                return new AzureBlobUploadResult(blobPath, content.length, true);
            }
            throw new AzureUploadException(blobPath,
                    "Upload failed with Azure status " + bse.getStatusCode() + ": " + blobPath, bse);
        } catch (RuntimeException ex) {
            throw new AzureUploadException(blobPath,
                    "Unexpected failure during upload: " + blobPath, ex);
        }
    }

    /**
     * Builds the canonical blob path:
     * {@code {entity_uuid}/{YYYY}/{MM}/{entity_number}/{sanitized_filename}}.
     * Visible for testing.
     */
    static String buildBlobPath(UUID entityId, int year, int month, int entityNumber, String sanitizedName) {
        return String.format(Locale.ROOT, "%s/%04d/%02d/%d/%s",
                entityId.toString(), year, month, entityNumber, sanitizedName);
    }

    /**
     * Sanitizes a filename for safe use as an Azure blob name component.
     * <p>
     * Rules:
     * <ul>
     *   <li>Control chars (0x00-0x1F, 0x7F) are removed.</li>
     *   <li>Forward slashes (path separators) are replaced with underscore.</li>
     *   <li>Azure-illegal chars ({@code \ : * ? " < > |}) are replaced with underscore.</li>
     *   <li>Leading/trailing whitespace and dots are trimmed.</li>
     *   <li>If the result has no extension, ".pdf" is appended.</li>
     *   <li>Length is capped at {@link #MAX_FILENAME_CHARS} chars.</li>
     *   <li>Greek and other Unicode characters are preserved.</li>
     * </ul>
     * Visible for testing.
     */
    static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be null or blank");
        }

        // Step 1: Remove control chars and replace slashes
        String clean = CONTROL_AND_SLASH.matcher(name).replaceAll("_");

        // Step 2: Replace Azure-illegal chars
        clean = ILLEGAL_BLOB_CHARS.matcher(clean).replaceAll("_");

        // Step 3: Trim whitespace and leading/trailing dots
        clean = clean.trim();
        while (clean.startsWith(".")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith(".") || clean.endsWith(" ")) {
            clean = clean.substring(0, clean.length() - 1);
        }

        if (clean.isBlank()) {
            clean = "unnamed";
        }

        // Step 4: Ensure a file extension (assume .pdf for Next2me attachments)
        int lastDot = clean.lastIndexOf('.');
        boolean hasExtension = lastDot > 0 && lastDot < clean.length() - 1
                && lastDot > clean.length() - 10; // ext within last 9 chars
        if (!hasExtension) {
            clean = clean + ".pdf";
        }

        // Step 5: Cap length (keep extension intact)
        if (clean.length() > MAX_FILENAME_CHARS) {
            int dotIdx = clean.lastIndexOf('.');
            String ext = (dotIdx > 0) ? clean.substring(dotIdx) : "";
            int baseLen = MAX_FILENAME_CHARS - ext.length();
            if (baseLen < 1) {
                baseLen = 1;
            }
            clean = clean.substring(0, baseLen) + ext;
        }

        return clean;
    }

    /**
     * Extracts AccountName=... from a connection string for safe logging.
     * Never returns the AccountKey.
     */
    private static String extractAccountName(String connStr) {
        for (String part : connStr.split(";")) {
            if (part.startsWith("AccountName=")) {
                return part.substring("AccountName=".length());
            }
        }
        return "unknown";
    }
}
