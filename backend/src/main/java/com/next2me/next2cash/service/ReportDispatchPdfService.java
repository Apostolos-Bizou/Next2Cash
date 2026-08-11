package com.next2me.next2cash.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.next2me.next2cash.model.Transaction;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * ReportDispatchPdfService — S105 Level 4.
 *
 * Renders a Report Builder dispatch as a PDF, using the production CashControl
 * kartela PDF ({@link CardExportService}) as the template, with the SPEC §2.4
 * differences:
 *   - title box says "ΤΙΤΛΟΣ ΑΝΑΦΟΡΑΣ" + recipient + sent date (not ΠΡΟΜΗΘΕΥΤΗΣ)
 *   - ONE chronological flow (income + expense mixed, newest first) — no sections
 *   - ΠΟΣΟ + ΠΛΗΡΩΜΕΝΟ colored green (income) / red (expense)
 *   - ΥΠΟΛΟΙΠΟ green when zero, red when pending (as in the kartela)
 *   - KPI "ΠΛΗΡΩΜΕΝΟ" tile split into «έξοδα / έσοδα»
 *   - ΣΥΝΟΨΗ block: Σύνολο Εισπράξεων, Σύνολο Εξόδων, Εκκρεμείς (only if >0), Καθαρό Υπόλοιπο
 *   - pagination: table header repeats per page + footer "σελίδα X / Y"
 *
 * Two-pass render: pass 1 counts pages, pass 2 stamps the real total so the
 * "X / Y" footer is extractable text (not a deferred template).
 */
@Service
public class ReportDispatchPdfService {

    private static final String FONT_PATH = "fonts/DejaVuSans.ttf";
    private static final DateTimeFormatter DATE_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Color NAVY   = new Color(0x16, 0x2B, 0x40);
    private static final Color BLUE   = new Color(0x2E, 0x75, 0xB6);
    private static final Color GREEN  = new Color(0x27, 0xAE, 0x60);
    private static final Color RED    = new Color(0xE7, 0x4C, 0x3C);
    private static final Color ORANGE = new Color(0xFF, 0x64, 0x00);
    private static final Color MUTED  = new Color(0x88, 0x88, 0x88);
    private static final Color BORDER = new Color(0xE0, 0xE6, 0xED);
    // S105 restyle — old client-side look: thin gray hairlines, soft zebra, pills.
    private static final Color LINE           = new Color(0xE5, 0xE7, 0xEB);
    private static final Color ZEBRA          = new Color(0xF8, 0xFA, 0xFC);
    private static final Color DASH           = new Color(0x9C, 0xA3, 0xAF);
    private static final Color PILL_GREEN_BG  = new Color(0xE8, 0xF5, 0xE9);
    private static final Color PILL_ORANGE_BG = new Color(0xFF, 0xF3, 0xE0);
    private static final Color PILL_ORANGE_FG = new Color(0xD9, 0x80, 0x14);
    private static final Color TID            = new Color(0x7D, 0x8F, 0xA1);  // faded blue-gray IDs

    /**
     * Render a dispatch PDF. Transactions are rendered as a single chronological
     * flow (newest first). Does not touch DB or blob storage.
     */
    public byte[] render(String title, String recipient, LocalDate sentDate, List<Transaction> txns) {
        return render(title, recipient, sentDate, txns, null);
    }

    /** Round-2 restyle: the eyebrow label of the title panel shows the ENTITY
     *  name (uppercase). The 4-arg overload (tests, legacy) falls back to a
     *  generic label. */
    public byte[] render(String title, String recipient, LocalDate sentDate,
                         List<Transaction> txns, String entityName) {
        List<Transaction> ordered = new ArrayList<>(txns);
        ordered.sort(Comparator
                .comparing(Transaction::getDocDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Transaction::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());

        byte[] firstPass = build(title, recipient, sentDate, ordered, entityName, 0);
        int totalPages = pageCount(firstPass);
        return build(title, recipient, sentDate, ordered, entityName, totalPages);
    }

    // ─── Core build ─────────────────────────────────────────────────────────

    private byte[] build(String title, String recipient, LocalDate sentDate,
                         List<Transaction> ordered, String entityName, int totalPages) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 28f, 28f, 24f, 24f);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            BaseFont bf = loadBaseFont();
            writer.setPageEvent(new FooterEvent(bf, totalPages));

