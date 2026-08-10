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
 * No Spring context needed: the service has no injected dependencies (font is
 * loaded from the classpath). Verifies pagination (multi-page, header repeat,
 * correct "σελίδα X / Y") and Greek correctness at the byte level (extracted
 * text must decode to real Greek, not mojibake).
 */
class ReportDispatchPdfServiceTest {

    private final ReportDispatchPdfService pdf = new ReportDispatchPdfService();

    private Transaction tx(int id, String type, String amount, LocalDate date, String desc) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setEntityId(UUID.randomUUID());
        t.setType(type);
        t.setDocDate(date);
        t.setDescription(desc);
        t.setCategory("ΛΕΙΤΟΥΡΓΙΚΑ");
        t.setPaymentMethod("Τράπεζα");
        t.setAmount(new BigDecimal(amount));
        boolean income = "income".equals(type);
        t.setAmountPaid(income ? new BigDecimal(amount) : BigDecimal.ZERO);
        t.setAmountRemaining(income ? BigDecimal.ZERO : new BigDecimal(amount));
        t.setPaymentStatus(income ? "received" : "unpaid");
        return t;
    }

    private static String compact(String s) {
        return s.replaceAll("\\s+", "");
    }

    @Test
    void render_manyRows_isMultiPage_withRepeatedHeaderAndCorrectXofY() throws Exception {
        List<Transaction> txns = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String type = (i % 5 == 0) ? "income" : "expense";
            txns.add(tx(i, type, "123.45", LocalDate.of(2026, 1, 1).plusDays(i),
                    "Κίνηση περιγραφή " + i));
        }

        byte[] bytes = pdf.render("Απολογισμός Ιανουαρίου", "Λεωνίδας",
                LocalDate.of(2026, 1, 31), txns);

        PdfReader reader = new PdfReader(bytes);
        int total = reader.getNumberOfPages();
        assertTrue(total >= 2, "50 rows should span multiple pages, got " + total);

        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= total; page++) {
            String text = extractor.getTextFromPage(page);
            String c = compact(text);
            // Table header repeats on every page.
            assertTrue(c.contains("ΠΕΡΙΓΡΑΦΗ"),
                    "header must repeat on page " + page);
            // Footer "σελίδα X / Y" with the correct running number and total.
            assertTrue(c.contains("σελίδα" + page + "/" + total),
                    "page " + page + " must show 'σελίδα " + page + " / " + total + "'");
        }
        reader.close();
    }

    @Test
    void render_greekText_isNotMojibake() throws Exception {
        List<Transaction> txns = List.of(
                tx(1, "expense", "100.00", LocalDate.of(2026, 1, 10), "Ενοίκιο γραφείου"),
                tx(2, "income", "500.00", LocalDate.of(2026, 1, 20), "Είσπραξη πελάτη"));

        byte[] bytes = pdf.render("Απολογισμός", "Λεωνίδας Παπαδόπουλος",
                LocalDate.of(2026, 1, 31), txns);

        PdfReader reader = new PdfReader(bytes);
        String c = compact(new PdfTextExtractor(reader).getTextFromPage(1));
        reader.close();

        // Labels and Greek content must decode correctly (byte-level proof).
        assertTrue(c.contains("ΤΙΤΛΟΣΑΝΑΦΟΡΑΣ"), "title label must be real Greek");
        assertTrue(c.contains("ΣΥΝΟΨΗ"), "summary block label must be real Greek");
        assertTrue(c.contains("ΕΙΣΠΡΑΞΕΙΣ"), "KPI label must be real Greek");
        assertTrue(c.contains("ΛεωνίδαςΠαπαδόπουλος"), "recipient must not be mojibake");
        assertTrue(c.contains("Ενοίκιογραφείου"), "description must not be mojibake");
    }
}
