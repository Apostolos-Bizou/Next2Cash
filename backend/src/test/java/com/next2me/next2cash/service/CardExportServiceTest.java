package com.next2me.next2cash.service;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.repository.ConfigRepository;
import com.next2me.next2cash.repository.PaymentRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CardExportServiceTest — Phase I, Session #13.
 *
 * Excel and PDF generation tests for cards, covering:
 *   - non-empty cards (with mixed paid/unpaid/urgent transactions)
 *   - empty cards (card exists but rule matches no rows)
 *   - filename sanitization (Greek → ASCII transliteration + edge cases)
 *
 * Follows the established Next2Cash test pattern:
 *   - Extends BaseIntegrationTest (Spring Boot + H2 + @Transactional rollback).
 *   - Uses TestDataBuilder for FK-safe CompanyEntity creation.
 *   - Reflection-based field setting for Transaction (mirrors CardServiceTest).
 */
class CardExportServiceTest extends BaseIntegrationTest {

    // XLSX = OOXML zip, first 2 bytes = "PK"
    private static final byte[] XLSX_MAGIC = {0x50, 0x4B};

    // PDF header, first 5 bytes = "%PDF-"
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D};

    @Autowired private TestDataBuilder tdb;
    @Autowired private ConfigRepository configRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CardExportService cardExportService;

    private CompanyEntity entity;
    private UUID cardWithTxnsId;
    private UUID emptyCardId;
    private UUID cardWithPaymentsId;
    private Integer linkedTxnId;

    @BeforeEach
    void setup() {
        entity = tdb.createEntity("EXPTEST", "Export Test Entity");

        // Card 1 — "search:ΜΑΛΑΜΙΤΣΗΣ" with 3 matching expense transactions
        Config cardWithTxns = saveCard("kartela_malamitsis", "ΜΑΛΑΜΙΤΣΗΣ", "search:ΜΑΛΑΜΙΤΣΗΣ");
        cardWithTxnsId = cardWithTxns.getId();

        saveTxn(1001, "Τιμολόγιο ΜΑΛΑΜΙΤΣΗΣ #1", "ΠΡΟΜΗΘΕΙΕΣ", "expense",
            "500.00", "500.00", "0.00", "paid",   LocalDate.of(2026, 1, 15));
        saveTxn(1002, "ΜΑΛΑΜΙΤΣΗΣ απόδειξη",    "ΠΡΟΜΗΘΕΙΕΣ", "expense",
            "300.00", "0.00",   "300.00", "unpaid", LocalDate.of(2026, 2, 10));
        saveTxn(1003, "ΜΑΛΑΜΙΤΣΗΣ επείγουσα",    "ΠΡΟΜΗΘΕΙΕΣ", "expense",
            "200.00", "0.00",   "200.00", "urgent", LocalDate.of(2026, 3, 1));

        // Card 2 — rule that matches nothing (for empty-card test)
        Config emptyCard = saveCard("kartela_empty", "ΚΕΝΗ ΚΑΡΤΕΛΑ", "search:NONEXISTENT_9X");
        emptyCardId = emptyCard.getId();

        // Card 3 (Phase K.3) - 1 txn + 1 linked payment. ASCII-only fixtures.
        Config cardWithPayments = saveCard("kartela_k3", "K3_PAYTEST", "search:K3PAYTEST");
        cardWithPaymentsId = cardWithPayments.getId();

        Transaction linkedTxn = saveTxn(2001, "K3PAYTEST invoice #1", "EXPENSES", "expense",
            "1000.00", "600.00", "400.00", "partial", LocalDate.of(2026, 3, 10));
        linkedTxnId = linkedTxn.getId();

        savePayment(linkedTxnId, "K3PAYTEST settlement", "outgoing", "600.00",
            "BANK_X", LocalDate.of(2026, 3, 12));
    }

    // ───── Fixture helpers (adapted from CardServiceTest) ──────────

    private Config saveCard(String key, String displayName, String rule) {
        Config c = new Config();
        c.setEntityId(entity.getId());
        c.setConfigType("card");
        c.setConfigKey(key);
        c.setConfigValue(displayName);
        c.setParentKey(rule);
        c.setIsActive(Boolean.TRUE);
        c.setSortOrder(0);
        return configRepository.save(c);
    }

    private Transaction saveTxn(int entityNumber, String description, String category,
                                 String type, String amount, String paid, String remaining,
                                 String paymentStatus, LocalDate docDate) {
        Transaction t = new Transaction();
        setField(t, "id",              new Random().nextInt(Integer.MAX_VALUE));
        setField(t, "entityId",        entity.getId());
        setField(t, "entityNumber",    entityNumber);
        setField(t, "type",            type);
        setField(t, "docDate",         docDate);
        setField(t, "description",     description);
        setField(t, "category",        category);
        setField(t, "amount",          new BigDecimal(amount));
        setField(t, "amountPaid",      new BigDecimal(paid));
        setField(t, "amountRemaining", new BigDecimal(remaining));
        setField(t, "paymentStatus",   paymentStatus);
        setField(t, "recordStatus",    "active");
        return transactionRepository.save(t);
    }

    private Payment savePayment(Integer transactionId, String description, String paymentType,
                                  String amount, String paymentMethod, LocalDate paymentDate) {
        Payment p = new Payment();
        setField(p, "id",            new Random().nextInt(Integer.MAX_VALUE));
        setField(p, "entityId",      entity.getId());
        setField(p, "transactionId", transactionId);
        setField(p, "paymentDate",   paymentDate);
        setField(p, "paymentType",   paymentType);
        setField(p, "amount",        new BigDecimal(amount));
        setField(p, "paymentMethod", paymentMethod);
        setField(p, "description",   description);
        setField(p, "status",        "completed");
        return paymentRepository.save(p);
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + name + ": " + e.getMessage(), e);
        }
    }

    // ───── Excel tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Excel export: non-empty card → valid XLSX (PK magic, > 1KB)")
    void excelExportWithTransactions() {
        byte[] bytes = cardExportService.generateExcel(cardWithTxnsId, entity.getId());

        assertThat(bytes).isNotNull();
        assertThat(bytes.length)
            .as("XLSX with 3 transactions should be > 1KB")
            .isGreaterThan(1000);
        assertThat(bytes[0]).isEqualTo(XLSX_MAGIC[0]);
        assertThat(bytes[1]).isEqualTo(XLSX_MAGIC[1]);
    }

    @Test
    @DisplayName("Excel export: empty card → valid XLSX (still has sheets + styles)")
    void excelExportEmptyCard() {
        byte[] bytes = cardExportService.generateExcel(emptyCardId, entity.getId());

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(1000);
        assertThat(bytes[0]).isEqualTo(XLSX_MAGIC[0]);
        assertThat(bytes[1]).isEqualTo(XLSX_MAGIC[1]);
    }

    // ───── PDF tests ───────────────────────────────────────────────

    @Test
    @DisplayName("PDF export: non-empty card → valid PDF (%PDF- magic, > 1KB with embedded font)")
    void pdfExportWithTransactions() {
        byte[] bytes = cardExportService.generatePdf(cardWithTxnsId, entity.getId());

        assertThat(bytes).isNotNull();
        assertThat(bytes.length)
            .as("PDF with embedded DejaVuSans (~740KB font) should be substantial")
            .isGreaterThan(1000);
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            assertThat(bytes[i])
                .as("PDF magic byte at index " + i)
                .isEqualTo(PDF_MAGIC[i]);
        }
    }

    @Test
    @DisplayName("PDF export: empty card → valid PDF with empty-state message")
    void pdfExportEmptyCard() {
        byte[] bytes = cardExportService.generatePdf(emptyCardId, entity.getId());

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(1000);
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            assertThat(bytes[i]).isEqualTo(PDF_MAGIC[i]);
        }
    }

    // ───── Filename sanitizer test ─────────────────────────────────

    @Test
    @DisplayName("sanitizeForFilename: Greek transliteration + edge cases")
    void filenameSanitization() {
        // Simple uppercase Greek
        assertThat(cardExportService.sanitizeForFilename("ΜΑΛΑΜΙΤΣΗΣ"))
            .isEqualTo("MALAMITSIS");

        // Greek with accents (NFD strip) + space
        assertThat(cardExportService.sanitizeForFilename("ΔΕΗ Παροχή"))
            .isEqualTo("DEI_PAROXI");

        // Mixed Greek + Latin + digits + punctuation
        assertThat(cardExportService.sanitizeForFilename("Kartela #42 - ΕΣΟΔΑ"))
            .isEqualTo("KARTELA_42_-_ESODA");

        // Null and blank → EXPORT fallback
        assertThat(cardExportService.sanitizeForFilename(null)).isEqualTo("EXPORT");
        assertThat(cardExportService.sanitizeForFilename("")).isEqualTo("EXPORT");
        assertThat(cardExportService.sanitizeForFilename("   ")).isEqualTo("EXPORT");

        // Only underscores → EXPORT (collapse + trim leaves empty)
        assertThat(cardExportService.sanitizeForFilename("___")).isEqualTo("EXPORT");
    }

    // ----- Phase K.3: Payment integration tests -----

    @Test
    @DisplayName("Excel export K.3: card with txn + payment -> bigger than empty-card xlsx")
    void excelExportWithPayments() {
        byte[] withPayments = cardExportService.generateExcel(cardWithPaymentsId, entity.getId());

        assertThat(withPayments).isNotNull();
        assertThat(withPayments.length)
            .as("XLSX with 1 txn + 1 payment should be > 1KB")
            .isGreaterThan(1000);
        assertThat(withPayments[0]).isEqualTo(XLSX_MAGIC[0]);
        assertThat(withPayments[1]).isEqualTo(XLSX_MAGIC[1]);

        byte[] emptyXlsx = cardExportService.generateExcel(emptyCardId, entity.getId());
        assertThat(withPayments.length)
            .as("card with 2 rows (txn + payment) should be larger than empty card")
            .isGreaterThan(emptyXlsx.length);
    }

    @Test
    @DisplayName("PDF export K.3: card with txn + payment -> both rows rendered")
    void pdfExportWithPayments() {
        byte[] withPayments = cardExportService.generatePdf(cardWithPaymentsId, entity.getId());

        assertThat(withPayments).isNotNull();
        assertThat(withPayments.length)
            .as("PDF with 1 txn + 1 payment should be substantial (embedded font)")
            .isGreaterThan(1000);
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            assertThat(withPayments[i])
                .as("PDF magic byte at index " + i)
                .isEqualTo(PDF_MAGIC[i]);
        }

        byte[] emptyPdf = cardExportService.generatePdf(emptyCardId, entity.getId());
        assertThat(withPayments.length)
            .as("card with 2 rendered rows should exceed empty-state PDF")
            .isGreaterThan(emptyPdf.length);
    }
}