            doc.open();

            addHeader(doc, bf);
            addTitleBlock(doc, bf, entityName, title, recipient, sentDate);
            Kpis k = computeKpis(ordered);
            addKpiRow(doc, bf, ordered, k);
            // Single chronological flow, newest first — SPEC §2.4 stands.
            addTransactionsTable(doc, bf, ordered);
            addSummaryBlock(doc, bf, k);

            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF generation failed: " + e.getMessage(), e);
        }
    }

    private int pageCount(byte[] pdf) {
        try {
            PdfReader reader = new PdfReader(pdf);
            int n = reader.getNumberOfPages();
            reader.close();
            return n;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF page count failed: " + e.getMessage(), e);
        }
    }

    // ─── Header ───────────────────────────────────────────────────────────

    private void addHeader(Document doc, BaseFont bf) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1f, 1f});

        com.lowagie.text.Font brandName = new com.lowagie.text.Font(bf, 16, com.lowagie.text.Font.BOLD, NAVY);
        com.lowagie.text.Font brandSub  = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, BLUE);
        com.lowagie.text.Font docTitle  = new com.lowagie.text.Font(bf, 13, com.lowagie.text.Font.BOLD, NAVY);
        com.lowagie.text.Font docDate    = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, MUTED);

        Paragraph brand = new Paragraph();
        Chunk brandTop = new Chunk("CashControl", brandName);
        brandTop.setCharacterSpacing(0.4f);          // letterspacing, as in the Καρτέλα lockup
        brand.add(brandTop);
        brand.add(Chunk.NEWLINE);
        Chunk brandBottom = new Chunk("NEXT2ME", brandSub);
        brandBottom.setCharacterSpacing(2.6f);
        brand.add(brandBottom);
        PdfPCell left = new PdfPCell(brand);
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingBottom(8f);
        header.addCell(left);

        Paragraph right = new Paragraph();
        right.setAlignment(Element.ALIGN_RIGHT);
        right.add(new Chunk("ΑΝΑΦΟΡΑ", docTitle));
        right.add(Chunk.NEWLINE);
        right.add(new Chunk("Εκτυπώθηκε: " + LocalDate.now().format(DATE_DISPLAY), docDate));
        PdfPCell rightCell = new PdfPCell(right);
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setPaddingBottom(8f);
        header.addCell(rightCell);

        doc.add(header);
        LineSeparator line = new LineSeparator();
        line.setLineColor(NAVY);
        line.setLineWidth(1.5f);
        doc.add(line);
        doc.add(thinGap());
    }

    // ─── Title block (eyebrow = entity name + title + recipient/sent date) ───

    private void addTitleBlock(Document doc, BaseFont bf, String entityName,
                               String title, String recipient, LocalDate sentDate)
            throws DocumentException {
        com.lowagie.text.Font labelFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, BLUE);
        com.lowagie.text.Font nameFont  = new com.lowagie.text.Font(bf, 20, com.lowagie.text.Font.BOLD, NAVY);
        com.lowagie.text.Font metaFont  = new com.lowagie.text.Font(bf, 10, com.lowagie.text.Font.NORMAL, NAVY);

        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(100);

        // Eyebrow: entity name uppercase (Greek locale); generic fallback for the
        // legacy 4-arg render used by tests. "ΑΝΑΦΟΡΑ" = ΑΝΑΦΟΡΑ
        String eyebrow = (entityName != null && !entityName.isBlank())
                ? entityName.toUpperCase(new java.util.Locale("el"))
                : "ΑΝΑΦΟΡΑ";

        Paragraph content = new Paragraph();
        Chunk eyebrowChunk = new Chunk(eyebrow, labelFont);
        eyebrowChunk.setCharacterSpacing(1.1f);
        content.add(eyebrowChunk);
        content.add(Chunk.NEWLINE);
        content.add(new Chunk(safe(title), nameFont));
        content.add(Chunk.NEWLINE);
        content.add(new Chunk("Παραλήπτης: " + safe(recipient)
                + "     •     Ημ. αποστολής: "
                + (sentDate != null ? sentDate.format(DATE_DISPLAY) : "—"), metaFont));

        PdfPCell cell = new PdfPCell(content);
        cell.setBackgroundColor(new Color(0xF0, 0xF4, 0xF8));
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderColor(BLUE);
        cell.setBorderWidth(4f);
        cell.setPadding(12f);
        wrap.addCell(cell);

        doc.add(wrap);
        doc.add(thinGap());
    }

    // ─── KPI band ───────────────────────────────────────────────────────────

    private void addKpiRow(Document doc, BaseFont bf, List<Transaction> txns, Kpis k) throws DocumentException {
        // Round 2: the split ΠΛΗΡΩΜΕΝΟ tile broke the rhythm — now SEVEN uniform
        // cards, each one big number + one small gray subline.
        // "ΠΛΗΡΩΜΕΝΑ ΕΞΟΔΑ" / "ΕΙΣΠΡΑΓΜΕΝΑ ΕΣΟΔΑ" (uXXXX per convention):
        String lblPaidExp = "ΠΛΗΡΩΜΕΝΑ ΕΞΟΔΑ";
        String lblPaidInc = "ΕΙΣΠΡΑΓΜΕΝΑ ΕΣΟΔΑ";
        String kin = "κινήσεις"; // κινήσεις

        PdfPTable kpis = new PdfPTable(7);
        kpis.setWidthPercentage(100);
        kpis.setWidths(new float[]{1f, 1f, 1f, 1f, 1f, 1f, 1f});

        kpis.addCell(kpiCell(bf, NAVY, "ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ", formatCount(k.countTotal),
                k.countExpense + " έξοδα / " + k.countIncome + " έσοδα"));
        kpis.addCell(kpiCell(bf, GREEN, "ΕΙΣΠΡΑΞΕΙΣ", formatMoney(k.totalIncome), k.countIncome + " " + kin));
        kpis.addCell(kpiCell(bf, RED, "ΕΞΟΔΑ", formatMoney(k.totalExpense), k.countExpense + " " + kin));
        kpis.addCell(kpiCell(bf, RED, lblPaidExp, formatMoney(k.expensePaid), k.countPaidExp + " " + kin));
        kpis.addCell(kpiCell(bf, GREEN, lblPaidInc, formatMoney(k.incomeReceived), k.countPaidInc + " " + kin));
        kpis.addCell(kpiCell(bf, ORANGE, "ΕΚΚΡΕΜΕΙΣ", formatMoney(k.unpaidRemaining), k.countUnpaid + " απλήρωτες"));
        kpis.addCell(kpiCell(bf, k.net.signum() >= 0 ? GREEN : RED, "ΚΑΘΑΡΟ ΥΠΟΛΟΙΠΟ", formatMoney(k.net), ""));

        doc.add(kpis);
        doc.add(thinGap());
    }

    private PdfPCell kpiCell(BaseFont bf, Color valueColor, String label, String value, String sub) {
        com.lowagie.text.Font labelFont = new com.lowagie.text.Font(bf, 7.5f, com.lowagie.text.Font.BOLD, MUTED);
        com.lowagie.text.Font valueFont = new com.lowagie.text.Font(bf, 13, com.lowagie.text.Font.BOLD, valueColor);
        com.lowagie.text.Font subFont   = new com.lowagie.text.Font(bf, 7.5f, com.lowagie.text.Font.NORMAL, MUTED);

        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.add(new Chunk(label, labelFont));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk(value, valueFont));
        if (sub != null && !sub.isEmpty()) {
            p.add(Chunk.NEWLINE);
            p.add(new Chunk(sub, subFont));
        }
        return kpiWrap(p);
    }

    private PdfPCell kpiWrap(Paragraph p) {
        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.5f);   // thin hairline, not a heavy box
        cell.setPadding(7f);         // 7 cards need slightly tighter padding
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    // ─── Transactions table (single chronological flow, Καρτέλα palette) ────

    private void addTransactionsTable(Document doc, BaseFont bf, List<Transaction> txns)
            throws DocumentException {
        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, Color.WHITE);
        com.lowagie.text.Font bodyFont   = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, new Color(0x1A, 0x1A, 0x2E));
        com.lowagie.text.Font boldBody   = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.BOLD, new Color(0x1A, 0x1A, 0x2E));
        com.lowagie.text.Font greenBody  = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, GREEN);
        com.lowagie.text.Font redBody    = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, RED);
        com.lowagie.text.Font dashFont   = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, DASH);
        com.lowagie.text.Font blueBody   = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, BLUE);
        com.lowagie.text.Font tidFont    = new com.lowagie.text.Font(bf, 7.5f, com.lowagie.text.Font.NORMAL, TID);

        PdfPTable table = new PdfPTable(10);
        table.setWidthPercentage(100);
        // Widths re-audited for header@8 + horizontal header padding 6:
        // "ΗΜ/ΝΙΑ ΠΛΗΡΩΜΗΣ" full label on ONE line (col 9: 104 raw ≈ 100pt).
        // ΚΑΤΗΓΟΡΙΑ keeps 102 (ΧΡΗΜΑΤΟΔΟΤΗΣΗ needs ~98 at body pad 7.5).
        table.setWidths(new float[]{42f, 72f, 112f, 102f, 74f, 70f, 82f, 72f, 104f, 84f});
        table.setHeaderRows(1); // navy column header repeats on every page

        String[] headers = {"ID", "ΗΜ/ΝΙΑ", "ΠΕΡΙΓΡΑΦΗ", "ΚΑΤΗΓΟΡΙΑ", "ΜΕΘΟΔΟΣ",
                "ΠΟΣΟ", "ΠΛΗΡΩΜΕΝΟ", "ΥΠΟΛΟΙΠΟ", "ΗΜ/ΝΙΑ ΠΛΗΡΩΜΗΣ", "STATUS"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, headerFont));
            c.setBackgroundColor(NAVY);
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(9f);
            c.setPaddingLeft(6f);
            c.setPaddingRight(6f);
            c.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(c);
        }

        if (txns.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Δεν υπάρχουν κινήσεις σε αυτή την αναφορά.", bodyFont));
            empty.setColspan(10);
            empty.setPadding(14f);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBackgroundColor(ZEBRA);
            table.addCell(empty);
        } else {
            for (int i = 0; i < txns.size(); i++) {
                Transaction t = txns.get(i);
                boolean income = "income".equalsIgnoreCase(t.getType());
                Color bg = (i % 2 == 1) ? ZEBRA : Color.WHITE;
                com.lowagie.text.Font signFont = income ? greenBody : redBody;

                // ID: smaller, faded blue-gray — entityNumber (fallback id).
                Integer idNum = t.getEntityNumber() != null ? t.getEntityNumber() : t.getId();
                String idStr = idNum != null ? String.valueOf(idNum) : "—";
                addBodyCell(table, bg, tidFont, idStr, Element.ALIGN_LEFT);

                addBodyCell(table, bg, bodyFont,
                        t.getDocDate() != null ? t.getDocDate().format(DATE_DISPLAY) : "—", Element.ALIGN_LEFT);

                // ΠΕΡΙΓΡΑΦΗ: bold the counterparty when it is a literal substring —
                // no guessing, only the counterparty FIELD verbatim.
                addDescriptionCell(table, bg, bodyFont, boldBody,
                        safe(stripLeadingId(idNum, t.getDescription())), t.getCounterparty());

                addBodyCell(table, bg, bodyFont, safe(t.getCategory()), Element.ALIGN_LEFT);

                // ΜΕΘΟΔΟΣ: blue (Καρτέλα palette); gray dash when empty.
                String method = nonEmpty(t.getPaymentMethod());
                addBodyCell(table, bg, "—".equals(method) ? dashFont : blueBody, method, Element.ALIGN_LEFT);

                // ΠΟΣΟ colored by sign — green income / red expense (production look).
                addBodyCell(table, bg, signFont, formatMoney(t.getAmount()), Element.ALIGN_RIGHT);

                // ΠΛΗΡΩΜΕΝΟ: green when paid; gray «—» when nothing paid.
                BigDecimal paidAmt = t.getAmountPaid();
                if (paidAmt == null || paidAmt.signum() == 0) {
                    addBodyCell(table, bg, dashFont, "—", Element.ALIGN_RIGHT);
                } else {
                    addBodyCell(table, bg, greenBody, formatMoney(paidAmt), Element.ALIGN_RIGHT);
                }

                // ΥΠΟΛΟΙΠΟ: red when pending, green when 0,00.
                BigDecimal rem = t.getAmountRemaining();
                com.lowagie.text.Font remFont = (rem != null && rem.signum() > 0) ? redBody : greenBody;
                addBodyCell(table, bg, remFont, formatMoney(rem), Element.ALIGN_RIGHT);

                if (t.getPaymentDate() != null) {
                    addBodyCell(table, bg, bodyFont, t.getPaymentDate().format(DATE_DISPLAY), Element.ALIGN_LEFT);
                } else {
                    addBodyCell(table, bg, dashFont, "—", Element.ALIGN_LEFT);
                }

                addStatusPill(table, bg, bf, dashFont, t.getPaymentStatus());
            }
        }
        doc.add(table);
    }

    /** Description cell with the counterparty bolded when it appears verbatim. */
    private void addDescriptionCell(PdfPTable table, Color bg,
                                    com.lowagie.text.Font bodyFont, com.lowagie.text.Font boldBody,
                                    String desc, String counterparty) {
        Phrase p;
        int idx = (counterparty != null && !counterparty.isBlank() && desc != null)
                ? desc.indexOf(counterparty) : -1;
        if (idx >= 0) {
            p = new Phrase();
            if (idx > 0) p.add(new Chunk(desc.substring(0, idx), bodyFont));
            p.add(new Chunk(desc.substring(idx, idx + counterparty.length()), boldBody));
            if (idx + counterparty.length() < desc.length()) {
                p.add(new Chunk(desc.substring(idx + counterparty.length()), bodyFont));
            }
        } else {
            p = new Phrase(desc != null ? desc : "—", bodyFont);
        }
        PdfPCell c = new PdfPCell(p);
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderWidthBottom(0.5f);
        c.setBorderColorBottom(LINE);
        c.setPadding(7.5f);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(c);
    }

    private void addBodyCell(PdfPTable table, Color bg, com.lowagie.text.Font font, String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "—", font));
        c.setBackgroundColor(bg);
        // Only a thin horizontal hairline between rows — no vertical lines.
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderWidthBottom(0.5f);
        c.setBorderColorBottom(LINE);
        c.setPadding(7.5f);   // airier rows, like the old client-side render
        c.setHorizontalAlignment(align);
        table.addCell(c);
    }

    /** STATUS pill: rounded rect, soft fill + thin border in the pill color,
     *  colored bold text — like the Καρτέλα. Null status → plain gray dash. */
    private void addStatusPill(PdfPTable table, Color bg, BaseFont bf,
                               com.lowagie.text.Font dashFont, String status) {
        String label = statusLabelFor(status);
        if ("—".equals(label)) {
            addBodyCell(table, bg, dashFont, "—", Element.ALIGN_LEFT);
            return;
        }
        boolean ok = "paid".equals(status) || "received".equals(status);
        Color fg = ok ? GREEN : PILL_ORANGE_FG;
        Color pillBg = ok ? PILL_GREEN_BG : PILL_ORANGE_BG;
        com.lowagie.text.Font pillFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, fg);

        float textW = bf.getWidthPoint(label, 8) + 2f;  // +2 for simulated-bold spread
        PdfPCell c = new PdfPCell(new Phrase(new Chunk(label, pillFont)));
        c.setCellEvent(new PillEvent(textW, pillBg, fg));
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderWidthBottom(0.5f);
        c.setBorderColorBottom(LINE);
        c.setPadding(7.5f);
        c.setPaddingLeft(11f);   // text sits inside the drawn pill
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(c);
    }

    /** Draws the rounded pill (soft fill + thin colored stroke) behind the
     *  status text. Anchored to the cell top so multi-line rows stay aligned. */
    private static class PillEvent implements com.lowagie.text.pdf.PdfPCellEvent {
        private final float textWidth;
        private final Color fill;
        private final Color stroke;

        PillEvent(float textWidth, Color fill, Color stroke) {
            this.textWidth = textWidth;
            this.fill = fill;
            this.stroke = stroke;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[com.lowagie.text.pdf.PdfPTable.BACKGROUNDCANVAS];
            float h = 14.5f;
            float x = position.getLeft() + 6f;
            float y = position.getTop() - 19.5f;   // top-anchored, wraps the first text line
            float w = textWidth + 10f;
            cb.saveState();
            cb.setColorFill(fill);
            cb.setColorStroke(stroke);
            cb.setLineWidth(0.6f);
            cb.roundRectangle(x, y, w, h, 4f);
            cb.fillStroke();
            cb.restoreState();
        }
    }

    // ─── ΣΥΝΟΨΗ block ───────────────────────────────────────────────────────

    private void addSummaryBlock(Document doc, BaseFont bf, Kpis k) throws DocumentException {
        doc.add(thinGap());
        com.lowagie.text.Font labelFont = new com.lowagie.text.Font(bf, 10, com.lowagie.text.Font.BOLD, NAVY);
        com.lowagie.text.Font hdrFont   = new com.lowagie.text.Font(bf, 11, com.lowagie.text.Font.BOLD, NAVY);

        PdfPTable box = new PdfPTable(2);
        box.setWidthPercentage(45f);
        box.setHorizontalAlignment(Element.ALIGN_RIGHT);
        box.setWidths(new float[]{2f, 1f});
        box.setKeepTogether(true); // never split the ΣΥΝΟΨΗ block across pages

        PdfPCell title = new PdfPCell(new Phrase("ΣΥΝΟΨΗ", hdrFont));
        title.setColspan(2);
        title.setBorder(Rectangle.BOTTOM);
        title.setBorderWidthBottom(0.5f);   // thin hairline, not a heavy navy rule
        title.setBorderColorBottom(LINE);
        title.setPaddingBottom(6f);
        box.addCell(title);

        summaryRow(box, labelFont, bf, "Σύνολο Εισπράξεων", formatMoney(k.totalIncome), GREEN);
        summaryRow(box, labelFont, bf, "Σύνολο Εξόδων", formatMoney(k.totalExpense), RED);
        if (k.unpaidRemaining.signum() > 0) {
            summaryRow(box, labelFont, bf, "Εκκρεμείς πληρωμές", formatMoney(k.unpaidRemaining), RED);
        }
        summaryRow(box, labelFont, bf, "Καθαρό Υπόλοιπο", formatMoney(k.net), k.net.signum() >= 0 ? GREEN : RED);

        doc.add(box);
    }

    private void summaryRow(PdfPTable box, com.lowagie.text.Font labelFont, BaseFont bf,
                            String label, String value, Color valueColor) {
        com.lowagie.text.Font valueFont = new com.lowagie.text.Font(bf, 10, com.lowagie.text.Font.BOLD, valueColor);
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.BOTTOM);         // thin separator per row (old look)
        l.setBorderWidthBottom(0.5f);
        l.setBorderColorBottom(LINE);
        l.setPaddingTop(5f);
        l.setPaddingBottom(5f);
        box.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderWidthBottom(0.5f);
        v.setBorderColorBottom(LINE);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(5f);
        v.setPaddingBottom(5f);
        box.addCell(v);
    }

    // ─── Footer page event ("σελίδα X / Y") ─────────────────────────────────

    private static class FooterEvent extends PdfPageEventHelper {
        private final BaseFont bf;
        private final int totalPages;

        FooterEvent(BaseFont bf, int totalPages) {
            this.bf = bf;
            this.totalPages = totalPages;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 12f;

            String brand = "CashControl · Next2Me Financial System";
            cb.beginText();
            cb.setFontAndSize(bf, 8);
            cb.setColorFill(MUTED);
            cb.setTextMatrix(document.left(), y);
            cb.showText(brand);
            cb.endText();

            // totalPages == 0 on pass 1 (unknown); real total on pass 2.
            String pageInfo = totalPages > 0
                    ? "σελίδα " + writer.getPageNumber() + " / " + totalPages
                    : "σελίδα " + writer.getPageNumber();
            float w = bf.getWidthPoint(pageInfo, 8);
            cb.beginText();
            cb.setFontAndSize(bf, 8);
            cb.setColorFill(MUTED);
            cb.setTextMatrix(document.right() - w, y);
            cb.showText(pageInfo);
            cb.endText();
        }
    }

    // ─── KPI computation ────────────────────────────────────────────────────

    private Kpis computeKpis(List<Transaction> txns) {
        Kpis k = new Kpis();
        for (Transaction t : txns) {
            BigDecimal amount = nz(t.getAmount());
            BigDecimal paid   = nz(t.getAmountPaid());
            BigDecimal rem    = nz(t.getAmountRemaining());
            if ("income".equalsIgnoreCase(t.getType())) {
                k.totalIncome = k.totalIncome.add(amount);
                k.incomeReceived = k.incomeReceived.add(paid);
                k.countIncome++;
                if (paid.signum() > 0) k.countPaidInc++;
            } else {
                k.totalExpense = k.totalExpense.add(amount);
                k.expensePaid = k.expensePaid.add(paid);
                k.countExpense++;
                if (paid.signum() > 0) k.countPaidExp++;
                if (rem.signum() > 0) {
                    k.unpaidRemaining = k.unpaidRemaining.add(rem);
                    k.countUnpaid++;
                }
            }
        }
        k.countTotal = txns.size();
        k.net = k.totalIncome.subtract(k.totalExpense);
        return k;
    }

    private static class Kpis {
        BigDecimal totalIncome = zero();
        BigDecimal totalExpense = zero();
        BigDecimal incomeReceived = zero();
        BigDecimal expensePaid = zero();
        BigDecimal unpaidRemaining = zero();
        BigDecimal net = zero();
        int countTotal, countIncome, countExpense, countUnpaid, countPaidExp, countPaidInc;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Thin vertical gap between sections — a fraction of a blank line. */
    private Paragraph thinGap() {
        Paragraph p = new Paragraph(" ");
        p.setLeading(5f);
        return p;
    }

    private BaseFont loadBaseFont() {
        try (InputStream is = new ClassPathResource(FONT_PATH).getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            return BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
        } catch (IOException | DocumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load DejaVuSans font: " + e.getMessage(), e);
        }
    }

    /**
     * Display-only: strip a leading "{id} - " or "{id}-" prefix from the
     * description when it EXACTLY duplicates this transaction's id (already shown
     * in the ID column). Never a general "number at the start" — only the id.
     * Stored data is untouched.
     */
    static String stripLeadingId(Integer id, String desc) {
        if (id == null || desc == null) return desc;
        String sid = String.valueOf(id);
        if (desc.startsWith(sid + " - ")) return desc.substring(sid.length() + 3);
        if (desc.startsWith(sid + "-"))   return desc.substring(sid.length() + 1);
        return desc;
    }

    private static BigDecimal zero() { return new BigDecimal("0.00"); }
    private static BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String nonEmpty(String s) {
        if (s == null) return "—";
        String t = s.trim();
        return t.isEmpty() ? "—" : t;
    }
    private static String formatCount(int n) { return String.valueOf(n); }

    private static String formatMoney(BigDecimal b) {
        if (b == null) return "0,00 €";
        return String.format(Locale.GERMANY, "%,.2f €", b.doubleValue());
    }

    private static String statusLabelFor(String status) {
        if (status == null) return "—";
        return switch (status) {
            case "paid"     -> "Εξοφλημένη";
            case "received" -> "Εισπράχθηκε";
            case "unpaid"   -> "⚡ Εκκρεμής";   // round-2 lexicon: Εκκρεμής, not Απλήρωτη
            case "urgent"   -> "⚡ Εκκρεμής";
            case "partial"  -> "Μερική";
            default         -> status;
        };
    }
}
