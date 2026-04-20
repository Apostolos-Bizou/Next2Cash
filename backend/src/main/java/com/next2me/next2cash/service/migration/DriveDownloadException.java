package com.next2me.next2cash.service.migration;

/**
 * Thrown when a Drive file download fails for reasons other than
 * not-found or permission-denied.
 *
 * <p>Typical causes:
 * <ul>
 *   <li>Network timeout or connection reset</li>
 *   <li>Drive API 5xx server errors after all retries exhausted</li>
 *   <li>Rate limit (429) after all retries exhausted</li>
 *   <li>IOException while reading the response stream</li>
 *   <li>Invalid credentials (401) - usually indicates expired or corrupted
 *       service account JSON key</li>
 * </ul>
 *
 * <p>The {@link #getCause()} should always be set to the underlying
 * IOException or GoogleJsonResponseException for diagnostic logging.
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
public class DriveDownloadException extends RuntimeException {

    private final String driveId;

    public DriveDownloadException(String driveId, String message) {
        super(message);
        this.driveId = driveId;
    }

    public DriveDownloadException(String driveId, String message, Throwable cause) {
        super(message, cause);
        this.driveId = driveId;
    }

    public String getDriveId() {
        return driveId;
    }
}