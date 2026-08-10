package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.ReportDispatch;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.dto.ReportDispatchCreateRequest;
import com.next2me.next2cash.repository.ReportDispatchRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.service.ReportDispatchBlobStore;
import com.next2me.next2cash.service.ReportDispatchService;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * S105 Level 3 — ReportDispatchController security + behavior.
 *
 * INVARIANT #1 focus: VIEWER (Σίμος) and ACCOUNTANT are locked out of ALL
 * endpoints, GETs included. Plus role/scope matrix and PDF-404-while-null.
 */
class ReportDispatchControllerTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ReportDispatchService dispatchService;
    @Autowired private ReportDispatchRepository dispatchRepository;

    @MockBean private ReportDispatchBlobStore blobStore;

    private CompanyEntity next2me;
    private CompanyEntity house;
    private CompanyEntity next2meGroup;
    private User admin;

    @BeforeEach
    void setup() {
        CompanyEntity[] ents = tdb.createStandardEntities();
        next2me = ents[0];
        house = ents[1];
        next2meGroup = ents[2];
        admin = tdb.createAdmin("apostolos");
    }

    private Transaction expense(UUID entityId) {
        Transaction t = new Transaction();
        t.setEntityId(entityId);
        t.setType("expense");
        t.setDocDate(LocalDate.of(2026, 1, 15));
        t.setDescription("expense");
        t.setAmount(new BigDecimal("100.00"));
        t.setAmountPaid(BigDecimal.ZERO);
        t.setAmountRemaining(new BigDecimal("100.00"));
        t.setEntryMode("ACTUAL");
        t.setRecordStatus("active");
        return transactionRepository.save(t);
    }

    /** Create a dispatch (as admin, who can access all entities) and return it. */
    private ReportDispatch seedDispatch(UUID entityId) {
        Transaction e = expense(entityId);
        ReportDispatchCreateRequest.Item item = new ReportDispatchCreateRequest.Item();
        item.setTransactionId(e.getId());
        item.setSection("EXPENSE");
        ReportDispatchCreateRequest r = new ReportDispatchCreateRequest();
        r.setTitle("Report");
        r.setRecipient("Λογιστήριο");
        r.setSentDate(LocalDate.of(2026, 1, 31));
        r.setItems(List.of(item));
        return dispatchService.create(admin, entityId, r, null);
    }

    private String createBody(Integer txId) {
        return "{\"title\":\"T\",\"recipient\":\"Λογιστήριο\",\"sentDate\":\"2026-05-01\","
             + "\"items\":[{\"transactionId\":" + txId + ",\"section\":\"EXPENSE\"}]}";
    }

    // ─── VIEWER locked out of ALL (incl. GET) ──────────────────────────────

    @Test
    void viewer_getList_forbidden() throws Exception {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, next2meGroup);
        mockMvc.perform(get("/api/report-dispatches")
                .header("Authorization", tdb.bearerToken(simos))
                .param("entityId", next2meGroup.getId().toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void viewer_getById_forbidden() throws Exception {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, next2meGroup);
        ReportDispatch d = seedDispatch(next2meGroup.getId());
        mockMvc.perform(get("/api/report-dispatches/" + d.getId())
                .header("Authorization", tdb.bearerToken(simos)))
            .andExpect(status().isForbidden());
    }

    @Test
    void viewer_getRecipients_forbidden() throws Exception {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, next2meGroup);
        mockMvc.perform(get("/api/report-dispatches/recipients")
                .header("Authorization", tdb.bearerToken(simos))
                .param("entityId", next2meGroup.getId().toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void viewer_post_forbidden() throws Exception {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, next2meGroup);
        Transaction e = expense(next2meGroup.getId());
        mockMvc.perform(post("/api/report-dispatches")
                .header("Authorization", tdb.bearerToken(simos))
                .param("entityId", next2meGroup.getId().toString())
                .contentType("application/json")
                .content(createBody(e.getId())))
            .andExpect(status().isForbidden());
    }

    // ─── ACCOUNTANT locked out of GET too ──────────────────────────────────

    @Test
    void accountant_getList_forbidden() throws Exception {
        User leonidas = tdb.createAccountant("leonidas");
        tdb.assignEntities(leonidas, house);
        mockMvc.perform(get("/api/report-dispatches")
                .header("Authorization", tdb.bearerToken(leonidas))
                .param("entityId", house.getId().toString()))
            .andExpect(status().isForbidden());
    }

    // ─── USER ──────────────────────────────────────────────────────────────

    @Test
    void user_post_ok() throws Exception {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house);
        Transaction e = expense(house.getId());
        mockMvc.perform(post("/api/report-dispatches")
                .header("Authorization", tdb.bearerToken(sissy))
                .param("entityId", house.getId().toString())
                .contentType("application/json")
                .content(createBody(e.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.hasPdf").value(true));
    }

    @Test
    void user_delete_forbidden() throws Exception {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house);
        ReportDispatch d = seedDispatch(house.getId());
        mockMvc.perform(delete("/api/report-dispatches/" + d.getId())
                .header("Authorization", tdb.bearerToken(sissy)))
            .andExpect(status().isForbidden());
    }

    @Test
    void user_unassignedEntity_list_forbidden() throws Exception {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house); // only house
        mockMvc.perform(get("/api/report-dispatches")
                .header("Authorization", tdb.bearerToken(sissy))
                .param("entityId", next2meGroup.getId().toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void user_getById_otherEntity_forbiddenNot404() throws Exception {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house);                 // assigned house only
        ReportDispatch d = seedDispatch(next2meGroup.getId()); // dispatch in another entity
        mockMvc.perform(get("/api/report-dispatches/" + d.getId())
                .header("Authorization", tdb.bearerToken(sissy)))
            .andExpect(status().isForbidden());           // 403, not 404 — don't leak existence
    }

    // ─── ADMIN 200 everywhere ──────────────────────────────────────────────

    @Test
    void admin_getList_ok() throws Exception {
        seedDispatch(next2me.getId());
        mockMvc.perform(get("/api/report-dispatches")
                .header("Authorization", tdb.bearerToken(admin))
                .param("entityId", next2me.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void admin_getById_ok() throws Exception {
        ReportDispatch d = seedDispatch(next2me.getId());
        mockMvc.perform(get("/api/report-dispatches/" + d.getId())
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.transactionIds").isArray());
    }

    @Test
    void admin_recipients_ok() throws Exception {
        seedDispatch(next2me.getId());
        mockMvc.perform(get("/api/report-dispatches/recipients")
                .header("Authorization", tdb.bearerToken(admin))
                .param("entityId", next2me.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value("Λογιστήριο"));
    }

    @Test
    void admin_delete_ok() throws Exception {
        ReportDispatch d = seedDispatch(next2me.getId());
        mockMvc.perform(delete("/api/report-dispatches/" + d.getId())
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // ─── PDF 404 while blob_path is null ───────────────────────────────────

    @Test
    void getPdf_nullBlob_returns404() throws Exception {
        // Seed a dispatch directly with a null blob_path (create() always sets one).
        ReportDispatch d = new ReportDispatch();
        d.setId(UUID.randomUUID());
        d.setEntityId(next2me.getId());
        d.setTitle("no-pdf");
        d.setRecipient("Λογιστήριο");
        d.setSentDate(LocalDate.of(2026, 1, 31));
        d.setCreatedBy(admin.getId());
        d.setBlobPath(null);
        dispatchRepository.save(d);

        mockMvc.perform(get("/api/report-dispatches/" + d.getId() + "/pdf")
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("pdf_not_generated"));
    }

    // ─── PDF 200 after POST (streams from Blob) ─────────────────────────────

    @Test
    void getPdf_afterPost_returns200Pdf() throws Exception {
        when(blobStore.download(anyString())).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        ReportDispatch d = seedDispatch(next2me.getId()); // create() set a blob_path

        mockMvc.perform(get("/api/report-dispatches/" + d.getId() + "/pdf")
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_PDF));
    }

    // ─── VIEWER 403 on preview too ──────────────────────────────────────────

    @Test
    void viewer_preview_forbidden() throws Exception {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, next2meGroup);
        Transaction e = expense(next2meGroup.getId());
        mockMvc.perform(post("/api/report-dispatches/preview")
                .header("Authorization", tdb.bearerToken(simos))
                .param("entityId", next2meGroup.getId().toString())
                .contentType("application/json")
                .content(createBody(e.getId())))
            .andExpect(status().isForbidden());
    }
}
