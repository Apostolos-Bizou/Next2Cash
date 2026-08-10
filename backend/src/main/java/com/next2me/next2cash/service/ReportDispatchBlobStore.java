package com.next2me.next2cash.service;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the production Azure Blob container used for Report
 * dispatch PDFs. Uses the SAME connection string + container as
 * DocumentController (next2cash-documents). No new resource.
 *
 * Separated into its own bean so tests can @MockBean it (the dummy test
 * connection string cannot reach real Azure — same reason DocumentController's
 * upload happy-path is not integration-tested).
 */
@Service
@Slf4j
public class ReportDispatchBlobStore {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final String connectionString;
    private final String containerName;

    public ReportDispatchBlobStore(
            @Value("${next2cash.azure.blob.connection-string:}") String connectionString,
            @Value("${next2cash.azure.blob.container:next2cash-documents}") String containerName) {
        this.connectionString = connectionString;
        this.containerName = containerName;
    }

    /** Upload bytes to the given blob path (overwrite). Throws on failure. */
    public void upload(String blobPath, byte[] bytes) {
        BlobClient blob = client(blobPath);
        blob.upload(BinaryData.fromBytes(bytes), /* overwrite */ true);
        blob.setHttpHeaders(new BlobHttpHeaders().setContentType(PDF_CONTENT_TYPE));
    }

    /** Download bytes for the given blob path. Throws if missing. */
    public byte[] download(String blobPath) {
        return client(blobPath).downloadContent().toBytes();
    }

    /** Delete if present. Tolerant: returns false (never throws) on any error. */
    public boolean deleteIfExists(String blobPath) {
        try {
            return client(blobPath).deleteIfExists();
        } catch (Exception ex) {
            log.warn("Blob deleteIfExists failed for {}: {}", blobPath, ex.getMessage());
            return false;
        }
    }

    private BlobClient client(String blobPath) {
        BlobServiceClient svc = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        return svc.getBlobContainerClient(containerName).getBlobClient(blobPath);
    }
}
