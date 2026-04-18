package com.next2me.next2cash.service;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.ConfigRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase H v2 — CardService rule engine tests.
 *
 * These are integration tests (Spring context + H2) but exercise pure-Java
 * filtering logic. No custom JPQL/native queries are used by the service,
 * so H2 parity with PostgreSQL is guaranteed at the query level.
 *
 * Coverage:
 *   1. search single-word match
 *   2. search multi-word AND
 *   3. search comma-separated OR
 *   4. category exact match
 *   5. subcategory exact match
 *   6. counterparty exact match
 *   7. invalid rule format → 400
 */
class CardServiceTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;
    @Autowired private ConfigRepository configRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CardService cardService;

    private CompanyEntity entity;

    @BeforeEach
    void setup() {
        entity = tdb.createEntity("TEST", "Test Entity");

        // Seed 6 transactions covering different scenarios:
        //   1. description "ΔΕΗ Παροχή ρεύματος"
        //   2. description "ΔΕΔΔΗΕ τελος"
        //   3. description "ΜΑΛΑΜΙΤΣΗΣ αμοιβή"
        //   4. description NULL (must not break search)
        //   5. category "ΛΕΙΤΟΥΡΓΙΚΑ", subcategory "ΕΝΟΙΚΙΟ"
        //   6. counterparty "ΜΑΚΗΣ"
        saveTxn(entity.getId(), "active", "ΔΕΗ Παροχή ρεύματος", null, null, null);
        saveTxn(entity.getId(), "active", "ΔΕΔΔΗΕ τελος",           null, null, null);
        saveTxn(entity.getId(), "active", "ΜΑΛΑΜΙΤΣΗΣ αμοιβή",       null, null, null);
        saveTxn(entity.getId(), "active", null,                    null, null, null);
        saveTxn(entity.getId(), "active", "τυχαιο",                "ΛΕΙΤΟΥΡΓΙΚΑ", "ΕΝΟΙΚΙΟ", null);
        saveTxn(entity.getId(), "active", "τυχαιο",                null, null, "ΜΑΚΗΣ");

        // Add one VOID transaction that must NEVER appear:
        saveTxn(entity.getId(), "void",   "ΔΕΗ ακυρωμένο",        null, null, null);
    }

    private Transaction saveTxn(UUID entityId, String status, String desc,
                                 String cat, String sub, String cparty) {
        Transaction t = new Transaction();
        setField(t, "id",              new java.util.Random().nextInt(Integer.MAX_VALUE));
        setField(t, "entityId",        entityId);
        setField(t, "type",            "expense");
        setField(t, "docDate",         LocalDate.of(2026, 1, 1));
        setField(t, "description",     desc);
        setField(t, "category",        cat);
        setField(t, "subcategory",     sub);
        setField(t, "counterparty",    cparty);
        setField(t, "amount",          new BigDecimal("100.00"));
        setField(t, "amountPaid",      BigDecimal.ZERO);
        setField(t, "amountRemaining", new BigDecimal("100.00"));
        setField(t, "recordStatus",    status);
        return transactionRepository.save(t);
    }

    /**
     * Overload used by summary tests — lets us set type, amountPaid, remaining, paymentStatus.
     */
    private Transaction saveTxnFull(UUID entityId, String type, String desc,
                                     String cat, String cparty,
                                     String amount, String paid, String remaining,
                                     String paymentStatus) {
        Transaction t = new Transaction();
        setField(t, "id",              new java.util.Random().nextInt(Integer.MAX_VALUE));
        setField(t, "entityId",        entityId);
        setField(t, "type",            type);
        setField(t, "docDate",         LocalDate.of(2026, 1, 1));
        setField(t, "description",     desc);
        setField(t, "category",        cat);
        setField(t, "counterparty",    cparty);
        setField(t, "amount",          new BigDecimal(amount));
        setField(t, "amountPaid",      new BigDecimal(paid));
        setField(t, "amountRemaining", new BigDecimal(remaining));
        setField(t, "paymentStatus",   paymentStatus);
        setField(t, "recordStatus",    "active");
        return transactionRepository.save(t);
    }

    private Config saveCard(UUID entityId, String key, String displayName, String rule) {
        Config c = new Config();
        c.setEntityId(entityId);
        c.setConfigType("card");
        c.setConfigKey(key);
        c.setConfigValue(displayName);
        c.setParentKey(rule);
        c.setIsActive(Boolean.TRUE);
        c.setSortOrder(0);
        return configRepository.save(c);
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + name, e);
        }
    }

    // ──────────────────────────────
    // 1. search single-word
    // ──────────────────────────────
    @Test
    void search_singleWord_matches() {
        Config card = saveCard(entity.getId(), "malamitsis", "Malamitsis", "search:ΜΑΛΑΜΙΤΣΗΣ");

        CardService.CardTransactions result =
            cardService.getTransactionsForCard(card.getId(), entity.getId(), 2000, 0);

        assertEquals(1, result.total());
        assertTrue(result.transactions().get(0).getDescription().contains("ΜΑΛΑΜΙΤΣΗΣ"));
    }

    // ──────────────────────────────
    // 2. search multi-word AND — both words must appear
    //    Also verifies null-description safety (that row must be skipped, not crash)
    // ──────────────────────────────
    @Test
    void search_multiWord_and() {
        Config card = saveCard(entity.getId(), "dei_parochi", "DEH Parochi",
            "search:ΔΕΗ ΠΑΡΟΧΗ");

        CardService.CardTransactions result =
            cardService.getTransactionsForCard(card.getId(), entity.getId(), 2000, 0);

        // Only row 1 has BOTH ΔΕΗ AND ΠΑΡΟΧΗ (row 2 has ΔΕΔΔΗΕ, row 3 unrelated)
        assertEquals(1, result.total());
    }

    // ──────────────────────────────
    // 3. search comma-separated OR
    // ──────────────────────────────
    @Test
    void search_commaSeparated_or() {
        Config card = saveCard(entity.getId(), "utilities", "Utilities",
            "search:ΔΕΗ,ΔΕΔΔΗΕ");

        CardService.CardTransactions result =
            cardService.getTransactionsForCard(card.getId(), entity.getId(), 2000, 0);

        // Rows 1 (ΔΕΗ) + 2 (ΔΕΔΔΗΕ) match. VOID row must be excluded.
        assertEquals(2, result.total());
    }

    // ──────────────────────────────
    // 4. category exact match
    // ──────────────────────────────
    @Test
    void category_exactMatch() {
        Config card = saveCard(entity.getId(), "leit_cat", "Λειτουργικά",
            "category:ΛΕΙΤΟΥΡΓΙΚΑ");

        CardService.CardTransactions result =
            cardService.getTransactionsForCard(card.getId(), entity.getId(), 2000, 0);

        assertEquals(1, result.total());
    }

    // ──────────────────────────────
    // 5. subcategory exact match
    // ──────────────────────────────
    @Test
    void subcategory_exactMatch() {
        Config card = saveCard(entity.getId(), "rent_sub", "Ενοίκιο",
            "subcategory:ΕΝΟΙΚΙΟ");

        CardService.CardTransactions result =
            cardService.getTransactionsForCard(card.getId(), entity.getId(), 2000, 0);

        assertEquals(1, result.total());
    }

    // ──────────────────────────────
    // 6. counterparty exact match
    // ──────────────────────────────
    @Test
    void counterparty_exactMatch() {
        Config card = saveCard(entity.getId(), "makis", "Μάκης",
            "counterparty:ΜΑΚΗΣ");

        CardService.CardTransactions result =
            cardService.getTransactionsForCard(card.getId(), entity.getId(), 2000, 0);

        assertEquals(1, result.total());
    }

    // ──────────────────────────────
    // 7. invalid rule format → 400
    // ──────────────────────────────
    @Test
    void invalidRule_throwsBadRequest() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("configKey",   "bad_card");
        payload.put("configValue", "Bad");
        payload.put("parentKey",   "invalidFormatNoColon");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> cardService.createCard(entity.getId(), payload));

        assertEquals(400, ex.getStatusCode().value());
    }

    // ─────────────────────────────────────────────
    // 8. Summary — expenses only (no income, no urgent)
    // ─────────────────────────────────────────────
    @Test
    void summary_expensesOnly() {
        CompanyEntity e = tdb.createEntity("SUM1", "Summary Expenses Only");

        saveTxnFull(e.getId(), "expense", "Rent Jan", "RENT", null, "1000.00", "1000.00",   "0.00", "paid");
        saveTxnFull(e.getId(), "expense", "Rent Feb", "RENT", null, "1000.00",  "500.00", "500.00", "partial");
        saveTxnFull(e.getId(), "expense", "Rent Mar", "RENT", null, "1000.00",    "0.00","1000.00", "unpaid");

        Config card = saveCard(e.getId(), "rent_sum", "Rent", "category:RENT");

        CardService.CardSummary s = cardService.getCardSummary(card.getId(), e.getId());

        assertEquals(new BigDecimal("3000.00"), s.total());
        assertEquals(new BigDecimal("1500.00"), s.paid());
        assertEquals(new BigDecimal("1500.00"), s.unpaid());
        assertEquals(0, s.income().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.urgent().compareTo(BigDecimal.ZERO));
        assertEquals(3, s.countTotal());
        assertEquals(2, s.countPaid());    // 2 txns with paid > 0
        assertEquals(2, s.countUnpaid());  // 2 txns with remaining > 0
        assertEquals(0, s.countIncome());
        assertEquals(0, s.countUrgent());
    }

    // ─────────────────────────────────────────────
    // 9. Summary — mixed expenses + income + urgent
    // ─────────────────────────────────────────────
    @Test
    void summary_mixedWithIncomeAndUrgent() {
        CompanyEntity e = tdb.createEntity("SUM2", "Summary Mixed");

        // 2 expenses (one urgent), 1 income
        saveTxnFull(e.getId(), "expense", "DEH bill",   null, "DEH", "200.00", "0.00",   "200.00", "urgent");
        saveTxnFull(e.getId(), "expense", "DEH bill 2", null, "DEH", "100.00", "100.00",   "0.00", "paid");
        saveTxnFull(e.getId(), "income",  "DEH refund", null, "DEH", "50.00",  "50.00",    "0.00", "received");

        Config card = saveCard(e.getId(), "deh_sum", "DEH", "counterparty:DEH");

        CardService.CardSummary s = cardService.getCardSummary(card.getId(), e.getId());

        assertEquals(new BigDecimal("300.00"), s.total());
        assertEquals(new BigDecimal("100.00"), s.paid());
        assertEquals(new BigDecimal("200.00"), s.unpaid());
        assertEquals(new BigDecimal("50.00"),  s.income());
        assertEquals(new BigDecimal("200.00"), s.urgent());
        assertEquals(2, s.countTotal());
        assertEquals(1, s.countPaid());
        assertEquals(1, s.countUnpaid());
        assertEquals(1, s.countIncome());
        assertEquals(1, s.countUrgent());
    }

    // ─────────────────────────────────────────────
    // 10. Summary — empty card (no matches, all zeros)
    // ─────────────────────────────────────────────
    @Test
    void summary_emptyCard() {
        CompanyEntity e = tdb.createEntity("SUM3", "Summary Empty");
        Config card = saveCard(e.getId(), "nobody", "Nobody", "counterparty:DOES_NOT_EXIST");

        CardService.CardSummary s = cardService.getCardSummary(card.getId(), e.getId());

        // compareTo == 0 means numeric equality regardless of scale (0 vs 0.00)
        assertEquals(0, s.total().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.paid().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.unpaid().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.income().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.urgent().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.countTotal());
        assertEquals(0, s.countPaid());
        assertEquals(0, s.countUnpaid());
        assertEquals(0, s.countIncome());
        assertEquals(0, s.countUrgent());
    }

    // ─────────────────────────────────────────────
    // 11. Summary — urgent is subset of unpaid, NOT separate bucket
    // ─────────────────────────────────────────────
    @Test
    void summary_urgentIsSubsetOfUnpaid() {
        CompanyEntity e = tdb.createEntity("SUM4", "Summary Urgent Subset");

        // Both have remaining > 0, only one is urgent
        saveTxnFull(e.getId(), "expense", "Bill A", "UTIL", null, "500.00", "0.00", "500.00", "unpaid");
        saveTxnFull(e.getId(), "expense", "Bill B", "UTIL", null, "300.00", "0.00", "300.00", "urgent");

        Config card = saveCard(e.getId(), "util_sum", "Utils", "category:UTIL");

        CardService.CardSummary s = cardService.getCardSummary(card.getId(), e.getId());

        // Unpaid counts BOTH (800), urgent is the subset (300)
        assertEquals(new BigDecimal("800.00"), s.unpaid());
        assertEquals(new BigDecimal("300.00"), s.urgent());
        assertEquals(2, s.countUnpaid());
        assertEquals(1, s.countUrgent());
    }
}
