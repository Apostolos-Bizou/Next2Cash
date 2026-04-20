package com.next2me.next2cash.service.migration;

/**
 * Single row in the pilot execution CSV report.
 * One entry per Drive file processed (a transaction with 3 Drive IDs
 * produces 3 entries).
 *
 * <p>Status values:
 * <ul>
 *   <li>{@code SUCCESS} - downloaded + uploaded + (pending DB update)</li>
 *   <li>{@code SKIPPED_EXISTS} - Azure blob already exists (idempotent re-run)</li>
 *   <li>{@code FAILED_DRIVE_404} - Drive file not found or no access</li>
 *   <li>{@code FAILED_DRIVE_403} - Drive permission denied</li>
 *   <li>{@code FAILED_DRIVE_OTHER} - other Drive download failure</li>
 *   <li>{@code FAILED_AZURE} - Azure upload failure</li>
 *   <li>{@code FAILED_UNKNOWN} - unexpected runtime exception</li>
 * </ul>
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
public record PilotReportEntry(
        int transactionId,
        String driveId,
        String fileName,
        String azurePath,
        String status,
        String errorMessage,
        long durationMs
) {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_SKIPPED_EXISTS = "SKIPPED_EXISTS";
    public static final String STATUS_FAILED_DRIVE_404 = "FAILED_DRIVE_404";
    public static final String STATUS_FAILED_DRIVE_403 = "FAILED_DRIVE_403";
    public static final String STATUS_FAILED_DRIVE_OTHER = "FAILED_DRIVE_OTHER";
    public static final String STATUS_FAILED_AZURE = "FAILED_AZURE";
    public static final String STATUS_FAILED_UNKNOWN = "FAILED_UNKNOWN";

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status) || STATUS_SKIPPED_EXISTS.equals(status);
    }
}