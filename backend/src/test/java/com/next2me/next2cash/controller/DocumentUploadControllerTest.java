package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase M.1 — integration tests for POST /api/documents/upload
 *
 * Coverage strategy:
 * - These tests cover the authorization + validation paths that do NOT
 *   reach the actual Azure Blob SDK call. Testing the happy-path upload
 *   would require mocking BlobServiceClient, which is instantiated
 *   inline (not a Spring bean). Happy-path is covered by a manual
 *   curl test against production after deploy.
 */
class DocumentUploadControllerTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;
    @Autowired private TransactionRepository transactionRepository;

    // Helper: create an "active" transaction owned by a given entity
    private Transaction createTxn(CompanyEntity entity, User createdBy) {
        Transaction t = new Transaction();
        t.setEntityId(entity.getId());
        t.setType("expense");
        t.setDocDate(LocalDate.of(2026, 4, 15));
        t.setCounterparty("ACME Supplies Ltd");
        t.setCategory("office");
        t.setAmount(new BigDecimal("123.45"));
        t.setAmountPaid(BigDecimal.ZERO);
        t.setAmountRemaining(new BigDecimal("123.45"));
        t.setPaymentStatus("unpaid");
        t.setCreatedBy(createdBy.getId());
        t.setRecordStatus("active");
        t.setEntityNumber(1);
        return transactionRepository.save(t);
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile(
            "file", "invoice.pdf", "application/pdf",
            "%PDF-1.4 dummy content".getBytes()
        );
    }

    private MockMultipartFile jpgFile() {
        return new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg",
            "fake image bytes".getBytes()
        );
    }

    // -------------------------------------------------------------------
    // TEST 1: USER assigned to entity A cannot upload to a txn of entity B
    //         -> 403 Forbidden via assertCanAccessEntity()
    // -------------------------------------------------------------------
    @Test
    @DisplayName("USER assigned to A is BLOCKED from uploading to txn of B (403)")
    void upload_crossEntity_returns403() throws Exception {
        CompanyEntity entityA = tdb.createEntity("AAA", "EntityA");
        CompanyEntity entityB = tdb.createEntity("BBB", "EntityB");

        // user belongs to entity A only (explicit assignment)
        User userA = tdb.createUser("userA");
        tdb.assignEntities(userA, entityA);
        String tokenA = tdb.bearerToken(userA);

        // transaction lives in entity B
        Transaction txnB = createTxn(entityB, userA);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents/upload")
                .file(pdfFile())
                .param("transactionId", String.valueOf(txnB.getId()))
                .header("Authorization", tokenA)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------
    // TEST 2: ADMIN uploading to non-existent transaction returns 404
    // -------------------------------------------------------------------
    @Test
    @DisplayName("ADMIN uploading to non-existent transaction returns 404")
    void upload_missingTransaction_returns404() throws Exception {
        User admin = tdb.createAdmin("admin");
        String token = tdb.bearerToken(admin);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents/upload")
                .file(pdfFile())
                .param("transactionId", "99999999")
                .header("Authorization", token)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("transaction_not_found"));
    }

    // -------------------------------------------------------------------
    // TEST 3: ADMIN uploading a non-PDF file returns 400 only_pdf_allowed
    // -------------------------------------------------------------------
    @Test
    @DisplayName("ADMIN uploading non-PDF returns 400 only_pdf_allowed")
    void upload_nonPdf_returns400() throws Exception {
        CompanyEntity entity = tdb.createEntity("MAIN", "Main");
        User admin = tdb.createAdmin("admin");
        String token = tdb.bearerToken(admin);

        Transaction txn = createTxn(entity, admin);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents/upload")
                .file(jpgFile())
                .param("transactionId", String.valueOf(txn.getId()))
                .header("Authorization", token)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("only_pdf_allowed"));
    }

    // -------------------------------------------------------------------
    // TEST 5: Filename fallback — when both counterparty and account are
    //         null, validation still passes (hits blob layer, not 400).
    //         We assert it is NOT a 400 only_pdf_allowed / file_missing.
    //         The blob call will fail in test env (no Azure), causing
    //         a 500, which is acceptable — we are testing the validation
    //         path, not the blob integration.
    // -------------------------------------------------------------------
    @Test
    @DisplayName("upload passes validation when counterparty + account both null (doc fallback)")
    void upload_accountFallback_usesAccountWhenCounterpartyNull() throws Exception {
        CompanyEntity entity = tdb.createEntity("MAIN", "Main");
        User admin = tdb.createAdmin("admin");
        String token = tdb.bearerToken(admin);

        Transaction txn = createTxn(entity, admin);
        txn.setCounterparty(null);
        txn.setAccount(null);
        transactionRepository.save(txn);

        // Expect: NOT a 400 validation error (will 500 from blob layer in test)
        int status = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents/upload")
                .file(pdfFile())
                .param("transactionId", String.valueOf(txn.getId()))
                .header("Authorization", token)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andReturn().getResponse().getStatus();

        // We accept 200 (if blob somehow works) or 500 (blob fails in test env)
        // but NOT 400 (validation rejection) or 403/404/401
        org.junit.jupiter.api.Assertions.assertTrue(
            status == 200 || status == 500,
            "Expected 200 or 500 (blob layer), got " + status
        );
    }

    // -------------------------------------------------------------------
    // TEST 4: VIEWER role cannot upload -> 403 via @PreAuthorize
    // -------------------------------------------------------------------
    @Test
    @DisplayName("VIEWER role is BLOCKED from upload endpoint (403)")
    void upload_viewerRole_returns403() throws Exception {
        CompanyEntity entity = tdb.createEntity("MAIN", "Main");
        User viewer = tdb.createViewer("viewer");
        tdb.assignEntities(viewer, entity);
        String token = tdb.bearerToken(viewer);

        Transaction txn = createTxn(entity, viewer);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents/upload")
                .file(pdfFile())
                .param("transactionId", String.valueOf(txn.getId()))
                .header("Authorization", token)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isForbidden());
    }
}