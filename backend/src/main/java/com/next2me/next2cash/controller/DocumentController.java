package com.next2me.next2cash.controller;

import com.next2me.next2cash.repository.TransactionRepository;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.UserAccessService;
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
import java.time.OffsetDateTime;
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
    private final UserAccessService userAccessService;

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

    // -- POST /api/documents/upload --------------------------------------
    // Phase M.1: upload with auth + validation + auto-naming
    // - PDF only, max 10MB
    // - auto filename: [counterparty]_[docDate]_[seq].pdf
    // - appends blob path to transaction.blobFileIds under @Transactional
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> uploadDocument(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Integer transactionId,
            @RequestParam MultipartFile file) throws IOException {

        // 1. Resolve current user
        User user = userAccessService.getCurrentUser(authHeader);

        // 2. Load transaction + entity-access check
        var txnOpt = transactionRepository.findById(transactionId);
        if (txnOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error",   "transaction_not_found"
            ));
        }
        var txn = txnOpt.get();
        userAccessService.assertCanAccessEntity(user, txn.getEntityId());

        // 3. Validate file
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error",   "file_missing"
            ));
        }

        final long MAX_BYTES = 10L * 1024L * 1024L; // 10 MB
        if (file.getSize() > MAX_BYTES) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error",   "file_too_large",
                "maxBytes", MAX_BYTES
            ));
        }

        String contentType = file.getContentType();
        boolean pdfByType = contentType != null
            && contentType.equalsIgnoreCase("application/pdf");
        String origName = file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "";
        boolean pdfByExt = origName.toLowerCase().endsWith(".pdf");
        if (!pdfByType && !pdfByExt) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error",   "only_pdf_allowed",
                "received", contentType == null ? "" : contentType
            ));
        }

        // 4. Auto-generate filename: [counterparty]_[docDate]_[seq].pdf
        // Phase M.1.1: fallback chain counterparty -> account -> doc
        // (legacy data populates account, not counterparty)
        String rawName = (txn.getCounterparty() != null && !txn.getCounterparty().isBlank())
            ? txn.getCounterparty()
            : ((txn.getAccount() != null && !txn.getAccount().isBlank())
                ? txn.getAccount() : "doc");
        String counterparty = rawName;
        // Sanitize: remove whitespace, slashes, quotes, non-ASCII-friendly chars
        String safeCounterparty = counterparty
            .replaceAll("[\\s/\\\\:\"\'<>|?*,]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
        if (safeCounterparty.isEmpty()) safeCounterparty = "doc";
        if (safeCounterparty.length() > 40) {
            safeCounterparty = safeCounterparty.substring(0, 40);
        }

        String docDateStr = txn.getDocDate() != null
            ? txn.getDocDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Compute next seq = count of existing blobs for this txn + 1
        int seq = 1;
        String existingIds = txn.getBlobFileIds();
        if (existingIds != null && !existingIds.isBlank()) {
            seq = (int) Arrays.stream(existingIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).count() + 1;
        }

        String autoFileName = String.format("%s_%s_%d.pdf",
            safeCounterparty, docDateStr, seq);

        // 5. Build blob path (entityId/YYYY/MM/transactionId/filename)
        LocalDate pathDate = txn.getDocDate() != null
            ? txn.getDocDate() : LocalDate.now();
        String blobPath = String.format("%s/%d/%02d/%d/%s",
            txn.getEntityId(), pathDate.getYear(), pathDate.getMonthValue(),
            txn.getId(), autoFileName);

        // 6. Upload to Azure Blob
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .connectionString(blobConnectionString)
            .buildClient();

        BlobClient blobClient = blobServiceClient
            .getBlobContainerClient(containerName)
            .getBlobClient(blobPath);

        blobClient.upload(file.getInputStream(), file.getSize(), true);

        // 7. Append blob path to transaction.blobFileIds (under @Transactional)
        String updated = (existingIds == null || existingIds.isBlank())
            ? blobPath
            : existingIds + "," + blobPath;
        txn.setBlobFileIds(updated);
        txn.setUpdatedBy(user.getId());
        transactionRepository.save(txn);

        return ResponseEntity.ok(Map.of(
            "success",      true,
            "blobPath",     blobPath,
            "fileName",     autoFileName,
            "sizeBytes",    file.getSize(),
            "blobFileIds",  updated
        ));
    }

    // -- GET /api/documents/by-transaction/{id} ----------------------------
    // Returns attachments metadata + short-lived SAS download URLs (15 min).
    // Visible to all authenticated roles so viewers/accountants can inspect.
    @GetMapping("/by-transaction/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'VIEWER', 'ACCOUNTANT')")
    public ResponseEntity<?> getDocumentsByTransaction(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id) {

        // SECURITY: resolve user and verify entity access
        User user = userAccessService.getCurrentUser(authHeader);

        var txnOpt = transactionRepository.findById(id);
        if (txnOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var txn = txnOpt.get();
        userAccessService.assertCanAccessEntity(user, txn.getEntityId());

        String blobIds = txn.getBlobFileIds();
        if (blobIds == null || blobIds.isBlank()) {
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "data",    java.util.List.of(),
                "total",   0
            ));
        }

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .connectionString(blobConnectionString)
            .buildClient();

        var containerClient = blobServiceClient.getBlobContainerClient(containerName);

        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

        for (String blobPath : blobIds.split(",")) {
            String trimmed = blobPath.trim();
            if (trimmed.isEmpty()) continue;

            try {
                BlobClient blobClient = containerClient.getBlobClient(trimmed);
                if (!blobClient.exists()) continue;

                // Short-lived SAS: read-only, 15 minutes
                BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
                OffsetDateTime expiry = OffsetDateTime.now().plusMinutes(15);

                BlobServiceSasSignatureValues sasValues =
                    new BlobServiceSasSignatureValues(expiry, permission);

                String sasToken = blobClient.generateSas(sasValues);
                String downloadUrl = blobClient.getBlobUrl() + "?" + sasToken;

                String fileName = trimmed.contains("/")
                    ? trimmed.substring(trimmed.lastIndexOf('/') + 1)
                    : trimmed;

                long sizeBytes = 0L;
                try {
                    sizeBytes = blobClient.getProperties().getBlobSize();
                } catch (Exception ignored) { /* size optional */ }

                java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("fileName",    fileName);
                entry.put("blobPath",    trimmed);
                entry.put("sizeBytes",   sizeBytes);
                entry.put("downloadUrl", downloadUrl);
                result.add(entry);

            } catch (Exception e) {
                // Skip individual broken blobs but keep the rest
            }
        }

        return ResponseEntity.ok(java.util.Map.of(
            "success", true,
            "data",    result,
            "total",   result.size()
        ));
    }
}
