package com.next2me.next2cash.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.next2me.next2cash.model.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S105 Level 4 — ReportDispatchPdfService rendering tests.
 *
 * These assert on RAW extracted text (NOT whitespace-stripped) so a column
 * whose header/value WRAPS to a second line — i.e. an under-allocated column —
 * fails the contiguous contains() check. The earlier version compacted the text
 * and therefore passed on a broken (wrapping) layout; that is the bug these
 * tests now guard against.
 */
class ReportDispatchPdfServiceTest {

    private final ReportDispatchPdfService pdf = new ReportDispatchPdfService();

    // Every column header, each expected on ONE line (contiguous in the text).
    // "ΗΜ/ΝΙΑ ΠΛΗΡ." is verified via its distinctive "ΠΛΗΡ." token.
    private static final String[] HEADER_TOKENS = {
        "ID", "ΗΜ/ΝΙΑ", "ΠΕΡΙΓΡΑΦΗ", "ΚΑΤΗΓΟΡΙΑ", "ΜΕΘΟΔΟΣ",
        "ΠΟΣΟ", "ΠΛΗΡΩΜΕΝΟ", "ΥΠΟΛΟΙΠΟ", "ΠΛΗΡΩΜΗΣ", "STATUS"
    };

    private Transaction tx(int id, String type, String desc, String amount,
                           String paid, String remaining, String status, LocalDate date) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setEntityNumber(id);
        t.setEntityId(UUID.randomUUID());
        t.setType(type);
        t.setDocDate(date);
        t.setDescription(desc);
        t.setCategory("ΛΕΙΤΟΥΡΓΙΚΑ");
        t.setPaymentMethod("Τράπεζα");
        t.setAmount(new BigDecimal(amount));
        t.setAmountPaid(new BigDecimal(paid));
        t.setAmountRemaining(new BigDecimal(remaining));
        t.setPaymentStatus(status);
        t.setPaymentDate("0.00".equals(remaining) ? date : null);
        return t;
    }

    private String pageText(PdfReader reader, int page) throws Exception {
        return new PdfTextExtractor(reader).getTextFromPage(page);
    }

    // ─── All 10 headers unwrapped + ΣΥΝΟΨΗ 4 values + Greek not mojibake ────

    @Test
    void render_singlePage_headersUnwrapped_summaryComplete_greekIntact() throws Exception {
        LocalDate d = LocalDate.of(2026, 1, 28);
        List<Transaction> rows = List.of(
            tx(4801, "income",  "Είσπραξη ΒΑΡΙΑΣ - ΜΕΤΡΗΤΑ", "1290.00", "1290.00", "0.00", "received", d),
            tx(4802, "income",  "Επιστροφή ΦΠΑ",             "3450.75", "3450.75", "0.00", "received", d),
            tx(4803, "expense", "ΔΕΗ",                       "112.41",  "112.41",  "0.00", "paid",     d),
            tx(4804, "expense", "ΕΝΟΙΚΙΟ",                   "1290.00", "0.00", "1290.00", "unpaid",   d),
            tx(4805, "expense", "ΤΑΛΙΑΔΟΡΟΣ Dn2Me UK εξόφληση τιμολογίου", "2000.00", "1200.00", "800.00", "partial", d));

        byte[] bytes = pdf.render("Απόδοση Δαπανών Ιανουαρίου 2026", "Λεωνίδας", LocalDate.of(2026, 1, 31), rows);
        PdfReader reader = new PdfReader(bytes);
        String text = pageText(reader, 1);
        reader.close();

        // All 10 column headers present AND contiguous (no wrap).
        for (String h : HEADER_TOKENS) {
            assertTrue(text.contains(h), "header wrapped or missing: '" + h + "'");
        }
        // Dates must not wrap ("28/01/2026" contiguous, not "28/01/202"+"6").
        assertTrue(text.contains("28/01/2026"), "date column too narrow — date wrapped");

        // ΣΥΝΟΨΗ block has 4 numeric (€) values: income, expense, pending, net.
        int idx = text.indexOf("ΣΥΝΟΨΗ");
        assertTrue(idx >= 0, "ΣΥΝΟΨΗ block missing");
        long euros = text.substring(idx).chars().filter(c -> c == '€').count();
        assertTrue(euros >= 4, "ΣΥΝΟΨΗ must show 4 amounts, found " + euros);

        // Greek decodes correctly (byte-level proof — not mojibake).
        // (Round-2: the eyebrow is the entity name / generic fallback, so assert the title text itself.)
        assertTrue(text.contains("Απόδοση Δαπανών"), "title text mojibake");
        assertTrue(text.contains("ΕΙΣΠΡΑΞΕΙΣ"), "KPI label mojibake");
        assertTrue(text.contains("Λεωνίδας"), "recipient mojibake");
    }

    // ─── Multi-page: header repeats + "σελίδα X / Y" on every page ──────────

    @Test
    void render_manyRows_multiPage_headerRepeats_andPageXofY() throws Exception {
        List<Transaction> rows = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String type = (i % 5 == 0) ? "income" : "expense";
            rows.add(tx(i, type, "Κίνηση περιγραφή " + i, "123.45",
                    "income".equals(type) ? "123.45" : "0.00",
                    "income".equals(type) ? "0.00" : "123.45",
                    "income".equals(type) ? "received" : "unpaid",
                    LocalDate.of(2026, 1, 1).plusDays(i)));
        }

        byte[] bytes = pdf.render("Αναλυτική Κατάσταση", "Λεωνίδας", LocalDate.of(2026, 1, 31), rows);
        PdfReader reader = new PdfReader(bytes);
        int total = reader.getNumberOfPages();
        assertTrue(total >= 2, "50 rows should span multiple pages, got " + total);

        int pagesWithFooter = 0;
        for (int page = 1; page <= total; page++) {
            String text = pageText(reader, page);
            // Header repeats on every page (contiguous, not wrapped).
            assertTrue(text.contains("ΠΛΗΡΩΜΕΝΟ"),
                    "header must repeat unwrapped on page " + page);
            // Footer "σελίδα X / Y" with the correct running number and real total.
            assertTrue(text.contains("σελίδα " + page + " / " + total),
                    "page " + page + " must show 'σελίδα " + page + " / " + total + "'");
            if (text.contains("σελίδα")) pagesWithFooter++;
        }
        reader.close();

        assertEquals(total, pagesWithFooter, "'σελίδα' must appear on every one of " + total + " pages");
    }

    // ─── 90+ rows: the NORMAL monthly send size (all pending of a month) ─────

    @Test
    void render_90plusRows_multiPage_headerRepeats_andPageXofY() throws Exception {
        List<Transaction> rows = new ArrayList<>();
        for (int i = 1; i <= 95; i++) {
            String type = (i % 6 == 0) ? "income" : "expense";
            Transaction t = tx(i, type, "Μηνιαία κίνηση με ελληνική περιγραφή " + i, "247.13",
                    "income".equals(type) ? "247.13" : "0.00",
                    "income".equals(type) ? "0.00" : "247.13",
                    "income".equals(type) ? "received" : "unpaid",
                    LocalDate.of(2026, 7, 1).plusDays(i % 30));
            t.setCategory(i % 4 == 0 ? "ΧΡΗΜΑΤΟΔΟΤΗΣΗ" : "ΛΕΙΤΟΥΡΓΙΚΑ");
            rows.add(t);
        }

        byte[] bytes = pdf.render("Μηνιαία Αποστολή Ιουλίου — Όλες οι Εκκρεμότητες", "Λογιστήριο",
                LocalDate.of(2026, 7, 31), rows);
        PdfReader reader = new PdfReader(bytes);
        int total = reader.getNumberOfPages();
        assertTrue(total >= 3, "95 rows should span at least 3 pages, got " + total);

        for (int page = 1; page <= total; page++) {
            String text = pageText(reader, page);
            boolean hasTableHeader = text.contains("ΠΛΗΡΩΜΕΝΟ");
            if (page < total) {
                assertTrue(hasTableHeader,
                        "table header must repeat unwrapped on page " + page + "/" + total);
            } else {
                // Last page may legitimately hold only the ΣΥΝΟΨΗ block (no table).
                assertTrue(hasTableHeader || text.contains("ΣΥΝΟΨΗ"),
                        "last page must have the table header or the ΣΥΝΟΨΗ block");
            }
            assertTrue(text.contains("σελίδα " + page + " / " + total),
                    "page " + page + " must show 'σελίδα " + page + " / " + total + "'");
        }
        reader.close();
    }

    // ─── Long category must not wrap (ΧΡΗΜΑΤΟΔΟΤΗΣΗ) ─────────────────────────

    @Test
    void render_longCategory_staysOnOneLine() throws Exception {
        Transaction t = tx(4802, "income", "128 - ΒΑΡΙΑΣ ΕΣΟΔΑ ΜΕΤΡΗΤΑ",
                "1000.00", "1000.00", "0.00", "received", LocalDate.of(2026, 7, 30));
        t.setCategory("ΧΡΗΜΑΤΟΔΟΤΗΣΗ"); // the widest real category

        byte[] bytes = pdf.render("Τεστ Κατηγορίας", "Λεωνίδας", LocalDate.of(2026, 7, 31), List.of(t));
        PdfReader reader = new PdfReader(bytes);
        String text = pageText(reader, 1);
        reader.close();

        // Contiguous contains() fails if it wrapped to "ΧΡΗΜΑΤΟΔΟΤΗΣ" + "Η".
        assertTrue(text.contains("ΧΡΗΜΑΤΟΔΟΤΗΣΗ"), "ΚΑΤΗΓΟΡΙΑ column too narrow — category wrapped");
    }

    // ─── stripLeadingId: display-only id-prefix removal ────────────────────

    @Test
    void stripLeadingId_removesExactIdPrefixOnly() {
        // real case: entityNumber 127 + "127 - EPASS" → "EPASS"
        assertEquals("EPASS", ReportDispatchPdfService.stripLeadingId(127, "127 - EPASS"));
        // "{id} - " form
        assertEquals("ΕΣΟΔΑ", ReportDispatchPdfService.stripLeadingId(4811, "4811 - ΕΣΟΔΑ"));
        // "{id}-" form (no spaces)
        assertEquals("ΕΣΟΔΑ", ReportDispatchPdfService.stripLeadingId(4811, "4811-ΕΣΟΔΑ"));
        // leading number is NOT this id → unchanged
        assertEquals("2024/0345 - τιμολόγιο", ReportDispatchPdfService.stripLeadingId(4811, "2024/0345 - τιμολόγιο"));
        // no prefix → unchanged
        assertEquals("ΕΣΟΔΑ ΒΑΡΙΑΣ", ReportDispatchPdfService.stripLeadingId(4811, "ΕΣΟΔΑ ΒΑΡΙΑΣ"));
        // a longer number that merely starts with the id → unchanged
        assertEquals("48110 - X", ReportDispatchPdfService.stripLeadingId(4811, "48110 - X"));
    }

    // ─── Wiring: strip uses entityNumber (the ID column), not transaction id ──

    @Test
    void render_stripsEntityNumberPrefix_notTransactionId() throws Exception {
        // id 90619 (serial) but entityNumber 127 — the description prefix is 127.
        Transaction t = tx(90619, "expense", "127 - EPASS", "70.00", "70.00", "0.00", "paid", LocalDate.of(2026, 8, 3));
        t.setEntityNumber(127);

        byte[] bytes = pdf.render("Τεστ Προθέματος", "Λεωνίδας", LocalDate.of(2026, 8, 4), List.of(t));
        PdfReader reader = new PdfReader(bytes);
        String text = pageText(reader, 1);
        reader.close();

        assertTrue(text.contains("EPASS"), "description missing");
        assertFalse(text.contains("127 - EPASS"), "entityNumber prefix not stripped (strip compared the wrong field)");
    }
}
