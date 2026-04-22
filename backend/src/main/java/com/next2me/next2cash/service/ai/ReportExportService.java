package com.next2me.next2cash.service.ai;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.next2me.next2cash.model.AiQueryHistory;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ReportExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Brand colors matching UI
    private static final DeviceRgb BLACK = new DeviceRgb(0, 0, 0);
    private static final DeviceRgb DARK_HEADER = new DeviceRgb(26, 35, 50);
    private static final DeviceRgb BRAND_TEAL = new DeviceRgb(79, 195, 161);
    private static final DeviceRgb MUTED = new DeviceRgb(108, 122, 138);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(208, 215, 222);
    private static final DeviceRgb ROW_ALT = new DeviceRgb(246, 248, 250);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);

    // ========================================================================
    // PDF GENERATION
    // ========================================================================

    public byte[] generatePdf(AiQueryHistory h) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.setMargins(50, 50, 50, 50);
            PdfFont regular = loadClasspathFont("/fonts/DejaVuSans.ttf");

            Paragraph brand = new Paragraph("NEXT2ME GROUP")
                .setFont(regular).setBold().setFontSize(22).setFontColor(DARK_HEADER)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2);
            doc.add(brand);

            Paragraph subtitle = new Paragraph("Next2Cash — AI Financial Analysis")
                .setFont(regular).setFontSize(11).setFontColor(BRAND_TEAL)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(24);
            doc.add(subtitle);

            doc.add(new LineSeparator(new SolidLine(2f)).setMarginBottom(18));

            Paragraph typeH = new Paragraph(safe(h.getAnalysisType()))
                .setFont(regular).setBold().setFontSize(20).setFontColor(BLACK)
                .setMarginBottom(4);
            doc.add(typeH);

            doc.add(metaParagraph(h, regular).setMarginBottom(2));
            String createdAt = h.getCreatedAt() != null ?
                "Δημιουργήθηκε: " + h.getCreatedAt().format(DATETIME_FMT) : "";
            doc.add(new Paragraph(createdAt)
                .setFont(regular).setFontSize(9).setFontColor(MUTED)
                .setMarginBottom(18));

            doc.add(new Paragraph("Ερώτηση:")
                .setFont(regular).setBold().setFontSize(11).setFontColor(BRAND_TEAL)
                .setMarginBottom(2));
            doc.add(new Paragraph(safe(h.getQuestion()))
                .setFont(regular).setFontSize(11).setFontColor(BLACK)
                .setMarginBottom(14));

            doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginBottom(14));

            renderMarkdownToPdf(safe(h.getAnswer()), doc, regular);

            doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(22).setMarginBottom(6));
            String footer = String.format("Powered by Claude %s  •  Tokens: %d in / %d out  •  Κόστος: €%.4f",
                safe(h.getModelUsed()), h.getInputTokens(), h.getOutputTokens(),
                h.getCostEur() == null ? 0.0 : h.getCostEur().setScale(4, RoundingMode.HALF_UP).doubleValue());
            doc.add(new Paragraph(footer)
                .setFont(regular).setFontSize(8).setFontColor(MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        }

        return baos.toByteArray();
    }

    private Paragraph metaParagraph(AiQueryHistory h, PdfFont font) {
        StringBuilder meta = new StringBuilder();
        meta.append("Περίοδος: ");
        if (h.getDateFrom() != null && h.getDateTo() != null) {
            meta.append(h.getDateFrom().format(DATE_FMT)).append(" — ").append(h.getDateTo().format(DATE_FMT));
        } else {
            meta.append(safe(h.getDateRangeLabel()));
        }
        meta.append("   •   Εταιρία: ").append(entityLabel(h.getEntityScope()));
        meta.append("   •   Εγγραφές: ").append(h.getRowsAnalyzed() == null ? 0 : h.getRowsAnalyzed());
        return new Paragraph(meta.toString())
            .setFont(font).setFontSize(10).setFontColor(MUTED);
    }

    private void renderMarkdownToPdf(String md, Document doc, PdfFont font) {
        String[] lines = md.split("\\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                doc.add(new Paragraph(" ").setFont(font).setFontSize(4));
                i++; continue;
            }

            if (trimmed.matches("^-{3,}$")) {
                doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(6).setMarginBottom(6));
                i++; continue;
            }

            if (trimmed.startsWith("### ")) {
                doc.add(new Paragraph(stripInline(trimmed.substring(4)))
                    .setFont(font).setBold().setFontSize(14).setFontColor(BLACK)
                    .setMarginTop(10).setMarginBottom(4));
                i++; continue;
            }
            if (trimmed.startsWith("## ")) {
                Paragraph p = new Paragraph(stripInline(trimmed.substring(3)))
                    .setFont(font).setBold().setFontSize(17).setFontColor(BLACK)
                    .setMarginTop(14).setMarginBottom(4)
                    .setPaddingBottom(2)
                    .setBorderBottom(new SolidBorder(BLACK, 1f));
                doc.add(p);
                i++; continue;
            }
            if (trimmed.startsWith("# ")) {
                Paragraph p = new Paragraph(stripInline(trimmed.substring(2)))
                    .setFont(font).setBold().setFontSize(22).setFontColor(BLACK)
                    .setMarginTop(16).setMarginBottom(6)
                    .setPaddingBottom(4)
                    .setBorderBottom(new SolidBorder(BLACK, 2f));
                doc.add(p);
                i++; continue;
            }

            if (trimmed.startsWith("|") && i + 1 < lines.length &&
                lines[i+1].trim().matches("^\\|[\\s\\-|:]+\\|$")) {
                List<String> header = splitPipeRow(trimmed);
                List<List<String>> rows = new ArrayList<>();
                i += 2;
                while (i < lines.length && lines[i].trim().startsWith("|")) {
                    rows.add(splitPipeRow(lines[i].trim()));
                    i++;
                }
                doc.add(buildPdfTable(header, rows, font));
                continue;
            }

            if (trimmed.matches("^[-*]\\s+.*")) {
                List<String> items = new ArrayList<>();
                while (i < lines.length && lines[i].trim().matches("^[-*]\\s+.*")) {
                    items.add(lines[i].trim().replaceFirst("^[-*]\\s+", ""));
                    i++;
                }
                for (String item : items) {
                    doc.add(new Paragraph("  •  " + stripInline(item))
                        .setFont(font).setFontSize(11).setFontColor(BLACK)
                        .setMarginLeft(10).setMarginBottom(2));
                }
                continue;
            }

            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                int n = 1;
                while (i < lines.length && lines[i].trim().matches("^\\d+\\.\\s+.*")) {
                    String item = lines[i].trim().replaceFirst("^\\d+\\.\\s+", "");
                    doc.add(new Paragraph("  " + n + ". " + stripInline(item))
                        .setFont(font).setFontSize(11).setFontColor(BLACK)
                        .setMarginLeft(10).setMarginBottom(2));
                    n++; i++;
                }
                continue;
            }

            doc.add(new Paragraph(stripInline(trimmed))
                .setFont(font).setFontSize(11).setFontColor(BLACK)
                .setMarginBottom(6));
            i++;
        }
    }

    private Table buildPdfTable(List<String> header, List<List<String>> rows, PdfFont font) {
        int cols = header.size();
        Table t = new Table(UnitValue.createPercentArray(cols))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginTop(8).setMarginBottom(12);

        for (String h : header) {
            Cell c = new Cell()
                .add(new Paragraph(stripInline(h))
                    .setFont(font).setBold().setFontSize(10).setFontColor(WHITE))
                .setBackgroundColor(DARK_HEADER)
                .setPadding(8)
                .setBorder(new SolidBorder(DARK_HEADER, 1f));
            t.addHeaderCell(c);
        }

        int idx = 0;
        for (List<String> row : rows) {
            boolean alt = (idx % 2 == 1);
            for (int k = 0; k < cols; k++) {
                String cellText = k < row.size() ? row.get(k) : "";
                Cell c = new Cell()
                    .add(new Paragraph(stripInline(cellText))
                        .setFont(font).setFontSize(10).setFontColor(BLACK))
                    .setPadding(7)
                    .setBorder(new SolidBorder(BORDER_COLOR, 0.5f));
                if (alt) c.setBackgroundColor(ROW_ALT);
                t.addCell(c);
            }
            idx++;
        }
        return t;
    }

    private PdfFont loadClasspathFont(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            return PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H);
        } catch (Exception e) {
            log.warn("Failed to load classpath font {}, using default: {}", path, e.getMessage());
            return PdfFontFactory.createFont();
        }
    }

    // ========================================================================
    // WORD GENERATION
    // ========================================================================

    public byte[] generateWord(AiQueryHistory h) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (XWPFDocument doc = new XWPFDocument()) {
            addWordParagraph(doc, "NEXT2ME GROUP", true, 22, "1A2332", ParagraphAlignment.CENTER);
            addWordParagraph(doc, "Next2Cash — AI Financial Analysis", false, 11, "4FC3A1", ParagraphAlignment.CENTER);
            addWordDivider(doc);

            addWordParagraph(doc, safe(h.getAnalysisType()), true, 20, "000000", null);

            StringBuilder meta = new StringBuilder();
            meta.append("Περίοδος: ");
            if (h.getDateFrom() != null && h.getDateTo() != null) {
                meta.append(h.getDateFrom().format(DATE_FMT)).append(" — ").append(h.getDateTo().format(DATE_FMT));
            } else {
                meta.append(safe(h.getDateRangeLabel()));
            }
            meta.append("  •  Εταιρία: ").append(entityLabel(h.getEntityScope()));
            meta.append("  •  Εγγραφές: ").append(h.getRowsAnalyzed() == null ? 0 : h.getRowsAnalyzed());
            addWordParagraph(doc, meta.toString(), false, 10, "6C7A8A", null);

            if (h.getCreatedAt() != null) {
                addWordParagraph(doc, "Δημιουργήθηκε: " + h.getCreatedAt().format(DATETIME_FMT),
                    false, 9, "6C7A8A", null);
            }

            addWordParagraph(doc, "Ερώτηση:", true, 11, "4FC3A1", null);
            addWordParagraph(doc, safe(h.getQuestion()), false, 11, "000000", null);
            addWordDivider(doc);

            renderMarkdownToWord(safe(h.getAnswer()), doc);

            addWordDivider(doc);
            String footer = String.format("Powered by Claude %s  •  Tokens: %d in / %d out  •  Κόστος: €%.4f",
                safe(h.getModelUsed()), h.getInputTokens(), h.getOutputTokens(),
                h.getCostEur() == null ? 0.0 : h.getCostEur().setScale(4, RoundingMode.HALF_UP).doubleValue());
            addWordParagraph(doc, footer, false, 8, "6C7A8A", ParagraphAlignment.CENTER);

            doc.write(baos);
        }
        return baos.toByteArray();
    }

    private void renderMarkdownToWord(String md, XWPFDocument doc) {
        String[] lines = md.split("\\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) { doc.createParagraph(); i++; continue; }

            if (trimmed.matches("^-{3,}$")) {
                addWordDivider(doc); i++; continue;
            }

            if (trimmed.startsWith("### ")) {
                addWordParagraph(doc, stripInline(trimmed.substring(4)), true, 14, "000000", null);
                i++; continue;
            }
            if (trimmed.startsWith("## ")) {
                XWPFParagraph p = addWordParagraph(doc, stripInline(trimmed.substring(3)), true, 17, "000000", null);
                p.setBorderBottom(Borders.SINGLE);
                i++; continue;
            }
            if (trimmed.startsWith("# ")) {
                XWPFParagraph p = addWordParagraph(doc, stripInline(trimmed.substring(2)), true, 22, "000000", null);
                p.setBorderBottom(Borders.THICK);
                i++; continue;
            }

            if (trimmed.startsWith("|") && i + 1 < lines.length &&
                lines[i+1].trim().matches("^\\|[\\s\\-|:]+\\|$")) {
                List<String> header = splitPipeRow(trimmed);
                List<List<String>> rows = new ArrayList<>();
                i += 2;
                while (i < lines.length && lines[i].trim().startsWith("|")) {
                    rows.add(splitPipeRow(lines[i].trim()));
                    i++;
                }
                buildWordTable(doc, header, rows);
                continue;
            }

            if (trimmed.matches("^[-*]\\s+.*")) {
                while (i < lines.length && lines[i].trim().matches("^[-*]\\s+.*")) {
                    String item = lines[i].trim().replaceFirst("^[-*]\\s+", "");
                    addWordParagraph(doc, "  •  " + stripInline(item), false, 11, "000000", null);
                    i++;
                }
                continue;
            }
            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                int n = 1;
                while (i < lines.length && lines[i].trim().matches("^\\d+\\.\\s+.*")) {
                    String item = lines[i].trim().replaceFirst("^\\d+\\.\\s+", "");
                    addWordParagraph(doc, "  " + n + ". " + stripInline(item), false, 11, "000000", null);
                    n++; i++;
                }
                continue;
            }

            addWordParagraph(doc, stripInline(trimmed), false, 11, "000000", null);
            i++;
        }
    }

    private void buildWordTable(XWPFDocument doc, List<String> header, List<List<String>> rows) {
        int cols = header.size();
        XWPFTable table = doc.createTable(rows.size() + 1, cols);

        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        CTTblWidth tblW = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setType(STTblWidth.PCT);
        tblW.setW(BigInteger.valueOf(5000));

        XWPFTableRow hr = table.getRow(0);
        for (int k = 0; k < cols; k++) {
            XWPFTableCell cell = hr.getCell(k);
            cell.removeParagraph(0);
            XWPFParagraph p = cell.addParagraph();
            XWPFRun r = p.createRun();
            r.setText(stripInline(header.get(k)));
            r.setBold(true);
            r.setFontSize(10);
            r.setColor("FFFFFF");
            r.setFontFamily("Arial");
            setCellShading(cell, "1A2332");
        }

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            List<String> row = rows.get(rowIdx);
            XWPFTableRow tr = table.getRow(rowIdx + 1);
            boolean alt = (rowIdx % 2 == 1);
            for (int k = 0; k < cols; k++) {
                XWPFTableCell cell = tr.getCell(k);
                cell.removeParagraph(0);
                XWPFParagraph p = cell.addParagraph();
                XWPFRun r = p.createRun();
                r.setText(k < row.size() ? stripInline(row.get(k)) : "");
                r.setFontSize(10);
                r.setColor("000000");
                r.setFontFamily("Arial");
                if (alt) setCellShading(cell, "F6F8FA");
            }
        }
    }

    private void setCellShading(XWPFTableCell cell, String hexColor) {
        CTShd shd = cell.getCTTc().addNewTcPr().addNewShd();
        shd.setVal(STShd.CLEAR);
        shd.setColor("auto");
        shd.setFill(hexColor);
    }

    private XWPFParagraph addWordParagraph(XWPFDocument doc, String text, boolean bold, int size, String hexColor, ParagraphAlignment align) {
        XWPFParagraph p = doc.createParagraph();
        if (align != null) p.setAlignment(align);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
        r.setFontSize(size);
        r.setColor(hexColor);
        r.setFontFamily("Arial");
        return p;
    }

    private void addWordDivider(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setBorderBottom(Borders.SINGLE);
    }

    // ========================================================================
    // MARKDOWN HELPERS
    // ========================================================================

    private List<String> splitPipeRow(String row) {
        String t = row.trim();
        if (t.startsWith("|")) t = t.substring(1);
        if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String s : t.split("\\|")) cells.add(s.trim());
        return cells;
    }

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*([^*]+)\\*");
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`]+)`");

    private String stripInline(String s) {
        if (s == null) return "";
        String out = s;
        out = BOLD_PATTERN.matcher(out).replaceAll("$1");
        out = ITALIC_PATTERN.matcher(out).replaceAll("$1");
        out = CODE_PATTERN.matcher(out).replaceAll("$1");
        return out;
    }

    private String entityLabel(String scope) {
        if (scope == null || "all".equals(scope)) return "Όλες";
        if ("next2me".equals(scope)) return "Next2me";
        if ("house".equals(scope)) return "House";
        return scope;
    }

    private String safe(String s) { return s == null ? "" : s; }
}