package com.next2me.next2cash.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.lowagie.text.pdf.*;
import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * CardExportService — Phase I (Session #13)
 *
 * Renders a card (kartela) as Excel (.xlsx) or PDF using the legacy
 * design as the 1:1 reference (colors, column widths, KPI layout).
 *
 * Data is pulled via CardService (reuses the same rule engine + summary
 * pipeline that feeds the UI, so exports are always consistent with
 * what the user sees on screen).
 *
 * Color palette (taken from legacy index.html exportSupplierCard):
 *   Navy brand:        #162B40
 *   Blue accent:       #2E75B6
 *   Success (paid):    #27ae60 (UI) / #1E8449 (Excel)
 *   Danger (unpaid):   #e74c3c (UI) / #C0392B (Excel)
 *   Warning (urgent):  #ff6400
 *   Zebra stripe:      #FAFBFC / #FFFFFF
 *   Supplier bg:       #F0F4F8
 *
 * Legacy accountant semantics:
 *   ΕΙΣΠΡΑΞΕΙΣ = SUM(amount) for type='income' (NOT combined with paid expenses).
 */
@Service
@RequiredArgsConstructor
public class CardExportService {

    private static final String FONT_PATH = "fonts/DejaVuSans.ttf";

    // Locale-neutral date formatter — DD/MM/YYYY for display
    private static final DateTimeFormatter DATE_DISPLAY =
        DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CardService cardService;

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generates an XLSX binary for the given card.
     * Two sheets:
     *   - "Σύνοψη" — title + 5 KPI summary
     *   - "Κινήσεις" — 10-column transactions table
     *
     * @param cardId   card UUID
     * @param entityId entity scope (for multi-tenant isolation)
     * @return raw xlsx bytes
     */
    public byte[] generateExcel(UUID cardId, UUID entityId) {
        CardService.CardSummary summary = cardService.getCardSummary(cardId, entityId);
        CardService.CardTransactions txns =
            cardService.getTransactionsForCard(cardId, entityId, 10000, 0);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildSummarySheet(wb, summary);
            buildTransactionsSheet(wb, summary, txns.transactions());

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Excel generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a PDF binary for the given card.
     * A4 landscape, 1cm margins, DejaVuSans (Unicode/Greek).
     * Layout: header band → supplier block → 5 KPI cards → transactions table.
     *
     * @param cardId   card UUID
     * @param entityId entity scope
     * @return raw pdf bytes
     */
    public byte[] generatePdf(UUID cardId, UUID entityId) {
        CardService.CardSummary summary = cardService.getCardSummary(cardId, entityId);
        CardService.CardTransactions txns =
            cardService.getTransactionsForCard(cardId, entityId, 10000, 0);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Document doc = new Document(PageSize.A4.rotate(), 28f, 28f, 24f, 24f);
            PdfWriter.getInstance(doc, out);
            doc.open();

            BaseFont baseFont = loadBaseFont();

            addPdfHeader(doc, baseFont);
            addPdfSupplierBlock(doc, baseFont, summary.card());
            addPdfKpiRow(doc, baseFont, summary);
            addPdfTransactionsTable(doc, baseFont, txns.transactions());

            doc.close();
            return out.toByteArray();

        } catch (IOException | DocumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "PDF generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Filename sanitizer: accent/diacritic strip + ASCII-only + uppercase
     * for use in Content-Disposition headers. Non-ASCII chars are removed
     * (Greek → transliteration: Α→A, Β→V... simplified to strip entirely
     * here, since most browsers auto-handle UTF-8 but older ones don't).
     */
    public String sanitizeForFilename(String s) {
        if (s == null || s.isBlank()) return "EXPORT";
        String decomposed = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
        String stripped = decomposed.replaceAll("\\p{M}+", "");
        String greekMapped = stripped
            .replace('Α', 'A').replace('Β', 'B').replace('Γ', 'G').replace('Δ', 'D')
            .replace('Ε', 'E').replace('Ζ', 'Z').replace('Η', 'I').replace('Θ', 'T')
            .replace('Ι', 'I').replace('Κ', 'K').replace('Λ', 'L').replace('Μ', 'M')
            .replace('Ν', 'N').replace('Ξ', 'X').replace('Ο', 'O').replace('Π', 'P')
            .replace('Ρ', 'R').replace('Σ', 'S').replace('Τ', 'T').replace('Υ', 'Y')
            .replace('Φ', 'F').replace('Χ', 'X').replace('Ψ', 'P').replace('Ω', 'O')
            .replace('α', 'a').replace('β', 'b').replace('γ', 'g').replace('δ', 'd')
            .replace('ε', 'e').replace('ζ', 'z').replace('η', 'i').replace('θ', 't')
            .replace('ι', 'i').replace('κ', 'k').replace('λ', 'l').replace('μ', 'm')
            .replace('ν', 'n').replace('ξ', 'x').replace('ο', 'o').replace('π', 'p')
            .replace('ρ', 'r').replace('σ', 's').replace('ς', 's').replace('τ', 't')
            .replace('υ', 'y').replace('φ', 'f').replace('χ', 'x').replace('ψ', 'p')
            .replace('ω', 'o');
        String asciiOnly = greekMapped.replaceAll("[^A-Za-z0-9_-]", "_");
        String collapsed = asciiOnly.replaceAll("_+", "_").replaceAll("^_|_$", "");
        return collapsed.isEmpty() ? "EXPORT" : collapsed.toUpperCase();
    }

    // ═══════════════════════════════════════════════════════════════
    // EXCEL — legacy design port
    // ═══════════════════════════════════════════════════════════════

    private void buildSummarySheet(XSSFWorkbook wb, CardService.CardSummary s) {
        Sheet sheet = wb.createSheet("Σύνοψη");

        CreationHelper ch = wb.getCreationHelper();
        Font brandFont = wb.createFont();
        brandFont.setFontName("Calibri");
        brandFont.setFontHeightInPoints((short) 14);
        brandFont.setBold(true);
        brandFont.setColor(IndexedColors.DARK_BLUE.getIndex());

        Font titleFont = wb.createFont();
        titleFont.setFontName("Calibri");
        titleFont.setFontHeightInPoints((short) 18);
        titleFont.setBold(true);

        Font labelFont = wb.createFont();
        labelFont.setFontName("Calibri");
        labelFont.setFontHeightInPoints((short) 10);
        labelFont.setBold(true);
        labelFont.setColor(IndexedColors.WHITE.getIndex());

        Font valueFont = wb.createFont();
        valueFont.setFontName("Calibri");
        valueFont.setFontHeightInPoints((short) 12);
        valueFont.setBold(true);

        Font valueGreen = wb.createFont();
        valueGreen.setFontName("Calibri");
        valueGreen.setFontHeightInPoints((short) 12);
        valueGreen.setBold(true);
        valueGreen.setColor(IndexedColors.DARK_GREEN.getIndex());

        Font valueRed = wb.createFont();
        valueRed.setFontName("Calibri");
        valueRed.setFontHeightInPoints((short) 12);
        valueRed.setBold(true);
        valueRed.setColor(IndexedColors.DARK_RED.getIndex());

        CellStyle brandStyle = wb.createCellStyle();
        brandStyle.setFont(brandFont);
        brandStyle.setAlignment(HorizontalAlignment.LEFT);

        CellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.LEFT);

        CellStyle labelStyle = wb.createCellStyle();
        labelStyle.setFont(labelFont);
        labelStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        labelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        labelStyle.setAlignment(HorizontalAlignment.CENTER);
        labelStyle.setBorderBottom(BorderStyle.THIN);

        CellStyle moneyStyle = wb.createCellStyle();
        moneyStyle.setFont(valueFont);
        moneyStyle.setDataFormat(ch.createDataFormat().getFormat("#,##0.00 €"));
        moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle moneyGreen = wb.createCellStyle();
        moneyGreen.cloneStyleFrom(moneyStyle);
        moneyGreen.setFont(valueGreen);

        CellStyle moneyRed = wb.createCellStyle();
        moneyRed.cloneStyleFrom(moneyStyle);
        moneyRed.setFont(valueRed);

        // Row 0: Brand
        Row r0 = sheet.createRow(0);
        Cell c00 = r0.createCell(0);
        c00.setCellValue("CashControl — Next2Me");
        c00.setCellStyle(brandStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        // Row 1: Card name
        Row r1 = sheet.createRow(1);
        Cell c10 = r1.createCell(0);
        c10.setCellValue(safe(s.card().getConfigValue()));
        c10.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 4));

        // Row 2: Rule description
        Row r2 = sheet.createRow(2);
        Cell c20 = r2.createCell(0);
        c20.setCellValue("Κανόνας: " + safe(s.card().getParentKey()));
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 4));

        // Row 3: Export timestamp
        Row r3 = sheet.createRow(3);
        Cell c30 = r3.createCell(0);
        c30.setCellValue("Εκτυπώθηκε: " +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 4));

        // Row 5: Labels row
        Row r5 = sheet.createRow(5);
        String[] labels = {"ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ", "ΕΞΟΦΛΗΜΕΝΕΣ", "ΑΠΛΗΡΩΤΕΣ",
                           "ΕΙΣΠΡΑΞΕΙΣ", "ΕΚΚΡΕΜΕΙΣ"};
        for (int i = 0; i < 5; i++) {
            Cell cell = r5.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(labelStyle);
        }
        r5.setHeightInPoints(24f);

        // Row 6: Values row
        Row r6 = sheet.createRow(6);
        setMoney(r6, 0, s.total(),  moneyStyle);
        setMoney(r6, 1, s.paid(),   moneyGreen);
        setMoney(r6, 2, s.unpaid(), moneyRed);
        setMoney(r6, 3, s.income(), moneyStyle);
        setMoney(r6, 4, s.urgent(), moneyRed);

        // Row 7: Counts row
        Row r7 = sheet.createRow(7);
        r7.createCell(0).setCellValue(s.countTotal()  + " κινήσεις");
        r7.createCell(1).setCellValue(s.countPaid()   + " κινήσεις");
        r7.createCell(2).setCellValue(s.countUnpaid() + " κινήσεις");
        r7.createCell(3).setCellValue(s.countIncome() + " κινήσεις");
        r7.createCell(4).setCellValue(s.countUrgent() + " κινήσεις");

        for (int i = 0; i < 5; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }
    }

    private void buildTransactionsSheet(XSSFWorkbook wb, CardService.CardSummary summary,
                                         List<Transaction> txns) {
        Sheet sheet = wb.createSheet("Κινήσεις");

        CreationHelper ch = wb.getCreationHelper();

        Font headerFont = wb.createFont();
        headerFont.setFontName("Calibri");
        headerFont.setFontHeightInPoints((short) 10);
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.LEFT);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        Font bodyFont = wb.createFont();
        bodyFont.setFontName("Calibri");
        bodyFont.setFontHeightInPoints((short) 10);

        CellStyle bodyStyle = wb.createCellStyle();
        bodyStyle.setFont(bodyFont);
        bodyStyle.setAlignment(HorizontalAlignment.LEFT);
        bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle bodyZebra = wb.createCellStyle();
        bodyZebra.cloneStyleFrom(bodyStyle);
        bodyZebra.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        bodyZebra.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle dateStyle = wb.createCellStyle();
        dateStyle.cloneStyleFrom(bodyStyle);
        dateStyle.setDataFormat(ch.createDataFormat().getFormat("dd/mm/yyyy"));

        CellStyle dateZebra = wb.createCellStyle();
        dateZebra.cloneStyleFrom(bodyZebra);
        dateZebra.setDataFormat(ch.createDataFormat().getFormat("dd/mm/yyyy"));

        CellStyle moneyStyle = wb.createCellStyle();
        moneyStyle.cloneStyleFrom(bodyStyle);
        moneyStyle.setDataFormat(ch.createDataFormat().getFormat("#,##0.00 €"));
        moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle moneyZebra = wb.createCellStyle();
        moneyZebra.cloneStyleFrom(bodyZebra);
        moneyZebra.setDataFormat(ch.createDataFormat().getFormat("#,##0.00 €"));
        moneyZebra.setAlignment(HorizontalAlignment.RIGHT);

        Font greenFont = wb.createFont();
        greenFont.setFontName("Calibri");
        greenFont.setFontHeightInPoints((short) 10);
        greenFont.setColor(IndexedColors.DARK_GREEN.getIndex());

        CellStyle moneyGreen = wb.createCellStyle();
        moneyGreen.cloneStyleFrom(moneyStyle);
        moneyGreen.setFont(greenFont);

        CellStyle moneyGreenZebra = wb.createCellStyle();
        moneyGreenZebra.cloneStyleFrom(moneyZebra);
        moneyGreenZebra.setFont(greenFont);

        Font redFont = wb.createFont();
        redFont.setFontName("Calibri");
        redFont.setFontHeightInPoints((short) 10);
        redFont.setColor(IndexedColors.DARK_RED.getIndex());

        CellStyle moneyRed = wb.createCellStyle();
        moneyRed.cloneStyleFrom(moneyStyle);
        moneyRed.setFont(redFont);

        CellStyle moneyRedZebra = wb.createCellStyle();
        moneyRedZebra.cloneStyleFrom(moneyZebra);
        moneyRedZebra.setFont(redFont);

        // Header row
        String[] headers = {"ID", "ΗΜ/ΝΙΑ", "ΠΕΡΙΓΡΑΦΗ", "ΚΑΤΗΓΟΡΙΑ", "ΜΕΘΟΔΟΣ",
                            "ΠΟΣΟ", "ΠΛΗΡΩΜΕΝΟ", "ΥΠΟΛΟΙΠΟ", "ΗΜ/ΝΙΑ ΠΛΗΡ.", "STATUS"};
        Row hdr = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        hdr.setHeightInPoints(22f);

        // Body rows
        for (int i = 0; i < txns.size(); i++) {
            Transaction t = txns.get(i);
            boolean zebra = (i % 2 == 1);
            Row row = sheet.createRow(i + 1);

            // ID
            Cell c0 = row.createCell(0);
            Integer entNum = t.getEntityNumber();
            c0.setCellValue(entNum != null ? entNum : 0);
            c0.setCellStyle(zebra ? bodyZebra : bodyStyle);

            // Date
            Cell c1 = row.createCell(1);
            if (t.getDocDate() != null) {
                c1.setCellValue(java.sql.Date.valueOf(t.getDocDate()));
                c1.setCellStyle(zebra ? dateZebra : dateStyle);
            } else {
                c1.setCellValue("");
                c1.setCellStyle(zebra ? bodyZebra : bodyStyle);
            }

            // Description
            Cell c2 = row.createCell(2);
            c2.setCellValue(safe(t.getDescription()));
            c2.setCellStyle(zebra ? bodyZebra : bodyStyle);

            // Category
            Cell c3 = row.createCell(3);
            c3.setCellValue(safe(t.getCategory()));
            c3.setCellStyle(zebra ? bodyZebra : bodyStyle);

            // Payment method (reads from transaction's paymentStatus-adjacent field if present)
            Cell c4 = row.createCell(4);
            c4.setCellValue("—");  // not currently on Transaction model, placeholder
            c4.setCellStyle(zebra ? bodyZebra : bodyStyle);

            // Amount
            Cell c5 = row.createCell(5);
            c5.setCellValue(doubleValue(t.getAmount()));
            c5.setCellStyle(zebra ? moneyZebra : moneyStyle);

            // Amount paid (green)
            Cell c6 = row.createCell(6);
            c6.setCellValue(doubleValue(t.getAmountPaid()));
            c6.setCellStyle(zebra ? moneyGreenZebra : moneyGreen);

            // Amount remaining (red if > 0)
            Cell c7 = row.createCell(7);
            BigDecimal rem = t.getAmountRemaining();
            c7.setCellValue(doubleValue(rem));
            boolean hasRem = rem != null && rem.signum() > 0;
            if (hasRem) {
                c7.setCellStyle(zebra ? moneyRedZebra : moneyRed);
            } else {
                c7.setCellStyle(zebra ? moneyZebra : moneyStyle);
            }

            // Payment date (Transaction doesn't currently expose it directly;
            // show dash for now — will be wired after model check in later phase)
            Cell c8 = row.createCell(8);
            c8.setCellValue("—");
            c8.setCellStyle(zebra ? bodyZebra : bodyStyle);

            // Status (text)
            Cell c9 = row.createCell(9);
            c9.setCellValue(statusLabelFor(t.getPaymentStatus()));
            c9.setCellStyle(zebra ? bodyZebra : bodyStyle);
        }

        // Column widths (legacy-inspired)
        int[] widths = {8, 12, 38, 18, 14, 14, 14, 14, 14, 16};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }

        // Freeze header row
        sheet.createFreezePane(0, 1);
    }

    // ═══════════════════════════════════════════════════════════════
    // PDF — legacy design port (OpenPDF, A4 landscape)
    // ═══════════════════════════════════════════════════════════════

    private BaseFont loadBaseFont() {
        try (InputStream is = new ClassPathResource(FONT_PATH).getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            return BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED, true, fontBytes, null);
        } catch (IOException | DocumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to load DejaVuSans font: " + e.getMessage(), e);
        }
    }

    private void addPdfHeader(Document doc, BaseFont bf) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1f, 1f});

        com.lowagie.text.Font brandNameFont = new com.lowagie.text.Font(bf, 16, com.lowagie.text.Font.BOLD, new Color(0x16, 0x2B, 0x40));
        com.lowagie.text.Font brandSubFont  = new com.lowagie.text.Font(bf,  8, com.lowagie.text.Font.BOLD, new Color(0x2E, 0x75, 0xB6));
        com.lowagie.text.Font docTitleFont  = new com.lowagie.text.Font(bf, 13, com.lowagie.text.Font.BOLD, new Color(0x16, 0x2B, 0x40));
        com.lowagie.text.Font docDateFont   = new com.lowagie.text.Font(bf,  9, com.lowagie.text.Font.NORMAL, new Color(0x88, 0x88, 0x88));

        // Left cell
        Paragraph brand = new Paragraph();
        brand.add(new Chunk("CashControl", brandNameFont));
        brand.add(Chunk.NEWLINE);
        brand.add(new Chunk("NEXT2ME", brandSubFont));
        PdfPCell leftCell = new PdfPCell(brand);
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        leftCell.setPaddingBottom(8f);
        header.addCell(leftCell);

        // Right cell
        Paragraph right = new Paragraph();
        right.setAlignment(Element.ALIGN_RIGHT);
        right.add(new Chunk("ΚΑΡΤΕΛΑ", docTitleFont));
        right.add(Chunk.NEWLINE);
        right.add(new Chunk("Εκτυπώθηκε: " + LocalDate.now().format(DATE_DISPLAY), docDateFont));
        PdfPCell rightCell = new PdfPCell(right);
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setPaddingBottom(8f);
        header.addCell(rightCell);

        doc.add(header);

        // Blue underline
        LineSeparator line = new LineSeparator();
        line.setLineColor(new Color(0x16, 0x2B, 0x40));
        line.setLineWidth(1.5f);
        doc.add(line);
        doc.add(new Paragraph(" "));
    }

    private void addPdfSupplierBlock(Document doc, BaseFont bf, Config card) throws DocumentException {
        com.lowagie.text.Font labelFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, new Color(0x2E, 0x75, 0xB6));
        com.lowagie.text.Font nameFont  = new com.lowagie.text.Font(bf, 20, com.lowagie.text.Font.BOLD, new Color(0x16, 0x2B, 0x40));

        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(100);

        Paragraph content = new Paragraph();
        content.add(new Chunk("ΠΡΟΜΗΘΕΥΤΗΣ / ΑΝΤΙΣΥΜΒΑΛΛΟΜΕΝΟΣ", labelFont));
        content.add(Chunk.NEWLINE);
        content.add(new Chunk(safe(card.getConfigValue()), nameFont));

        PdfPCell cell = new PdfPCell(content);
        cell.setBackgroundColor(new Color(0xF0, 0xF4, 0xF8));
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderColor(new Color(0x2E, 0x75, 0xB6));
        cell.setBorderWidth(4f);
        cell.setPadding(12f);
        wrap.addCell(cell);

        doc.add(wrap);
        doc.add(new Paragraph(" "));
    }

    private void addPdfKpiRow(Document doc, BaseFont bf, CardService.CardSummary s) throws DocumentException {
        com.lowagie.text.Font labelFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.BOLD, new Color(0x88, 0x88, 0x88));
        com.lowagie.text.Font countFont = new com.lowagie.text.Font(bf, 8, com.lowagie.text.Font.NORMAL, new Color(0x88, 0x88, 0x88));

        Color navy    = new Color(0x16, 0x2B, 0x40);
        Color green   = new Color(0x27, 0xAE, 0x60);
        Color red     = new Color(0xE7, 0x4C, 0x3C);
        Color orange  = new Color(0xFF, 0x64, 0x00);

        PdfPTable kpis = new PdfPTable(5);
        kpis.setWidthPercentage(100);
        kpis.setWidths(new float[]{1f, 1f, 1f, 1f, 1f});

        kpis.addCell(kpiCell(bf, labelFont, countFont, navy,   "ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ", s.total(),  s.countTotal()));
        kpis.addCell(kpiCell(bf, labelFont, countFont, green,  "ΕΞΟΦΛΗΜΕΝΕΣ",      s.paid(),   s.countPaid()));
        kpis.addCell(kpiCell(bf, labelFont, countFont, red,    "ΑΠΛΗΡΩΤΕΣ",        s.unpaid(), s.countUnpaid()));
        kpis.addCell(kpiCell(bf, labelFont, countFont, navy,   "ΕΙΣΠΡΑΞΕΙΣ",       s.income(), s.countIncome()));
        kpis.addCell(kpiCell(bf, labelFont, countFont, orange, "⚡ ΕΚΚΡΕΜΕΙΣ",     s.urgent(), s.countUrgent()));

        doc.add(kpis);
        doc.add(new Paragraph(" "));
    }

    private PdfPCell kpiCell(BaseFont bf, com.lowagie.text.Font labelFont,
                              com.lowagie.text.Font countFont, Color valueColor,
                              String label, BigDecimal amount, int count) {
        com.lowagie.text.Font valueFont = new com.lowagie.text.Font(bf, 15, com.lowagie.text.Font.BOLD, valueColor);

        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.add(new Chunk(label, labelFont));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk(formatMoney(amount), valueFont));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk(count + " κινήσεις", countFont));

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(0xE0, 0xE6, 0xED));
        cell.setBorderWidth(1f);
        cell.setPadding(10f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private void addPdfTransactionsTable(Document doc, BaseFont bf,
                                          List<Transaction> txns) throws DocumentException {
        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(bf, 9,  com.lowagie.text.Font.BOLD, Color.WHITE);
        com.lowagie.text.Font bodyFont   = new com.lowagie.text.Font(bf, 9,  com.lowagie.text.Font.NORMAL, new Color(0x1A, 0x1A, 0x2E));
        com.lowagie.text.Font greenBody  = new com.lowagie.text.Font(bf, 9,  com.lowagie.text.Font.NORMAL, new Color(0x27, 0xAE, 0x60));
        com.lowagie.text.Font redBody    = new com.lowagie.text.Font(bf, 9,  com.lowagie.text.Font.NORMAL, new Color(0xE7, 0x4C, 0x3C));

        PdfPTable table = new PdfPTable(10);
        table.setWidthPercentage(100);
        // Legacy column widths (rough proportions)
        table.setWidths(new float[]{5f, 8f, 25f, 12f, 10f, 9f, 9f, 9f, 10f, 10f});
        table.setHeaderRows(1);

        String[] headers = {"ID", "ΗΜ/ΝΙΑ", "ΠΕΡΙΓΡΑΦΗ", "ΚΑΤΗΓΟΡΙΑ", "ΜΕΘΟΔΟΣ",
                            "ΠΟΣΟ", "ΠΛΗΡΩΜΕΝΟ", "ΥΠΟΛΟΙΠΟ", "ΗΜ/ΝΙΑ ΠΛΗΡ.", "STATUS"};

        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, headerFont));
            c.setBackgroundColor(new Color(0x16, 0x2B, 0x40));
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(7f);
            c.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(c);
        }

        if (txns.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Δεν υπάρχουν κινήσεις για αυτή την καρτέλα.", bodyFont));
            empty.setColspan(10);
            empty.setPadding(14f);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBackgroundColor(new Color(0xFA, 0xFB, 0xFC));
            table.addCell(empty);
        } else {
            for (int i = 0; i < txns.size(); i++) {
                Transaction t = txns.get(i);
                Color bg = (i % 2 == 1) ? new Color(0xFA, 0xFB, 0xFC) : Color.WHITE;

                addPdfBodyCell(table, bg, bodyFont,
                    t.getEntityNumber() != null ? String.valueOf(t.getEntityNumber()) : "—",
                    Element.ALIGN_LEFT);
                addPdfBodyCell(table, bg, bodyFont,
                    t.getDocDate() != null ? t.getDocDate().format(DATE_DISPLAY) : "—",
                    Element.ALIGN_LEFT);
                addPdfBodyCell(table, bg, bodyFont, safe(t.getDescription()), Element.ALIGN_LEFT);
                addPdfBodyCell(table, bg, bodyFont, safe(t.getCategory()),    Element.ALIGN_LEFT);
                addPdfBodyCell(table, bg, bodyFont, "—",                      Element.ALIGN_LEFT);
                addPdfBodyCell(table, bg, bodyFont, formatMoney(t.getAmount()), Element.ALIGN_RIGHT);

                BigDecimal paid = t.getAmountPaid();
                com.lowagie.text.Font paidFont = (paid != null && paid.signum() > 0) ? greenBody : bodyFont;
                addPdfBodyCell(table, bg, paidFont, formatMoney(paid), Element.ALIGN_RIGHT);

                BigDecimal rem = t.getAmountRemaining();
                com.lowagie.text.Font remFont = (rem != null && rem.signum() > 0) ? redBody : bodyFont;
                addPdfBodyCell(table, bg, remFont, formatMoney(rem), Element.ALIGN_RIGHT);

                addPdfBodyCell(table, bg, bodyFont, "—", Element.ALIGN_LEFT);
                addPdfBodyCell(table, bg, bodyFont, statusLabelFor(t.getPaymentStatus()), Element.ALIGN_LEFT);
            }
        }

        doc.add(table);
    }

    private void addPdfBodyCell(PdfPTable table, Color bg, com.lowagie.text.Font font,
                                 String text, int alignment) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "—", font));
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(new Color(0xF0, 0xF0, 0xF0));
        c.setPadding(6f);
        c.setHorizontalAlignment(alignment);
        table.addCell(c);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static double doubleValue(BigDecimal b) {
        return b == null ? 0.0 : b.doubleValue();
    }

    private static void setMoney(Row row, int col, BigDecimal v, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(doubleValue(v));
        c.setCellStyle(style);
    }

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
            case "partial"  -> "Μερ. Πληρωμένη";
            default         -> status;
        };
    }
}
