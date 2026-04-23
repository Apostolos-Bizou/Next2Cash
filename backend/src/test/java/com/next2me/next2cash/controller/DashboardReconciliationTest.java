package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.BankAccount;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.BankAccountRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session #40 — Integration tests for GET /api/dashboard/reconciliation.
 *
 * Covers:
 *   - admin can access any entity (200)
 *   - user assigned to entity A is blocked from entity B (403)
 *   - matching data: bank == book -> isMatch = true, diff ~= 0
 *   - mismatch data: bank != book -> isMatch = false, diff = bank - book
 *
 * Book balance = paid income - paid expense (only paid/received transactions count).
 * Threshold for "match" is 1.0 euro.
 */
class DashboardReconciliationTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BankAccountRepository bankAccountRepository;

    // Shared date range used by all tests in this class
    private static final String FROM = "2026-01-01";
    private static final String TO   = "2026-12-31";

    @Test
    @DisplayName("admin can access reconciliation of ANY entity (200)")
    void reconciliation_adminAnyEntity_returns200() throws Exception {
        User admin = tdb.createAdmin("apostolos");
        CompanyEntity[] entities = tdb.createStandardEntities();
        String bearer = tdb.bearerToken(admin);

        for (CompanyEntity e : entities) {
            mockMvc.perform(get("/api/dashboard/reconciliation")
                    .param("entityId", e.getId().toString())
                    .param("from", FROM)
                    .param("to",   TO)
                    .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.threshold").value(1.0));
        }
    }

    @Test
    @DisplayName("user assigned to House is BLOCKED from Next2Me reconciliation (403)")
    void reconciliation_userUnassignedEntity_returns403() throws Exception {
        User user = tdb.createUser("sissy");
        CompanyEntity house   = tdb.createEntity("HOUSE",   "House");
        CompanyEntity next2me = tdb.createEntity("NEXT2ME", "Next2Me");
        tdb.assignEntities(user, house);

        mockMvc.perform(get("/api/dashboard/reconciliation")
                .param("entityId", next2me.getId().toString())
                .param("from", FROM)
                .param("to",   TO)
                .header("Authorization", tdb.bearerToken(user)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("matching data: bank == book -> isMatch true, diff ~ 0")
    void reconciliation_matchingData_isMatchTrue() throws Exception {
        User admin = tdb.createAdmin("apostolos-match");
        CompanyEntity entity = tdb.createEntity("MATCHCO", "Match Co");

        // Bank account with 500.00 balance
        saveBankAccount(entity, new BigDecimal("500.00"));

        // Paid income 800, paid expense 300 -> book = 500
        savePaidIncome(entity,  new BigDecimal("800.00"), LocalDate.of(2026, 3, 15));
        savePaidExpense(entity, new BigDecimal("300.00"), LocalDate.of(2026, 4, 10));
        // Noise: an UNPAID income that MUST NOT affect the book balance
        saveUnpaidIncome(entity, new BigDecimal("9999.00"), LocalDate.of(2026, 5, 1));

        mockMvc.perform(get("/api/dashboard/reconciliation")
                .param("entityId", entity.getId().toString())
                .param("from", FROM)
                .param("to",   TO)
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalBanks").value(closeTo(500.0, 0.01)))
            .andExpect(jsonPath("$.data.paidIncome").value(closeTo(800.0, 0.01)))
            .andExpect(jsonPath("$.data.paidExpense").value(closeTo(300.0, 0.01)))
            .andExpect(jsonPath("$.data.bookBalance").value(closeTo(500.0, 0.01)))
            .andExpect(jsonPath("$.data.diff").value(closeTo(0.0, 0.01)))
            .andExpect(jsonPath("$.data.isMatch").value(equalTo(true)));
    }

    @Test
    @DisplayName("mismatch data: bank != book -> isMatch false, returns correct diff")
    void reconciliation_mismatchData_returnsCorrectDiff() throws Exception {
        User admin = tdb.createAdmin("apostolos-mismatch");
        CompanyEntity entity = tdb.createEntity("MISMATCHCO", "Mismatch Co");

        // Bank account with 1000.00 balance
        saveBankAccount(entity, new BigDecimal("1000.00"));

        // Paid income 500, paid expense 200 -> book = 300
        // Diff = 1000 - 300 = 700 (mismatch, way above threshold)
        savePaidIncome(entity,  new BigDecimal("500.00"), LocalDate.of(2026, 2, 20));
        savePaidExpense(entity, new BigDecimal("200.00"), LocalDate.of(2026, 3, 25));

        mockMvc.perform(get("/api/dashboard/reconciliation")
                .param("entityId", entity.getId().toString())
                .param("from", FROM)
                .param("to",   TO)
                .header("Authorization", tdb.bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalBanks").value(closeTo(1000.0, 0.01)))
            .andExpect(jsonPath("$.data.bookBalance").value(closeTo(300.0, 0.01)))
            .andExpect(jsonPath("$.data.diff").value(closeTo(700.0, 0.01)))
            .andExpect(jsonPath("$.data.isMatch").value(equalTo(false)));
    }

    // ── Helpers ──

    private BankAccount saveBankAccount(CompanyEntity entity, BigDecimal balance) {
        BankAccount b = new BankAccount();
        b.setEntityId(entity.getId());
        b.setBankName("Test Bank");
        b.setAccountLabel("Main");
        b.setAccountType("checking");
        b.setCurrency("EUR");
        b.setCurrentBalance(balance);
        b.setBalanceDate(LocalDate.now());
        b.setIsActive(true);
        b.setSortOrder(1);
        return bankAccountRepository.save(b);
    }

    private Transaction savePaidIncome(CompanyEntity entity, BigDecimal amount, LocalDate docDate) {
        return saveTransaction(entity, "income", "received", amount, docDate);
    }

    private Transaction savePaidExpense(CompanyEntity entity, BigDecimal amount, LocalDate docDate) {
        return saveTransaction(entity, "expense", "paid", amount, docDate);
    }

    private Transaction saveUnpaidIncome(CompanyEntity entity, BigDecimal amount, LocalDate docDate) {
        return saveTransaction(entity, "income", "unpaid", amount, docDate);
    }

    private Transaction saveTransaction(
            CompanyEntity entity, String type, String paymentStatus,
            BigDecimal amount, LocalDate docDate) {
        Transaction t = new Transaction();
        t.setEntityId(entity.getId());
        t.setType(type);
        t.setDocDate(docDate);
        t.setAmount(amount);
        t.setPaymentStatus(paymentStatus);
        t.setRecordStatus("active");
        t.setDescription("test " + type + " " + paymentStatus);
        return transactionRepository.save(t);
    }
}
