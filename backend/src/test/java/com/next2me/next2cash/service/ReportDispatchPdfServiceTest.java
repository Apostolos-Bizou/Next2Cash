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
        "ΠΟΣΟ", "ΠΛΗΡΩΜΕΝΟ", "ΥΠΟΛΟΙΠΟ", "ΠΛΗΡ.", "STATUS"
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
        assertTrue(text.contains("ΤΙΤΛΟΣ ΑΝΑΦΟΡΑΣ") || text.contains("ΤΙΤΛΟΣ"), "title label mojibake");
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
}
