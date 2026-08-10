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
    private static final Color ZEBRA  = new Color(0xFA, 0xFB, 0xFC);

    /**
     * Render a dispatch PDF. Transactions are rendered as a single chronological
     * flow (newest first). Does not touch DB or blob storage.
     */
    public byte[] render(String title, String recipient, LocalDate sentDate, List<Transaction> txns) {
        List<Transaction> ordered = new ArrayList<>(txns);
        ordered.sort(Comparator
                .comparing(Transaction::getDocDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Transaction::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());

        byte[] firstPass = build(title, recipient, sentDate, ordered, 0);
        int totalPages = pageCount(firstPass);
        return build(title, recipient, sentDate, ordered, totalPages);
    }

    // ─── Core build ─────────────────────────────────────────────────────────

    private byte[] build(String title, String recipient, LocalDate sentDate,
                         List<Transaction> ordered, int totalPages) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 28f, 28f, 24f, 24f);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            BaseFont bf = loadBaseFont();
            writer.setPageEvent(new FooterEvent(bf, totalPages));

            doc.open();

            addHeader(doc, bf);
            addTitleBlock(doc, bf, title, recipient, sentDate);
            Kpis k = computeKpis(ordered);
            addKpiRow(doc, bf, ordered, k);
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
        brand.add(new Chunk("CashControl", brandName));
        brand.add(Chunk.NEWLINE);
        brand.add(new Chunk("NEXT2ME", brandSub));
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

    // ─── Title block (ΤΙΤΛΟΣ ΑΝΑΦΟΡΑΣ + recipient + sent date) ───────────────

    private void addTitleBlock(Document doc, BaseFont bf, String title, String recipient, LocalDate sentDate)
            throws DocumentException {
        com.lowagie.text.Font labelFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, BLUE);
        com.lowagie.text.Font nameFont  = new com.lowagie.text.Font(bf, 20, com.lowagie.text.Font.BOLD, NAVY);
        com.lowagie.text.Font metaFont  = new com.lowagie.text.Font(bf, 10, com.lowagie.text.Font.NORMAL, NAVY);

        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(100);

        Paragraph content = new Paragraph();
        content.add(new Chunk("ΤΙΤΛΟΣ ΑΝΑΦΟΡΑΣ", labelFont));
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
        PdfPTable kpis = new PdfPTable(6);
        kpis.setWidthPercentage(100);
        kpis.setWidths(new float[]{1f, 1f, 1f, 1.2f, 1f, 1f});

        kpis.addCell(kpiCell(bf, NAVY, "ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ", formatCount(k.countTotal),
                k.countExpense + " έξοδα / " + k.countIncome + " έσοδα"));
        kpis.addCell(kpiCell(bf, GREEN, "ΕΙΣΠΡΑΞΕΙΣ", formatMoney(k.totalIncome), k.countIncome + " κινήσεις"));
        kpis.addCell(kpiCell(bf, RED, "ΕΞΟΔΑ", formatMoney(k.totalExpense), k.countExpense + " κινήσεις"));
        kpis.addCell(kpiCellPaidSplit(bf, k));
        kpis.addCell(kpiCell(bf, ORANGE, "ΕΚΚΡΕΜΕΙΣ", formatMoney(k.unpaidRemaining), k.countUnpaid + " απλήρωτες"));
        kpis.addCell(kpiCell(bf, k.net.signum() >= 0 ? GREEN : RED, "ΚΑΘΑΡΟ ΥΠΟΛΟΙΠΟ", formatMoney(k.net), ""));

        doc.add(kpis);
        doc.add(thinGap());
    }

    private PdfPCell kpiCell(BaseFont bf, Color valueColor, String label, String value, String sub) {
        com.lowagie.text.Font labelFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, MUTED);
        com.lowagie.text.Font valueFont = new com.lowagie.text.Font(bf, 15, com.lowagie.text.Font.BOLD, valueColor);
        com.lowagie.text.Font subFont   = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.NORMAL, MUTED);

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

    /** ΠΛΗΡΩΜΕΝΟ tile split into «έξοδα» (red) / «έσοδα» (green) — SPEC §2.4. */
    private PdfPCell kpiCellPaidSplit(BaseFont bf, Kpis k) {
        com.lowagie.text.Font labelFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, MUTED);
        com.lowagie.text.Font expFont   = new com.lowagie.text.Font(bf, 11, com.lowagie.text.Font.BOLD, RED);
        com.lowagie.text.Font incFont   = new com.lowagie.text.Font(bf, 11, com.lowagie.text.Font.BOLD, GREEN);

        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.add(new Chunk("ΠΛΗΡΩΜΕΝΟ", labelFont));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk("έξοδα " + formatMoney(k.expensePaid), expFont));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk("έσοδα " + formatMoney(k.incomeReceived), incFont));
        return kpiWrap(p);
    }

    private PdfPCell kpiWrap(Paragraph p) {
        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(1f);
        cell.setPadding(10f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    // ─── Transactions table (single chronological flow) ─────────────────────

    private void addTransactionsTable(Document doc, BaseFont bf, List<Transaction> txns) throws DocumentException {
        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.BOLD, Color.WHITE);
        com.lowagie.text.Font bodyFont   = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, new Color(0x1A, 0x1A, 0x2E));
        com.lowagie.text.Font greenBody  = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, GREEN);
        com.lowagie.text.Font redBody    = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.NORMAL, RED);

        PdfPTable table = new PdfPTable(10);
        table.setWidthPercentage(100);
        // Relative widths chosen so each column, once normalized to the 786pt
        // available width, holds its header + typical cell on ONE line
        // (measured against DejaVuSans metrics — see S105 width audit).
        // ID  ΗΜ/ΝΙΑ ΠΕΡΙΓΡ ΚΑΤΗΓ ΜΕΘΟΔ ΠΟΣΟ ΠΛΗΡΩΜ ΥΠΟΛ ΗΜ/ΝΙΑΠΛ STATUS
        // ΚΑΤΗΓΟΡΙΑ widened 92->102 so "ΧΡΗΜΑΤΟΔΟΤΗΣΗ" (needs ~95pt) stays on one
        // line; space taken from ΠΕΡΙΓΡΑΦΗ (138->132) and ΗΜ/ΝΙΑ ΠΛΗΡ. (86->82),
        // both of which had slack (S105 width audit).
        table.setWidths(new float[]{42f, 72f, 132f, 102f, 76f, 70f, 82f, 72f, 82f, 84f});
        table.setHeaderRows(1); // repeat header on every page (pagination)

        String[] headers = {"ID", "ΗΜ/ΝΙΑ", "ΠΕΡΙΓΡΑΦΗ", "ΚΑΤΗΓΟΡΙΑ", "ΜΕΘΟΔΟΣ",
                "ΠΟΣΟ", "ΠΛΗΡΩΜΕΝΟ", "ΥΠΟΛΟΙΠΟ", "ΗΜ/ΝΙΑ ΠΛΗΡ.", "STATUS"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, headerFont));
            c.setBackgroundColor(NAVY);
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(7f);
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

                String idStr = t.getEntityNumber() != null ? String.valueOf(t.getEntityNumber())
                        : (t.getId() != null ? String.valueOf(t.getId()) : "—");
                addBodyCell(table, bg, bodyFont, idStr, Element.ALIGN_LEFT);
                addBodyCell(table, bg, bodyFont,
                        t.getDocDate() != null ? t.getDocDate().format(DATE_DISPLAY) : "—", Element.ALIGN_LEFT);
                addBodyCell(table, bg, bodyFont, safe(stripLeadingId(t.getId(), t.getDescription())), Element.ALIGN_LEFT);
                addBodyCell(table, bg, bodyFont, safe(t.getCategory()), Element.ALIGN_LEFT);
                addBodyCell(table, bg, bodyFont, nonEmpty(t.getPaymentMethod()), Element.ALIGN_LEFT);

                // ΠΟΣΟ + ΠΛΗΡΩΜΕΝΟ colored by sign (income green / expense red)
                addBodyCell(table, bg, signFont, formatMoney(t.getAmount()), Element.ALIGN_RIGHT);
                addBodyCell(table, bg, signFont, formatMoney(t.getAmountPaid()), Element.ALIGN_RIGHT);

                // ΥΠΟΛΟΙΠΟ green when zero, red when pending
                BigDecimal rem = t.getAmountRemaining();
                com.lowagie.text.Font remFont = (rem != null && rem.signum() > 0) ? redBody : greenBody;
                addBodyCell(table, bg, remFont, formatMoney(rem), Element.ALIGN_RIGHT);

                addBodyCell(table, bg, bodyFont,
                        t.getPaymentDate() != null ? t.getPaymentDate().format(DATE_DISPLAY) : "—", Element.ALIGN_LEFT);
                addBodyCell(table, bg, bodyFont, statusLabelFor(t.getPaymentStatus()), Element.ALIGN_LEFT);
            }
        }
        doc.add(table);
    }

    private void addBodyCell(PdfPTable table, Color bg, com.lowagie.text.Font font, String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "—", font));
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(new Color(0xF0, 0xF0, 0xF0));
        c.setPadding(6f);
        c.setHorizontalAlignment(align);
        table.addCell(c);
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
        title.setBorderColorBottom(NAVY);
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
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingTop(4f);
        l.setPaddingBottom(4f);
        box.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(4f);
        v.setPaddingBottom(4f);
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
            } else {
                k.totalExpense = k.totalExpense.add(amount);
                k.expensePaid = k.expensePaid.add(paid);
                k.countExpense++;
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
        int countTotal, countIncome, countExpense, countUnpaid;
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
            case "unpaid"   -> "Απλήρωτη";
            case "urgent"   -> "⚡ Εκκρεμής";
            case "partial"  -> "Μερική";
            default         -> status;
        };
    }
}
