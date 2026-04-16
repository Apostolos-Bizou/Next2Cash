package com.next2me.next2cash.controller;

import com.next2me.next2cash.repository.TransactionRepository;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.BlobClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final TransactionRepository transactionRepository;

    @Value("${next2cash.azure.blob.connection-string}")
    private String blobConnectionString;

    @Value("${next2cash.azure.blob.container}")
    private String containerName;

    // ── ZIP EXPORT (ΚΡΙΣΙΜΗ ΛΕΙΤΟΥΡΓΙΑ) ──────────────────────────────────────
    // GET /api/documents/export?entity_id=X&from=YYYY-MM-DD&to=YYYY-MM-DD
    // Returns: ACC_[ENTITY]_[FROM]_[TO].zip
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> exportDocumentsZip(
            @RequestParam UUID entityId,
            @RequestParam String from,
            @RequestParam String to) throws IOException {

        LocalDate dateFrom = LocalDate.parse(from);
        LocalDate dateTo   = LocalDate.parse(to);

        // Get all transactions with documents in date range
        var transactions = transactionRepository
            .findWithDocumentsByEntityAndDateRange(entityId, dateFrom, dateTo);

        if (transactions.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        // Build ZIP in memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .connectionString(blobConnectionString)
            .buildClient();

        int fileCount = 0;

        for (var txn : transactions) {
            if (txn.getBlobFileIds() == null || txn.getBlobFileIds().isBlank()) continue;

            List<String> blobPaths = Arrays.stream(txn.getBlobFileIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

            for (String blobPath : blobPaths) {
                try {
                    BlobClient blobClient = blobServiceClient
                        .getBlobContainerClient(containerName)
                        .getBlobClient(blobPath);

                    if (!blobClient.exists()) continue;

                    ByteArrayOutputStream blobStream = new ByteArrayOutputStream();
                    blobClient.downloadStream(blobStream);

                    // File name in ZIP: keep original blob filename
                    String fileName = blobPath.contains("/")
                        ? blobPath.substring(blobPath.lastIndexOf('/') + 1)
                        : blobPath;

                    ZipEntry entry = new ZipEntry(fileName);
                    zos.putNextEntry(entry);
                    zos.write(blobStream.toByteArray());
                    zos.closeEntry();
                    fileCount++;

                } catch (Exception e) {
                    // Skip failed files — continue with rest
                }
            }
        }

        zos.close();

        if (fileCount == 0) {
            return ResponseEntity.noContent().build();
        }

        // ZIP filename: ACC_[ENTITY]_[FROM]_[TO].zip
        String zipFileName = String.format("ACC_%s_%s_%s.zip",
            entityId.toString().substring(0, 8).toUpperCase(),
            from.replace("-", ""),
            to.replace("-", ""));

        byte[] zipBytes = baos.toByteArray();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + zipFileName + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(zipBytes.length)
            .body(zipBytes);
    }

    // ── UPLOAD DOCUMENT ───────────────────────────────────────────────────────
    // POST /api/documents/upload
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> uploadDocument(
            @RequestParam UUID entityId,
            @RequestParam Integer transactionId,
            @RequestParam String fileName,
            @RequestParam(defaultValue = "receipt") String docType,
            @RequestParam MultipartFile file) throws IOException {

        // Blob path: entity_id/year/month/txn_id/filename
        LocalDate now = LocalDate.now();
        String blobPath = String.format("%s/%d/%02d/%d/%s",
            entityId, now.getYear(), now.getMonthValue(),
            transactionId, fileName);

        // Upload to Azure Blob
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .connectionString(blobConnectionString)
            .buildClient();

        BlobClient blobClient = blobServiceClient
            .getBlobContainerClient(containerName)
            .getBlobClient(blobPath);

        blobClient.upload(file.getInputStream(), file.getSize(), true);

        // Update transaction blob_file_ids (append)
        transactionRepository.findById(transactionId).ifPresent(txn -> {
            String existing = txn.getBlobFileIds();
            String updated  = (existing == null || existing.isBlank())
                ? blobPath
                : existing + "," + blobPath;
            txn.setBlobFileIds(updated);
            transactionRepository.save(txn);
        });

        return ResponseEntity.ok(Map.of(
            "success",  true,
            "blobPath", blobPath,
            "fileName", fileName
        ));
    }
}
