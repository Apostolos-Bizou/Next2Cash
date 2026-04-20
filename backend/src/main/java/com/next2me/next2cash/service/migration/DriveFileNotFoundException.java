package com.next2me.next2cash.service.migration;

/**
 * Thrown when a Google Drive file cannot be found (HTTP 404).
 *
 * <p>This is a permanent error: retrying will not help. During the
 * migration pilot the caller should log the failure, mark the record
 * as skipped in the CSV report, and continue with the next record.
 *
 * <p>Common causes:
 * <ul>
 *   <li>The Drive ID in blob_file_ids points to a file that was deleted</li>
 *   <li>The service account does not have the root folder shared with it
 *       (Drive returns 404 instead of 403 for security reasons)</li>
 *   <li>Typo or corruption in the stored Drive ID</li>
 * </ul>
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
public class DriveFileNotFoundException extends RuntimeException {

    private final String driveId;

    public DriveFileNotFoundException(String driveId, String message) {
        super(message);
        this.driveId = driveId;
    }

    public DriveFileNotFoundException(String driveId, String message, Throwable cause) {
        super(message, cause);
        this.driveId = driveId;
    }

    public String getDriveId() {
        return driveId;
    }
}