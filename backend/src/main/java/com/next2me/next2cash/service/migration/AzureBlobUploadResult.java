package com.next2me.next2cash.service.migration;

/**
 * Result of an Azure Blob upload operation.
 *
 * <p>Returned by
 * {@link AzureBlobUploadService#upload(java.util.UUID, int, int, int, String, byte[], String)}.
 * The {@code blobPath} is the key that should be written to the database
 * ({@code transactions.blob_file_ids}) to replace the original Drive ID.
 *
 * <p>The {@code skipped} flag indicates that the blob already existed at the
 * target path with identical size, so no actual upload happened. This is
 * critical for idempotent re-runs of the migration pilot.
 *
 * @param blobPath the full blob path within the container, e.g.
 *                 {@code 58202b71-4ddb-45c9-8e3c-39e816bde972/2026/04/4777/invoice.pdf}
 * @param size     the size of the uploaded (or pre-existing) blob, in bytes
 * @param skipped  {@code true} if the blob already existed and upload was skipped
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
public record AzureBlobUploadResult(
        String blobPath,
        long size,
        boolean skipped
) {
}