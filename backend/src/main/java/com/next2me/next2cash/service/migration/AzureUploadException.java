package com.next2me.next2cash.service.migration;

/**
 * Thrown when an Azure Blob Storage upload fails.
 *
 * <p>Typical causes:
 * <ul>
 *   <li>Authentication failure (401/403) - bad or expired connection string</li>
 *   <li>Network timeout or connection reset</li>
 *   <li>Container not found (404) - next2cash-documents should always exist,
 *       but may be missing in a broken environment</li>
 *   <li>Storage quota exceeded (507)</li>
 *   <li>Server errors (5xx) after all retries exhausted</li>
 * </ul>
 *
 * <p>Unlike {@link DriveDownloadException}, Azure upload failures are always
 * treated as potentially retryable at the batch level: the migration pilot
 * will log and skip the failed record, and the operator can re-run the pilot
 * (the service is idempotent - existing blobs are not overwritten).
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
public class AzureUploadException extends RuntimeException {

    private final String blobPath;

    public AzureUploadException(String blobPath, String message) {
        super(message);
        this.blobPath = blobPath;
    }

    public AzureUploadException(String blobPath, String message, Throwable cause) {
        super(message, cause);
        this.blobPath = blobPath;
    }

    public String getBlobPath() {
        return blobPath;
    }
}