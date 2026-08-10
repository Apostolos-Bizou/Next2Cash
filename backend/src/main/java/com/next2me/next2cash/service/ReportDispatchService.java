package com.next2me.next2cash.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.next2me.next2cash.dto.ReportDispatchCreateRequest;
import com.next2me.next2cash.model.ReportDispatch;
import com.next2me.next2cash.model.ReportDispatchItem;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ReportDispatchItemRepository;
import com.next2me.next2cash.repository.ReportDispatchRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ReportDispatchService — S105, Level 2.
 *
 * Business logic for the Report Builder "dispatch archive". ALL validations
 * live here (never in the UI) — the frontend is a convenience, the service is
 * the gate. PDF generation is NOT part of this level: {@code blob_path} stays
 * null on create and is produced later (Level 4). See SPEC §5.
 *
 * Validations enforced on create (SPEC §3–4):
 *   1. caller can access the target entity                → 403
 *   2. every transaction belongs to that entity           → 400 (cross-entity vector)
 *   3. no PLANNED transaction is archived as sent          → 400
 *   4. no zero-amount transaction                          → 400
 *   5. declared section matches the amount's sign          → 400 (πρόσημο → ενότητα)
 *   6. non-empty transaction list                          → 400
 *   7. duplicate ids in the same body are deduped silently (PK would catch anyway)
 *   8. only recordStatus='active' transactions             → 400
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDispatchService {

    private static final String ACTIVE = "active";
    private static final String SECTION_INCOME = "INCOME";
    private static final String SECTION_EXPENSE = "EXPENSE";

    private final ReportDispatchRepository dispatchRepository;
    private final ReportDispatchItemRepository itemRepository;
    private final TransactionRepository transactionRepository;
    private final UserAccessService userAccessService;
    private final AuditLogService auditLogService;

    // Same blob config the production DocumentController uses. Default empty so
    // the bean never blocks startup; blob delete is only reached when a
    // dispatch actually has a blob_path (Level 4+), and is tolerant of failure.
    @Value("${next2cash.azure.blob.connection-string:}")
    private String blobConnectionString;
    @Value("${next2cash.azure.blob.container:next2cash-documents}")
    private String containerName;

    // ─── CREATE ───────────────────────────────────────────────────────────

    /**
     * Validate + persist a dispatch and its line items. Does NOT generate a PDF
     * (Level 2): blob_path stays null. Returns the saved dispatch.
     */
    @Transactional
    public ReportDispatch create(User currentUser, UUID entityId,
                                 ReportDispatchCreateRequest req, String ip) {
        // 1. Entity access → 403
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        if (req == null) {
            throw badRequest("Request body is required");
        }
        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        String recipient = req.getRecipient() == null ? "" : req.getRecipient().trim();
        if (title.isEmpty())     throw badRequest("title is required");
        if (recipient.isEmpty()) throw badRequest("recipient is required");

        // 6. Non-empty list → 400
        List<ReportDispatchCreateRequest.Item> items = req.getItems();
        if (items == null || items.isEmpty()) {
            throw badRequest("At least one transaction is required");
        }

        // 7. Dedup by transaction id silently (keep first declared section).
        Map<Integer, String> declaredSection = new HashMap<>();
        List<Integer> orderedIds = new ArrayList<>();
        for (ReportDispatchCreateRequest.Item it : items) {
            if (it == null || it.getTransactionId() == null) {
                throw badRequest("Each item must have a transactionId");
            }
            Integer txId = it.getTransactionId();
            if (!declaredSection.containsKey(txId)) {
                declaredSection.put(txId, normalizeSection(it.getSection()));
                orderedIds.add(txId);
            }
        }

        // Validate each transaction against the entity + business rules.
        for (Integer txId : orderedIds) {
            Transaction t = transactionRepository.findById(txId).orElse(null);

            // 2. Cross-entity vector: must exist AND belong to this entity → 400
            if (t == null || !entityId.equals(t.getEntityId())) {
                throw badRequest("Transaction " + txId + " does not belong to entity " + entityId);
            }
            // 8. recordStatus must be 'active' → 400
            if (!ACTIVE.equals(t.getRecordStatus())) {
                throw badRequest("Transaction " + txId + " is not active (recordStatus="
                        + t.getRecordStatus() + ")");
            }
            // 3. PLANNED is never archived as sent (null treated as ACTUAL) → 400
            if ("PLANNED".equalsIgnoreCase(t.getEntryMode())) {
                throw badRequest("Transaction " + txId + " is PLANNED and cannot be dispatched");
            }
            // 4. Zero amount → 400
            BigDecimal amount = t.getAmount();
            if (amount == null || amount.signum() == 0) {
                throw badRequest("Transaction " + txId + " has zero amount");
            }
            // 5. Declared section must match the sign (income→INCOME, expense→EXPENSE) → 400
            String expected = "income".equalsIgnoreCase(t.getType()) ? SECTION_INCOME : SECTION_EXPENSE;
            if (!expected.equals(declaredSection.get(txId))) {
                throw badRequest("Transaction " + txId + " is " + t.getType()
                        + " but was routed to section " + declaredSection.get(txId));
            }
        }

        // Persist header (blob_path null — PDF comes at Level 4).
        ReportDispatch dispatch = new ReportDispatch();
        dispatch.setEntityId(entityId);
        dispatch.setTitle(title);
        dispatch.setRecipient(recipient);
        dispatch.setSentDate(req.getSentDate() != null ? req.getSentDate() : LocalDate.now());
        dispatch.setNote(req.getNote());
        dispatch.setBlobPath(null);
        dispatch.setCreatedBy(currentUser.getId());
        ReportDispatch saved = dispatchRepository.save(dispatch);

        // Persist line items.
        List<ReportDispatchItem> toSave = new ArrayList<>(orderedIds.size());
        for (Integer txId : orderedIds) {
            toSave.add(new ReportDispatchItem(saved.getId(), txId));
        }
        itemRepository.saveAll(toSave);

        auditLogService.log(entityId, currentUser.getId(), currentUser.getUsername(),
                "DISPATCH_CREATE", "report_dispatches", saved.getId().toString(),
                "title=" + title + "; recipient=" + recipient + "; items=" + orderedIds.size(), ip);

        return saved;
    }

    // ─── DELETE ───────────────────────────────────────────────────────────

    /**
     * Delete a dispatch and its items (explicit item delete keeps H2 tests and
     * PostgreSQL identical; the DB FK also cascades). Tolerant blob delete.
     */
    @Transactional
    public void delete(User currentUser, UUID entityId, UUID dispatchId, String ip) {
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        ReportDispatch dispatch = dispatchRepository.findByIdAndEntityId(dispatchId, entityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatch not found"));

        List<ReportDispatchItem> items = itemRepository.findByIdDispatchId(dispatchId);
        int itemCount = items.size();

        // Blob delete only if one was ever produced (Level 4+). Tolerant of a
        // missing/already-gone blob — log and continue, never throw.
        String blobPath = dispatch.getBlobPath();
        boolean blobDeleted = false;
        if (blobPath != null && !blobPath.isBlank()) {
            blobDeleted = deleteBlobTolerant(blobPath);
        }

        itemRepository.deleteAll(items);
        dispatchRepository.delete(dispatch);

        auditLogService.log(entityId, currentUser.getId(), currentUser.getUsername(),
                "DISPATCH_DELETE", "report_dispatches", dispatchId.toString(),
                "items=" + itemCount + "; blob=" + (blobPath == null ? "none"
                        : (blobDeleted ? "deleted" : "delete_skipped_or_missing")), ip);
    }

    /** @return true if a blob was actually deleted; false if missing or on error. */
    private boolean deleteBlobTolerant(String blobPath) {
        if (blobConnectionString == null || blobConnectionString.isBlank()) {
            log.warn("Blob delete skipped for {} — no connection string configured", blobPath);
            return false;
        }
        try {
            BlobServiceClient client = new BlobServiceClientBuilder()
                    .connectionString(blobConnectionString)
                    .buildClient();
            BlobClient blob = client.getBlobContainerClient(containerName).getBlobClient(blobPath);
            boolean deleted = blob.deleteIfExists();
            if (!deleted) {
                log.warn("Blob {} was already gone — treating as deleted", blobPath);
            }
            return deleted;
        } catch (Exception ex) {
            log.warn("Tolerant blob delete failed for {}: {}", blobPath, ex.getMessage());
            return false;
        }
    }

    // ─── QUERIES ──────────────────────────────────────────────────────────

    /**
     * Archive listing, entity-scoped. Optional sent_date range and free-text q.
     * q matches title/recipient/note (case-insensitive) OR, if numeric, a
     * transaction id contained in the dispatch (SPEC §7 "search by tx id").
     */
    @Transactional(readOnly = true)
    public List<ReportDispatch> list(User currentUser, UUID entityId,
                                     LocalDate from, LocalDate to, String q) {
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        List<ReportDispatch> base = (from != null && to != null)
                ? dispatchRepository.findByEntityIdAndSentDateBetweenOrderBySentDateDescCreatedAtDesc(entityId, from, to)
                : dispatchRepository.findByEntityIdOrderBySentDateDescCreatedAtDesc(entityId);

        if (q == null || q.isBlank()) {
            return base;
        }
        String needle = q.trim().toLowerCase();
        Integer txId = parseIntOrNull(q.trim());

        List<ReportDispatch> out = new ArrayList<>();
        for (ReportDispatch d : base) {
            boolean textMatch = contains(d.getTitle(), needle)
                    || contains(d.getRecipient(), needle)
                    || contains(d.getNote(), needle);
            boolean idMatch = false;
            if (txId != null) {
                idMatch = itemRepository.findByIdDispatchId(d.getId()).stream()
                        .anyMatch(it -> txId.equals(it.getId().getTransactionId()));
            }
            if (textMatch || idMatch) {
                out.add(d);
            }
        }
        return out;
    }

    /** Recipient autocomplete (most recent first). */
    @Transactional(readOnly = true)
    public List<String> recipients(User currentUser, UUID entityId) {
        userAccessService.assertCanAccessEntity(currentUser, entityId);
        return dispatchRepository.findDistinctRecipientsByEntity(entityId);
    }

    /**
     * Batch dispatch-status for badges: given a set of transaction ids, returns
     * the subset that appears in at least one dispatch of THIS entity.
     */
    @Transactional(readOnly = true)
    public Set<Integer> dispatchStatus(User currentUser, UUID entityId, List<Integer> transactionIds) {
        userAccessService.assertCanAccessEntity(currentUser, entityId);
        if (transactionIds == null || transactionIds.isEmpty()) {
            return new HashSet<>();
        }
        Map<UUID, UUID> dispatchEntityCache = new HashMap<>();
        Set<Integer> dispatched = new LinkedHashSet<>();
        for (ReportDispatchItem it : itemRepository.findByIdTransactionIdIn(transactionIds)) {
            UUID dId = it.getId().getDispatchId();
            UUID dispatchEntity = dispatchEntityCache.computeIfAbsent(dId,
                    id -> dispatchRepository.findById(id).map(ReportDispatch::getEntityId).orElse(null));
            if (entityId.equals(dispatchEntity)) {
                dispatched.add(it.getId().getTransactionId());
            }
        }
        return dispatched;
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private String normalizeSection(String s) {
        if (s == null) return "";
        String up = s.trim().toUpperCase();
        return (SECTION_INCOME.equals(up) || SECTION_EXPENSE.equals(up)) ? up : "";
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lowerNeedle);
    }

    private static Integer parseIntOrNull(String s) {
        try { return Integer.valueOf(s); }
        catch (NumberFormatException e) { return null; }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
