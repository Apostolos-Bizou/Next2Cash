package com.next2me.next2cash.service.migration;

import java.time.Instant;

/**
 * Immutable DTO for a downloaded Google Drive file.
 *
 * Returned by {@link DriveDownloadService#download(String)} after a
 * successful fetch. The {@code bytes} array contains the raw file content
 * (typically a PDF) and is intended to be uploaded as-is to Azure Blob
 * Storage by {@code AzureBlobUploadService}.
 *
 * <p>Metadata fields are extracted from the Drive API response:
 * <ul>
 *   <li>{@code fileName} - original file name on Drive (may contain Greek
 *       characters, must be sanitized before use as an Azure blob name)</li>
 *   <li>{@code mimeType} - e.g. "application/pdf"; may be null for
 *       Google-native formats (Docs, Sheets) which are not expected here</li>
 *   <li>{@code size} - file size in bytes, as reported by Drive metadata</li>
 *   <li>{@code modifiedTime} - last modification timestamp on Drive</li>
 * </ul>
 *
 * <p>Instances are safe to log (no sensitive content beyond the file name).
 * Do NOT log {@code bytes} directly.
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
public record DriveFileContent(
        byte[] bytes,
        String fileName,
        String mimeType,
        long size,
        Instant modifiedTime
) {

    /**
     * Defensive copy for the mutable {@code bytes} array at construction time.
     * Java 17 records don't deep-copy arrays by default, but we want to make
     * sure callers cannot mutate our bytes after we hand them off.
     */
    public DriveFileContent {
        if (bytes != null) {
            bytes = bytes.clone();
        }
    }

    /**
     * Returns a defensive copy of the bytes array. Callers should prefer
     * this over {@link #bytes()} when they need to mutate the data.
     */
    public byte[] bytesCopy() {
        return bytes == null ? null : bytes.clone();
    }

    /**
     * Human-readable summary for logging (excludes byte content).
     */
    @Override
    public String toString() {
        return "DriveFileContent{" +
                "fileName='" + fileName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", size=" + size +
                ", modifiedTime=" + modifiedTime +
                ", bytesLength=" + (bytes == null ? 0 : bytes.length) +
                '}';
    }
}