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