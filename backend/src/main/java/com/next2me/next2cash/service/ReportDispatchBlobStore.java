package com.next2me.next2cash.service;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Thin wrapper around the production Azure Blob container used for Report
 * dispatch PDFs and attachment ZIPs. Uses the SAME connection string +
 * container as DocumentController (next2cash-documents). No new resource.
 *
 * Separated into its own bean so tests can @MockBean it (the dummy test
 * connection string cannot reach real Azure — same reason DocumentController's
 * upload happy-path is not integration-tested).
 */
@Service
@Slf4j
public class ReportDispatchBlobStore {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String ZIP_CONTENT_TYPE = "application/zip";

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

    /** Stream a blob's content into the given OutputStream (does not close it). */
    public void downloadTo(String blobPath, OutputStream out) {
        client(blobPath).downloadStream(out);
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

    /**
     * Stream-zip the given source blobs into destZipPath and upload the ZIP.
     * NEVER buffers all content in memory: the ZIP is built on a temp file on
     * disk and uploaded with uploadFromFile (a 40-transaction dispatch of
     * scanned invoices is easily 100+ MB).
     *
     * Per-source failures are non-fatal (missing/unreadable blob → skipped and
     * reported in {@link ZipResult#failed()}). If NOTHING was included, uploads
     * nothing and returns included=0 — we never create an empty ZIP.
     */
    public ZipResult zipAndUpload(String destZipPath, List<String> sourcePaths) {
        List<String> failed = new ArrayList<>();
        if (sourcePaths == null || sourcePaths.isEmpty()) {
            return new ZipResult(0, failed);
        }
        Path tmp = null;
        int included = 0;
        try {
            tmp = Files.createTempFile("dispatch-docs-", ".zip");
            Set<String> usedNames = new HashSet<>();
            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                for (String src : sourcePaths) {
                    if (src == null || src.isBlank()) continue;
                    BlobClient c = client(src);
                    boolean exists;
                    try { exists = c.exists(); } catch (Exception ex) { exists = false; }
                    if (!exists) {
                        failed.add(src);
                        log.warn("docs-zip: source blob missing, skipped: {}", src);
                        continue;
                    }
                    try {
                        zos.putNextEntry(new ZipEntry(uniqueEntryName(src, usedNames)));
                        c.downloadStream(zos);   // streams blob → zip entry, no full buffer
                        zos.closeEntry();
                        included++;
                    } catch (Exception ex) {
                        failed.add(src);
                        log.warn("docs-zip: failed to add blob {}: {}", src, ex.getMessage());
                        try { zos.closeEntry(); } catch (Exception ignore) { /* best-effort */ }
                    }
                }
            }
            if (included == 0) {
                return new ZipResult(0, failed);   // never upload an empty ZIP
            }
            BlobClient dest = client(destZipPath);
            dest.uploadFromFile(tmp.toString(), /* overwrite */ true);
            dest.setHttpHeaders(new BlobHttpHeaders().setContentType(ZIP_CONTENT_TYPE));
            return new ZipResult(included, failed);
        } catch (Exception e) {
            // Whole build/upload failed → treat as no docs (caller keeps it non-fatal).
            log.warn("docs-zip: build/upload failed for {}: {}", destZipPath, e.getMessage());
            return new ZipResult(0, failed);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignore) { /* best-effort */ }
            }
        }
    }

    /** Result of a docs-ZIP build: how many attachments made it in + which failed. */
    public record ZipResult(int included, List<String> failed) {}

    private static String uniqueEntryName(String blobPath, Set<String> used) {
        int slash = blobPath.lastIndexOf('/');
        String base = slash >= 0 ? blobPath.substring(slash + 1) : blobPath;
        if (base.isBlank()) base = "file";
        String name = base;
        int n = 1;
        while (used.contains(name)) {
            int dot = base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            String ext = dot > 0 ? base.substring(dot) : "";
            name = stem + "_" + (n++) + ext;
        }
        used.add(name);
        return name;
    }

    private BlobClient client(String blobPath) {
        BlobServiceClient svc = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        return svc.getBlobContainerClient(containerName).getBlobClient(blobPath);
    }
}
