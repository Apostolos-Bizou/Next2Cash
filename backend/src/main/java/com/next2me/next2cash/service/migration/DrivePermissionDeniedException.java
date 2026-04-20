package com.next2me.next2cash.service.migration;

/**
 * Thrown when the service account lacks permission to access a Drive file
 * (HTTP 403).
 *
 * <p>This is a permanent error. In practice, Drive often returns 404 instead
 * of 403 to prevent information disclosure (confirming a file exists by
 * showing a different status code). However, some edge cases do return 403
 * directly - most commonly when the root folder was shared with the service
 * account but an individual file has more restrictive ACLs.
 *
 * <p>Recovery: Usually requires manual intervention (re-sharing the folder,
 * granting explicit access to the service account email).
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
public class DrivePermissionDeniedException extends RuntimeException {

    private final String driveId;

    public DrivePermissionDeniedException(String driveId, String message) {
        super(message);
        this.driveId = driveId;
    }

    public DrivePermissionDeniedException(String driveId, String message, Throwable cause) {
        super(message, cause);
        this.driveId = driveId;
    }

    public String getDriveId() {
        return driveId;
    }
}