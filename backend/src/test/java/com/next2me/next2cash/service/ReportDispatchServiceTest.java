package com.next2me.next2cash.service;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.dto.ReportDispatchCreateRequest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.ReportDispatch;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ReportDispatchItemRepository;
import com.next2me.next2cash.repository.ReportDispatchRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * S105 Level 2 — ReportDispatchService validations + queries.
 *
 * Integration tests (Spring context + H2). One test per SPEC validation (8),
 * plus delete-cascade, list filtering, dispatch-status batch, happy path, and
 * recipient autocomplete = 13 tests.
 */
class ReportDispatchServiceTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ReportDispatchRepository dispatchRepository;
    @Autowired private ReportDispatchItemRepository itemRepository;
    @Autowired private ReportDispatchService service;

    // Blob store is mocked: the dummy test connection string cannot reach Azure.
    @MockBean private ReportDispatchBlobStore blobStore;

    private User admin;
    private CompanyEntity entity;

    @BeforeEach
    void setup() {
        admin = tdb.createAdmin("apostolos");   // admin sees ALL entities
        entity = tdb.createEntity("N2M", "Next2Me");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private Transaction txn(UUID entityId, String type, String amount,
                            String entryMode, String recordStatus) {
        Transaction t = new Transaction();
        t.setEntityId(entityId);
        t.setType(type);
        t.setDocDate(LocalDate.of(2026, 1, 15));
        t.setDescription(type + " " + amount);
        t.setAmount(new BigDecimal(amount));
        t.setAmountPaid(BigDecimal.ZERO);
        t.setAmountRemaining(new BigDecimal(amount));
        t.setEntryMode(entryMode);
        t.setRecordStatus(recordStatus);
        return transactionRepository.save(t);
    }

    /** Active, ACTUAL expense of 100. */
    private Transaction expense(UUID entityId) {
        return txn(entityId, "expense", "100.00", "ACTUAL", "active");
    }

    private ReportDispatchCreateRequest.Item item(Integer txId, String section) {
        ReportDispatchCreateRequest.Item i = new ReportDispatchCreateRequest.Item();
        i.setTransactionId(txId);
        i.setSection(section);
        return i;
    }

    private ReportDispatchCreateRequest req(String title, String recipient,
                                            LocalDate sent, ReportDispatchCreateRequest.Item... items) {
        ReportDispatchCreateRequest r = new ReportDispatchCreateRequest();
        r.setTitle(title);
        r.setRecipient(recipient);
        r.setSentDate(sent);
        r.setItems(new ArrayList<>(Arrays.asList(items)));
        return r;
    }

    private ResponseStatusException createExpectingError(User u, UUID entityId,
                                                         ReportDispatchCreateRequest r) {
        return assertThrows(ResponseStatusException.class,
                () -> service.create(u, entityId, r, null));
    }

    // ─── 1. Entity access → 403 ───────────────────────────────────────────

    @Test
    void create_denies_whenUserCannotAccessEntity() {
        User viewer = tdb.createViewer("simos"); // no assignment → sees nothing
        Transaction e = expense(entity.getId());
        ReportDispatchCreateRequest r = req("T", "Λογιστήριο", LocalDate.now(),
                item(e.getId(), "EXPENSE"));

        ResponseStatusException ex = createExpectingError(viewer, entity.getId(), r);
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ─── 2. Cross-entity transaction → 400 ────────────────────────────────

    @Test
    void create_rejects_crossEntityTransaction() {
        CompanyEntity other = tdb.createEntity("HOUSE", "House");
        Transaction foreign = expense(other.getId()); // belongs to 'other'
        ReportDispatchCreateRequest r = req("T", "Λογιστήριο", LocalDate.now(),
                item(foreign.getId(), "EXPENSE"));

        ResponseStatusException ex = createExpectingError(admin, entity.getId(), r);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("does not belong"));
    }

    // ─── 3. PLANNED → 400 ─────────────────────────────────────────────────

    @Test
    void create_rejects_plannedTransaction() {
        Transaction planned = txn(entity.getId(), "expense", "100.00", "PLANNED", "active");
        ReportDispatchCreateRequest r = req("T", "Λογιστήριο", LocalDate.now(),
                item(planned.getId(), "EXPENSE"));

        ResponseStatusException ex = createExpectingError(admin, entity.getId(), r);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("PLANNED"));
    }

    // ─── 4. Zero amount → 400 ─────────────────────────────────────────────

    @Test
    void create_rejects_zeroAmount() {
        Transaction zero = txn(entity.getId(), "expense", "0.00", "ACTUAL", "active");
        ReportDispatchCreateRequest r = req("T", "Λογιστήριο", LocalDate.now(),
                item(zero.getId(), "EXPENSE"));

        ResponseStatusException ex = createExpectingError(admin, entity.getId(), r);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("zero amount"));
    }

    // ─── 5. Sign / section mismatch → 400 ─────────────────────────────────

    @Test
    void create_rejects_sectionSignMismatch() {
        Transaction e = expense(entity.getId()); // expense
        ReportDispatchCreateRequest r = req("T", "Λογιστήριο", LocalDate.now(),
                item(e.getId(), "INCOME")); // declared INCOME → mismatch

        ResponseStatusException ex = createExpectingError(admin, entity.getId(), r);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("section"));
    }

    // ─── 6. Empty list → 400 ──────────────────────────────────────────────

    @Test
    void create_rejects_emptyItemList() {
        ReportDispatchCreateRequest r = req("T", "Λογιστήριο", LocalDate.now());
        ResponseStatusException ex = createExpectingError(admin, entity.getId(), r);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // ─── 7. Duplicate ids deduped silently ────────────────────────────────

    @Test
    void create_dedupsDuplicateIds() {
        Transaction e = expense(entity.getId());
        ReportDispatchCreateRequest r = req("T", "Λογιστήριο", LocalDate.now(),
                item(e.getId(), "EXPENSE"), item(e.getId(), "EXPENSE"));

        ReportDispatch saved = service.create(admin, entity.getId(), r, null);
        assertEquals(1, itemRepository.findByIdDispatchId(saved.getId()).size());
    }

    // ─── 8. Non-active recordStatus → 400 ─────────────────────────────────

    @Test
    void create_rejects_nonActiveTransaction() {
        Transaction voided = txn(entity.getId(), "expense", "100.00", "ACTUAL", "void");
        ReportDispatchCreateRequest r = req("T", "Λογιστήριο", LocalDate.now(),
                item(voided.getId(), "EXPENSE"));

        ResponseStatusException ex = createExpectingError(admin, entity.getId(), r);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not active"));
    }

    // ─── 9. Delete cascades to items ──────────────────────────────────────

    @Test
    void delete_cascadesItems() {
        Transaction a = expense(entity.getId());
        Transaction b = expense(entity.getId());
        ReportDispatch saved = service.create(admin, entity.getId(),
                req("T", "Λογιστήριο", LocalDate.now(),
                        item(a.getId(), "EXPENSE"), item(b.getId(), "EXPENSE")), null);
        assertEquals(2, itemRepository.findByIdDispatchId(saved.getId()).size());

        service.delete(admin, entity.getId(), saved.getId(), null);

        assertTrue(dispatchRepository.findById(saved.getId()).isEmpty());
        assertTrue(itemRepository.findByIdDispatchId(saved.getId()).isEmpty());
    }

    // ─── 10. List filters by entity / from-to / q ─────────────────────────

    @Test
    void list_filtersByEntityFromToAndQ() {
        // Two dispatches in this entity on different dates + recipients.
        Transaction e1 = expense(entity.getId());
        ReportDispatch jan = service.create(admin, entity.getId(),
                req("Απολογισμός Ιανουαρίου", "Λεωνίδας", LocalDate.of(2026, 1, 31),
                        item(e1.getId(), "EXPENSE")), null);
        Transaction e2 = expense(entity.getId());
        ReportDispatch mar = service.create(admin, entity.getId(),
                req("Report Μαρτίου", "Σίμος", LocalDate.of(2026, 3, 31),
                        item(e2.getId(), "EXPENSE")), null);

        // A dispatch in another entity must never appear.
        CompanyEntity other = tdb.createEntity("HOUSE", "House");
        Transaction eo = expense(other.getId());
        service.create(admin, other.getId(),
                req("Ξένο", "Άλλος", LocalDate.of(2026, 2, 15), item(eo.getId(), "EXPENSE")), null);

        // Entity isolation.
        assertEquals(2, service.list(admin, entity.getId(), null, null, null).size());

        // Date range → only January.
        List<ReportDispatch> q1 = service.list(admin, entity.getId(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);
        assertEquals(1, q1.size());
        assertEquals(jan.getId(), q1.get(0).getId());

        // Free-text q on title.
        List<ReportDispatch> q2 = service.list(admin, entity.getId(), null, null, "μαρτίου");
        assertEquals(1, q2.size());
        assertEquals(mar.getId(), q2.get(0).getId());

        // q by transaction id finds the dispatch containing it.
        List<ReportDispatch> q3 = service.list(admin, entity.getId(), null, null, String.valueOf(e1.getId()));
        assertEquals(1, q3.size());
        assertEquals(jan.getId(), q3.get(0).getId());
    }

    // ─── 11. dispatch-status batch, mixed ids ─────────────────────────────

    @Test
    void dispatchStatus_returnsOnlyDispatchedForMixedIds() {
        Transaction a = expense(entity.getId()); // dispatched
        Transaction b = expense(entity.getId()); // dispatched
        Transaction c = expense(entity.getId()); // NOT dispatched
        service.create(admin, entity.getId(),
                req("T", "Λογιστήριο", LocalDate.now(),
                        item(a.getId(), "EXPENSE"), item(b.getId(), "EXPENSE")), null);

        Set<Integer> status = service.dispatchStatus(admin, entity.getId(),
                Arrays.asList(a.getId(), b.getId(), c.getId()));

        assertEquals(2, status.size());
        assertTrue(status.contains(a.getId()));
        assertTrue(status.contains(b.getId()));
        assertFalse(status.contains(c.getId()));
    }

    // ─── 12. Happy path: renders + uploads + persists with blob_path ───────

    @Test
    void create_happyPath_persistsWithBlobPath() {
        Transaction inc = txn(entity.getId(), "income", "250.00", "ACTUAL", "active");
        Transaction exp = expense(entity.getId());
        ReportDispatch saved = service.create(admin, entity.getId(),
                req("Μικτό Report", "Λογιστήριο", LocalDate.of(2026, 5, 1),
                        item(inc.getId(), "INCOME"), item(exp.getId(), "EXPENSE")), null);

        assertNotNull(saved.getId());
        assertNotNull(saved.getBlobPath(), "Level 4 uploads a PDF and stores its path");
        assertTrue(saved.getBlobPath().startsWith("dispatches/N2M/2026/05/"));
        assertTrue(saved.getBlobPath().endsWith(".pdf"));
        assertEquals(entity.getId(), saved.getEntityId());
        assertEquals(admin.getId(), saved.getCreatedBy());
        assertEquals(2, itemRepository.findByIdDispatchId(saved.getId()).size());
        // Upload happened exactly once, before the row existed.
        verify(blobStore, times(1)).upload(anyString(), any(byte[].class));
    }

    // ─── 14. preview writes nothing to DB or Blob ──────────────────────────

    @Test
    void preview_writesNothingToDbOrBlob() {
        Transaction e = expense(entity.getId());
        byte[] pdf = service.preview(admin, entity.getId(),
                req("Preview", "Λογιστήριο", LocalDate.of(2026, 5, 1), item(e.getId(), "EXPENSE")));

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertEquals(0, dispatchRepository.count(), "preview must not INSERT a dispatch");
        assertEquals(0, itemRepository.count(), "preview must not INSERT items");
        verify(blobStore, never()).upload(anyString(), any(byte[].class));
    }

    // ─── 15. upload failure → no row in the database ───────────────────────

    @Test
    void create_uploadFailure_leavesNoRow() {
        doThrow(new RuntimeException("azure down"))
                .when(blobStore).upload(anyString(), any(byte[].class));

        Transaction e = expense(entity.getId());
        assertThrows(RuntimeException.class, () -> service.create(admin, entity.getId(),
                req("T", "Λογιστήριο", LocalDate.now(), item(e.getId(), "EXPENSE")), null));

        assertEquals(0, dispatchRepository.count(), "no dispatch may exist after upload failure");
        assertEquals(0, itemRepository.count(), "no items may exist after upload failure");
    }

    // ─── 13. Recipient autocomplete, distinct + most recent first ─────────

    @Test
    void recipients_returnsDistinctMostRecentFirst() {
        // Insert directly to control createdAt ordering deterministically.
        seedDispatch(entity.getId(), "Λεωνίδας", LocalDate.of(2026, 1, 10),
                java.time.LocalDateTime.of(2026, 1, 10, 9, 0));
        seedDispatch(entity.getId(), "Σίμος", LocalDate.of(2026, 2, 10),
                java.time.LocalDateTime.of(2026, 2, 10, 9, 0));
        seedDispatch(entity.getId(), "Λεωνίδας", LocalDate.of(2026, 3, 10),
                java.time.LocalDateTime.of(2026, 3, 10, 9, 0)); // most recent overall

        List<String> recipients = service.recipients(admin, entity.getId());
        assertEquals(List.of("Λεωνίδας", "Σίμος"), recipients); // distinct, most-recent first
    }

    private void seedDispatch(UUID entityId, String recipient, LocalDate sent,
                              java.time.LocalDateTime createdAt) {
        ReportDispatch d = new ReportDispatch();
        d.setId(UUID.randomUUID());   // id is application-assigned (Level 4)
        d.setEntityId(entityId);
        d.setTitle("seed");
        d.setRecipient(recipient);
        d.setSentDate(sent);
        d.setCreatedBy(admin.getId());
        d.setCreatedAt(createdAt);
        dispatchRepository.save(d);
    }
}
