package com.next2me.next2cash.service.migration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Collections;

/**
 * Authenticated Google Drive API client for one-time migration of legacy
 * attachments to Azure Blob Storage.
 *
 * <p>This service is only loaded under the "migration" Spring profile, to
 * avoid carrying Drive credentials into the production web application.
 * The production runtime never needs Drive access: once the migration
 * completes (Phase 3, Session #32), all references point to Azure.
 *
 * <h3>Authentication</h3>
 * A Google service account JSON key is loaded from the file system at
 * startup. The file location is configurable via
 * {@code migration.gcp.credentials-path} (default:
 * {@code credentials/gcp-service-account.json}). The service account must
 * have at minimum {@link DriveScopes#DRIVE_READONLY DRIVE_READONLY} scope
 * and must have been explicitly shared into the root Drive folder
 * containing the legacy documents.
 *
 * <h3>Retry logic</h3>
 * Transient failures (HTTP 429, 500, 502, 503, 504, and generic IOExceptions)
 * are retried up to 3 times with exponential backoff (1s, 2s, 4s).
 * Permanent failures (404, 403) are thrown immediately as
 * {@link DriveFileNotFoundException} / {@link DrivePermissionDeniedException}.
 *
 * <h3>Thread safety</h3>
 * The underlying {@link Drive} client is thread-safe per Google's SDK
 * documentation; a single instance is shared across calls. This service
 * itself has no mutable state after {@link #init()} completes.
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
@Service
@Profile("migration")
@ConditionalOnProperty(name = "migration.gcp.enabled", havingValue = "true", matchIfMissing = true)
public class DriveDownloadService {

    private static final Logger log = LoggerFactory.getLogger(DriveDownloadService.class);

    private static final String APPLICATION_NAME = "Next2Cash-Migration/1.0";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1_000L;

    // Fields needed for the Drive Files.get().execute() metadata call
    private static final String METADATA_FIELDS = "id,name,mimeType,size,modifiedTime";

    private final String credentialsPath;

    private Drive driveClient;

    public DriveDownloadService(
            @Value("${migration.gcp.credentials-path:credentials/gcp-service-account.json}") String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    /**
     * Initializes the authenticated Drive client at application startup.
     * Fails fast (application refuses to start) if credentials are missing
     * or malformed - better than a cryptic 500 error on the 99th download.
     */
    @PostConstruct
    public void init() throws IOException, GeneralSecurityException {
        Path keyPath = Paths.get(credentialsPath);
        if (!Files.exists(keyPath)) {
            throw new IllegalStateException(
                    "GCP service account key not found at: " + keyPath.toAbsolutePath()
                            + ". Set migration.gcp.credentials-path or place the JSON key "
                            + "at credentials/gcp-service-account.json (relative to working directory).");
        }
        if (!Files.isReadable(keyPath)) {
            throw new IllegalStateException(
                    "GCP service account key is not readable: " + keyPath.toAbsolutePath());
        }

        GoogleCredentials credentials;
        try (InputStream keyStream = new FileInputStream(keyPath.toFile())) {
            credentials = GoogleCredentials.fromStream(keyStream)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE_READONLY));
        }

        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        this.driveClient = new Drive.Builder(
                transport,
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        log.info("DriveDownloadService initialized. Key: {} (readable, {} bytes). Scope: DRIVE_READONLY.",
                keyPath.toAbsolutePath(), Files.size(keyPath));
    }

    /**
     * Downloads the content and metadata of a single Drive file.
     *
     * @param driveId the Google Drive file ID (25-50 base64url characters,
     *                as stored in transactions.blob_file_ids)
     * @return the file content and metadata, ready to be uploaded to Azure
     * @throws DriveFileNotFoundException    if the file does not exist or the
     *                                       service account cannot see it (404)
     * @throws DrivePermissionDeniedException if the service account lacks
     *                                       explicit read access (403)
     * @throws DriveDownloadException        for all other failures, including
     *                                       network errors and exhausted retries
     */
    public DriveFileContent download(String driveId) {
        if (driveId == null || driveId.isBlank()) {
            throw new IllegalArgumentException("driveId must not be null or blank");
        }
        if (driveClient == null) {
            throw new IllegalStateException(
                    "DriveDownloadService not initialized (init() was not called or failed)");
        }

        // Step 1: fetch metadata (needed for filename, mimeType, size)
        File metadata = executeWithRetry(driveId, "metadata fetch",
                () -> driveClient.files().get(driveId)
                        .setFields(METADATA_FIELDS)
                        .execute());

        // Step 2: download raw bytes
        byte[] bytes = executeWithRetry(driveId, "content download",
                () -> {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    driveClient.files().get(driveId).executeMediaAndDownloadTo(buffer);
                    return buffer.toByteArray();
                });

        Instant modifiedTime = metadata.getModifiedTime() != null
                ? Instant.ofEpochMilli(metadata.getModifiedTime().getValue())
                : null;

        long size = metadata.getSize() != null ? metadata.getSize() : bytes.length;

        DriveFileContent content = new DriveFileContent(
                bytes,
                metadata.getName(),
                metadata.getMimeType(),
                size,
                modifiedTime);

        log.debug("Downloaded Drive file {}: {}", driveId, content);
        return content;
    }

    /**
     * Wraps a Drive API call with retry logic and exception classification.
     * Visible for testing - production callers should use {@link #download(String)}.
     */
    <T> T executeWithRetry(String driveId, String operation, DriveCall<T> call) {
        long backoffMs = INITIAL_BACKOFF_MS;
        IOException lastTransientError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return call.execute();
            } catch (GoogleJsonResponseException gjre) {
                int status = gjre.getStatusCode();
                if (status == 404) {
                    throw new DriveFileNotFoundException(driveId,
                            "Drive file not found (or invisible to service account): " + driveId, gjre);
                }
                if (status == 403) {
                    throw new DrivePermissionDeniedException(driveId,
                            "Permission denied for Drive file: " + driveId, gjre);
                }
                if (isRetryableStatus(status) && attempt < MAX_RETRIES) {
                    log.warn("Drive {} failed for {} (HTTP {}), attempt {}/{}, backing off {}ms",
                            operation, driveId, status, attempt, MAX_RETRIES, backoffMs);
                    sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                throw new DriveDownloadException(driveId,
                        "Drive " + operation + " failed with HTTP " + status + " for " + driveId, gjre);
            } catch (IOException ioe) {
                lastTransientError = ioe;
                if (attempt < MAX_RETRIES) {
                    log.warn("Drive {} failed for {} with IOException, attempt {}/{}, backing off {}ms: {}",
                            operation, driveId, attempt, MAX_RETRIES, backoffMs, ioe.getMessage());
                    sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
            }
        }

        throw new DriveDownloadException(driveId,
                "Drive " + operation + " failed after " + MAX_RETRIES + " attempts for " + driveId,
                lastTransientError);
    }

    private static boolean isRetryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new DriveDownloadException(null, "Interrupted during retry backoff", ie);
        }
    }

    /**
     * Functional interface for retryable Drive API calls. Visible for testing.
     */
    @FunctionalInterface
    interface DriveCall<T> {
        T execute() throws IOException;
    }
}
