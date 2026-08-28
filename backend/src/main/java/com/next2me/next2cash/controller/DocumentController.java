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
        int skippedCount = 0;
        // S106: duplicate basenames used to throw ZipException inside the catch
        // and get SILENTLY dropped — now they are deduplicated (_1, _2, ...).
        java.util.Set<String> usedEntryNames = new java.util.HashSet<>();

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

                    if (!blobClient.exists()) { skippedCount++; continue; }

                    ByteArrayOutputStream blobStream = new ByteArrayOutputStream();
                    blobClient.downloadStream(blobStream);

                    // File name in ZIP: the blob basename (the accountant needs the
                    // entity-number prefix for bookkeeping — no display-strip here),
                    // deduplicated so same-named files never collide.
                    String fileName = blobPath.contains("/")
                        ? blobPath.substring(blobPath.lastIndexOf('/') + 1)
                        : blobPath;
                    String entryName = uniqueZipEntryName(fileName, usedEntryNames);

                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    zos.write(blobStream.toByteArray());
                    zos.closeEntry();
                    fileCount++;

                } catch (Exception e) {
                    // Skip failed files — continue with rest, but COUNT them
                    // so the omission is visible to the caller.
                    skippedCount++;
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
            // S106: visible counters (CORS-exposed) — the UI can tell the user
            // exactly how many files made it in and how many were skipped.
            .header("X-Zip-Files", String.valueOf(fileCount))
            .header("X-Zip-Skipped", String.valueOf(skippedCount))
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(zipBytes.length)
            .body(zipBytes);
    }

    /** Dedup a ZIP entry name: "a.pdf" → "a.pdf", then "a_1.pdf", "a_2.pdf"...
     *  Package-private static for unit testing. */
    static String uniqueZipEntryName(String name, java.util.Set<String> used) {
        String candidate = name;
        int n = 1;
        while (used.contains(candidate)) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext  = dot > 0 ? name.substring(dot) : "";
            candidate = base + "_" + (n++) + ext;
        }
        used.add(candidate);
        return candidate;
    }

    /** Display name for an attachment: blob basename with the auto-numbering
     *  prefix "N - " stripped (display only — nothing moves in Blob storage).
     *  Guarded: if stripping leaves nothing meaningful, the original stays.
     *  "Πληρωμή #N - ..." names do not match and stay as-is.
     *  Package-private static for unit testing. */
    static String displayFileName(String blobPath) {
        if (blobPath == null) return null;
        String base = blobPath.contains("/")
            ? blobPath.substring(blobPath.lastIndexOf('/') + 1)
            : blobPath;
        String stripped = base.replaceFirst("^\\d+ - ", "");
        if (stripped.isBlank() || stripped.startsWith(".")) return base;
        return stripped;
    }

    /** RFC 5987 percent-encoding (UTF-8) for Content-Disposition filename*. */
    static String rfc5987Encode(String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte bch : bytes) {
            int c = bch & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '!' || c == '#' || c == '$' || c == '&' || c == '+' || c == '-'
                    || c == '.' || c == '^' || c == '_' || c == '`' || c == '|' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
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
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String customFileName) throws IOException {

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

        // Phase M.2.0: allow PDF + JPG + JPEG + PNG (parity with legacy system)
        String contentType = file.getContentType();
        String origName = file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "";
        String lowerName = origName.toLowerCase();

        boolean allowedByType = contentType != null && (
               contentType.equalsIgnoreCase("application/pdf")
            || contentType.equalsIgnoreCase("image/jpeg")
            || contentType.equalsIgnoreCase("image/jpg")
            || contentType.equalsIgnoreCase("image/png")
        );
        boolean allowedByExt = lowerName.endsWith(".pdf")
            || lowerName.endsWith(".jpg")
            || lowerName.endsWith(".jpeg")
            || lowerName.endsWith(".png");

        if (!allowedByType && !allowedByExt) {
            return ResponseEntity.badRequest().body(Map.of(
                "success",  false,
                "error",    "unsupported_file_type",
                "allowed",  "pdf,jpg,jpeg,png",
                "received", contentType == null ? "" : contentType
            ));
        }

        // Determine extension for auto-filename (PDF as safe default)
        String fileExt = "pdf";
        if (lowerName.endsWith(".jpeg"))       fileExt = "jpeg";
        else if (lowerName.endsWith(".jpg"))   fileExt = "jpg";
        else if (lowerName.endsWith(".png"))   fileExt = "png";

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

        String autoFileName = String.format("%s_%s_%d.%s",
            safeCounterparty, docDateStr, seq, fileExt);

        // Phase M.2.2: custom filename override (if provided by frontend)
        if (customFileName != null && !customFileName.isBlank()) {
            // Sanitize custom name: remove dangerous chars, keep extension
            String safeName = customFileName.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
            // Ensure it has the correct extension
            if (!safeName.toLowerCase().endsWith("." + fileExt)) {
                safeName = safeName.replaceAll("\\.[^.]*$", "") + "." + fileExt;
            }
            if (safeName.length() > 100) {
                safeName = safeName.substring(0, 96) + "." + fileExt;
            }
            if (!safeName.isBlank() && !safeName.equals("." + fileExt)) {
                autoFileName = safeName;
            }
        }

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

                // S106: clean display name — basename with the "N - " numbering
                // prefix stripped (display only; the blob itself is untouched).
                String fileName = displayFileName(trimmed);

                // Short-lived SAS: read-only, 15 minutes.
                // S106: response Content-Disposition override (rscd) — without it
                // the SDK's %2F-encoded blob URL makes browsers treat the WHOLE
                // path as one segment, so saving from the PDF viewer produced
                // names like "…_2026_06_90576_…pdf" ('/'→'_'). "inline" keeps
                // the preview tab behavior; filename* carries Greek intact.
                BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
                OffsetDateTime expiry = OffsetDateTime.now().plusMinutes(15);

                BlobServiceSasSignatureValues sasValues =
                    new BlobServiceSasSignatureValues(expiry, permission)
                        .setContentDisposition("inline; filename*=UTF-8''" + rfc5987Encode(fileName));

                String sasToken = blobClient.generateSas(sasValues);
                String downloadUrl = blobClient.getBlobUrl() + "?" + sasToken;

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

    // -- DELETE /api/documents/by-transaction/{id} ------------------------
    // Hard delete: removes a single blob from Azure AND removes its path
    // from transactions.blob_file_ids. ADMIN + USER only.
    //
    // Body: { "blobPath": "<existing path inside container>" }
    // Returns: { success, blobFileIds (updated), removed }
    @DeleteMapping("/by-transaction/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteDocumentByTransaction(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) {

        // 1. Resolve current user
        User user = userAccessService.getCurrentUser(authHeader);

        // 2. Validate input
        String blobPath = body == null ? null : body.get("blobPath");
        if (blobPath == null || blobPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error",   "blob_path_missing"
            ));
        }
        blobPath = blobPath.trim();

        // 3. Load transaction + entity-access check
        var txnOpt = transactionRepository.findById(id);
        if (txnOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error",   "transaction_not_found"
            ));
        }
        var txn = txnOpt.get();
        userAccessService.assertCanAccessEntity(user, txn.getEntityId());

        // 4. Verify blobPath actually belongs to this transaction
        String existing = txn.getBlobFileIds();
        if (existing == null || existing.isBlank()) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error",   "blob_not_attached"
            ));
        }
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (String p : existing.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) paths.add(t);
        }
        if (!paths.contains(blobPath)) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error",   "blob_not_attached"
            ));
        }

        // 5. Delete blob from Azure FIRST (fail fast).
        // If Azure is down or path is corrupt, abort before touching DB.
        try {
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(blobConnectionString)
                .buildClient();

            BlobClient blobClient = blobServiceClient
                .getBlobContainerClient(containerName)
                .getBlobClient(blobPath);

            // deleteIfExists returns false if blob is already gone — that
            // is fine, treat as success and proceed to DB cleanup so we
            // remove the dangling reference.
            blobClient.deleteIfExists();
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error",   "blob_delete_failed",
                "detail",  ex.getMessage() == null ? "" : ex.getMessage()
            ));
        }

        // 6. Rebuild blob_file_ids without the deleted path, save txn
        paths.remove(blobPath);
        String updated = String.join(",", paths);
        txn.setBlobFileIds(updated.isEmpty() ? null : updated);
        txn.setUpdatedBy(user.getId());
        transactionRepository.save(txn);

        return ResponseEntity.ok(Map.of(
            "success",     true,
            "removed",     blobPath,
            "blobFileIds", updated
        ));
    }
}
