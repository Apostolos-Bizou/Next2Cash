package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.ReportDispatchCreateRequest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.ReportDispatch;
import com.next2me.next2cash.model.ReportDispatchItem;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.CompanyEntityRepository;
import com.next2me.next2cash.repository.ReportDispatchItemRepository;
import com.next2me.next2cash.repository.ReportDispatchRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ReportDispatchService — S105.
 *
 * ALL validations live here (never in the UI). Level 4 adds PDF generation:
 * create() now renders a PDF, uploads it to Azure Blob, and only THEN inserts
 * the row (never INSERT before upload). preview() runs the same validations and
 * renders the PDF but writes nothing to DB or Blob.
 *
 * Validations on create/preview (SPEC §3–4):
 *   1. caller can access the target entity                → 403
 *   2. every transaction belongs to that entity           → 400 (cross-entity vector)
 *   3. no PLANNED transaction                              → 400
 *   4. no zero-amount transaction                          → 400
 *   5. declared section matches the amount's sign          → 400
 *   6. non-empty transaction list                          → 400
 *   7. duplicate ids deduped silently
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
    private final CompanyEntityRepository companyEntityRepository;
    private final UserAccessService userAccessService;
    private final AuditLogService auditLogService;
    private final ReportDispatchPdfService pdfService;
    private final ReportDispatchBlobStore blobStore;

    // ─── CREATE (render → upload → insert) ──────────────────────────────────

    @Transactional
    public ReportDispatch create(User currentUser, UUID entityId,
                                 ReportDispatchCreateRequest req, String ip) {
        List<Transaction> ordered = validate(currentUser, entityId, req);

        String title = req.getTitle().trim();
        String recipient = req.getRecipient().trim();
        LocalDate sentDate = req.getSentDate() != null ? req.getSentDate() : LocalDate.now();

        // 1. Render the PDF.
        byte[] pdf = pdfService.render(title, recipient, sentDate, ordered);

        // 2. Upload FIRST — id generated up front so the blob path can embed it.
        UUID dispatchId = UUID.randomUUID();
        String blobPath = buildBlobPath(entityId, sentDate, dispatchId);
        blobStore.upload(blobPath, pdf); // throws on failure → no INSERT below

        // 3. Only now insert the header + items.
        ReportDispatch dispatch = new ReportDispatch();
        dispatch.setId(dispatchId);
        dispatch.setEntityId(entityId);
        dispatch.setTitle(title);
        dispatch.setRecipient(recipient);
        dispatch.setSentDate(sentDate);
        dispatch.setNote(req.getNote());
        dispatch.setBlobPath(blobPath);
        dispatch.setCreatedBy(currentUser.getId());
        ReportDispatch saved = dispatchRepository.save(dispatch);

        List<ReportDispatchItem> toSave = new ArrayList<>(ordered.size());
        for (Transaction t : ordered) {
            toSave.add(new ReportDispatchItem(saved.getId(), t.getId()));
        }
        itemRepository.saveAll(toSave);

        auditLogService.log(entityId, currentUser.getId(), currentUser.getUsername(),
                "DISPATCH_CREATE", "report_dispatches", saved.getId().toString(),
                "title=" + title + "; recipient=" + recipient + "; items=" + ordered.size(), ip);

        return saved;
    }

    // ─── PREVIEW (render only — no DB, no Blob) ──────────────────────────────

    @Transactional(readOnly = true)
    public byte[] preview(User currentUser, UUID entityId, ReportDispatchCreateRequest req) {
        List<Transaction> ordered = validate(currentUser, entityId, req);
        String title = req.getTitle().trim();
        String recipient = req.getRecipient().trim();
        LocalDate sentDate = req.getSentDate() != null ? req.getSentDate() : LocalDate.now();
        return pdfService.render(title, recipient, sentDate, ordered);
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    @Transactional
    public void delete(User currentUser, UUID entityId, UUID dispatchId, String ip) {
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        ReportDispatch dispatch = dispatchRepository.findByIdAndEntityId(dispatchId, entityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatch not found"));

        List<ReportDispatchItem> items = itemRepository.findByIdDispatchId(dispatchId);
        int itemCount = items.size();

        String blobPath = dispatch.getBlobPath();
        boolean blobDeleted = false;
        if (blobPath != null && !blobPath.isBlank()) {
            blobDeleted = blobStore.deleteIfExists(blobPath); // tolerant: never throws
        }

        itemRepository.deleteAll(items);
        dispatchRepository.delete(dispatch);

        auditLogService.log(entityId, currentUser.getId(), currentUser.getUsername(),
                "DISPATCH_DELETE", "report_dispatches", dispatchId.toString(),
                "items=" + itemCount + "; blob=" + (blobPath == null ? "none"
                        : (blobDeleted ? "deleted" : "delete_skipped_or_missing")), ip);
    }

    /** Download the stored PDF bytes for a dispatch. Caller must scope access first. */
    @Transactional(readOnly = true)
    public byte[] downloadPdf(ReportDispatch dispatch) {
        if (dispatch.getBlobPath() == null || dispatch.getBlobPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "pdf_not_generated");
        }
        return blobStore.download(dispatch.getBlobPath());
    }

    // ─── QUERIES ──────────────────────────────────────────────────────────────

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
        String needle = q.trim().toLowerCase(Locale.ROOT);
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

    @Transactional(readOnly = true)
    public List<String> recipients(User currentUser, UUID entityId) {
        userAccessService.assertCanAccessEntity(currentUser, entityId);
        return dispatchRepository.findDistinctRecipientsByEntity(entityId);
    }

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

    // ─── Validation (shared by create + preview) ────────────────────────────

    /**
     * Runs all create/preview validations and returns the validated transactions
     * in the (deduped) order they were declared. Throws 403/400 on any failure.
     */
    private List<Transaction> validate(User currentUser, UUID entityId, ReportDispatchCreateRequest req) {
        // 1. Entity access → 403
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        if (req == null) throw badRequest("Request body is required");
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

        List<Transaction> ordered = new ArrayList<>(orderedIds.size());
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
            // 3. PLANNED never archived as sent → 400
            if ("PLANNED".equalsIgnoreCase(t.getEntryMode())) {
                throw badRequest("Transaction " + txId + " is PLANNED and cannot be dispatched");
            }
            // 4. Zero amount → 400
            BigDecimal amount = t.getAmount();
            if (amount == null || amount.signum() == 0) {
                throw badRequest("Transaction " + txId + " has zero amount");
            }
            // 5. Declared section must match the sign → 400
            String expected = "income".equalsIgnoreCase(t.getType()) ? SECTION_INCOME : SECTION_EXPENSE;
            if (!expected.equals(declaredSection.get(txId))) {
                throw badRequest("Transaction " + txId + " is " + t.getType()
                        + " but was routed to section " + declaredSection.get(txId));
            }
            ordered.add(t);
        }
        return ordered;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String buildBlobPath(UUID entityId, LocalDate sentDate, UUID dispatchId) {
        String entityCode = companyEntityRepository.findById(entityId)
                .map(CompanyEntity::getCode).orElse("UNKNOWN");
        return String.format(Locale.ROOT, "dispatches/%s/%04d/%02d/%s.pdf",
                entityCode, sentDate.getYear(), sentDate.getMonthValue(), dispatchId);
    }

    private String normalizeSection(String s) {
        if (s == null) return "";
        String up = s.trim().toUpperCase(Locale.ROOT);
        return (SECTION_INCOME.equals(up) || SECTION_EXPENSE.equals(up)) ? up : "";
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private static Integer parseIntOrNull(String s) {
        try { return Integer.valueOf(s); }
        catch (NumberFormatException e) { return null; }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
