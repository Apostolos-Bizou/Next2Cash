package com.next2me.next2cash.controller;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S106 — attachment display-name + ZIP dedup helpers.
 * Greek inputs are built from codepoints so the test source stays pure ASCII.
 */
class DocumentFilenameTest {

    /** ΚΙΝΗΤΟ (Kappa Iota Nu Eta Tau Omicron) */
    private static final String GR_KINITO =
            new String(new int[]{0x039A, 0x0399, 0x039D, 0x0397, 0x03A4, 0x039F}, 0, 6);
    /** Πληρωμή (payment) */
    private static final String GR_PLIROMI =
            new String(new int[]{0x03A0, 0x03BB, 0x03B7, 0x03C1, 0x03C9, 0x03BC, 0x03AE}, 0, 7);
    /** ΕΝΟΙΚΙΟ (rent) */
    private static final String GR_ENOIKIO =
            new String(new int[]{0x0395, 0x039D, 0x039F, 0x0399, 0x039A, 0x0399, 0x039F}, 0, 7);

    // ─── displayFileName: strip "N - " prefix, display only ─────────────────

    @Test
    void strips_numberDashPrefix_greekName() {
        String base = "95 - " + GR_KINITO + " 06os 2026.pdf";
        assertEquals(GR_KINITO + " 06os 2026.pdf", DocumentController.displayFileName(base));
    }

    @Test
    void strips_afterExtractingBasenameFromFullPath() {
        String path = "50317f44-9961-4fb4-add0-7a118e32dc14/2026/06/90576/95 - " + GR_KINITO + ".pdf";
        assertEquals(GR_KINITO + ".pdf", DocumentController.displayFileName(path));
    }

    @Test
    void keeps_paymentReceiptNames() {
        // "Πληρωμή #9 - ΕΝΟΙΚΙΟ.pdf" does not match ^digits-space-dash → unchanged
        String name = GR_PLIROMI + " #9 - " + GR_ENOIKIO + ".pdf";
        assertEquals(name, DocumentController.displayFileName(name));
    }

    @Test
    void keeps_numberWithoutDash() {
        assertEquals("0002 LEGAL TIMOLOGIA 14-09-2027.pdf",
                DocumentController.displayFileName("0002 LEGAL TIMOLOGIA 14-09-2027.pdf"));
    }

    @Test
    void guard_stripLeavingOnlyExtension_keepsOriginal() {
        assertEquals("95 - .pdf", DocumentController.displayFileName("95 - .pdf"));
    }

    @Test
    void plainNameWithoutPrefix_unchanged() {
        assertEquals(GR_KINITO + ".pdf", DocumentController.displayFileName(GR_KINITO + ".pdf"));
    }

    // ─── uniqueZipEntryName: _1, _2 dedup ───────────────────────────────────

    @Test
    void dedup_suffixesDuplicates() {
        Set<String> used = new HashSet<>();
        assertEquals("a.pdf",   DocumentController.uniqueZipEntryName("a.pdf", used));
        assertEquals("a_1.pdf", DocumentController.uniqueZipEntryName("a.pdf", used));
        assertEquals("a_2.pdf", DocumentController.uniqueZipEntryName("a.pdf", used));
        assertEquals("b.pdf",   DocumentController.uniqueZipEntryName("b.pdf", used));
    }

    @Test
    void dedup_greekAndNoExtension() {
        Set<String> used = new HashSet<>();
        String gr = GR_KINITO + ".pdf";
        assertEquals(gr, DocumentController.uniqueZipEntryName(gr, used));
        assertEquals(GR_KINITO + "_1.pdf", DocumentController.uniqueZipEntryName(gr, used));
        assertEquals("noext",   DocumentController.uniqueZipEntryName("noext", used));
        assertEquals("noext_1", DocumentController.uniqueZipEntryName("noext", used));
    }

    // ─── rfc5987Encode ──────────────────────────────────────────────────────

    @Test
    void rfc5987_greekAndSpaces() {
        // ΚΙΝΗΤΟ in UTF-8: CE 9A CE 99 CE 9D CE 97 CE A4 CE 9F
        assertEquals("%CE%9A%CE%99%CE%9D%CE%97%CE%A4%CE%9F",
                DocumentController.rfc5987Encode(GR_KINITO));
        assertEquals("a%20b.pdf", DocumentController.rfc5987Encode("a b.pdf"));
    }
}
