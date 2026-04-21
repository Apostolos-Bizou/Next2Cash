package com.next2me.next2cash.service.ai;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.io.font.PdfEncodings;
import com.next2me.next2cash.model.AiQueryHistory;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class ReportExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DeviceRgb BRAND_PRIMARY = new DeviceRgb(79, 195, 161);
    private static final DeviceRgb BRAND_DARK = new DeviceRgb(26, 47, 69);
    private static final DeviceRgb BRAND_MUTED = new DeviceRgb(136, 153, 170);

    public byte[] generatePdf(AiQueryHistory h) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            PdfFont regular;
            PdfFont bold;
            try {
                regular = PdfFontFactory.createFont("C:/Windows/Fonts/arial.ttf", PdfEncodings.IDENTITY_H);
                bold = PdfFontFactory.createFont("C:/Windows/Fonts/arialbd.ttf", PdfEncodings.IDENTITY_H);
            } catch (Exception e) {
                log.warn("Arial not found, fallback: {}", e.getMessage());
                regular = PdfFontFactory.createFont();
                bold = PdfFontFactory.createFont();
            }

            Paragraph title = new Paragraph("NEXT2ME GROUP")
                .setFont(bold).setFontSize(24).setFontColor(BRAND_DARK)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20).setMarginBottom(0);
            doc.add(title);

            Paragraph subtitle = new Paragraph("Next2Cash — AI Financial Analysis")
                .setFont(regular).setFontSize(12).setFontColor(BRAND_PRIMARY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(30);
            doc.add(subtitle);

            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(2f))
                .setMarginBottom(20));

            Paragraph typeHeading = new Paragraph(safe(h.getAnalysisType()))
                .setFont(bold).setFontSize(18).setFontColor(BRAND_DARK)
                .setMarginBottom(6);
            doc.add(typeHeading);

            StringBuilder meta = new StringBuilder();
            meta.append("Περίοδος: ");
            if (h.getDateFrom() != null && h.getDateTo() != null) {
                meta.append(h.getDateFrom().format(DATE_FMT))
                    .append(" — ")
                    .append(h.getDateTo().format(DATE_FMT));
            } else {
                meta.append(safe(h.getDateRangeLabel()));
            }
            meta.append("   •   Εταιρία: ").append(entityLabel(h.getEntityScope()));
            meta.append("   •   Εγγραφές: ").append(h.getRowsAnalyzed());

            doc.add(new Paragraph(meta.toString())
                .setFont(regular).setFontSize(10).setFontColor(BRAND_MUTED)
                .setMarginBottom(4));

            doc.add(new Paragraph("Δημιουργήθηκε: " +
                    (h.getCreatedAt() != null ? h.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : ""))
                .setFont(regular).setFontSize(9).setFontColor(BRAND_MUTED)
                .setMarginBottom(20));

            doc.add(new Paragraph("Ερώτηση:")
                .setFont(bold).setFontSize(11).setFontColor(BRAND_PRIMARY)
                .setMarginBottom(4));
            doc.add(new Paragraph(safe(h.getQuestion()))
                .setFont(regular).setFontSize(11).setFontColor(BRAND_DARK)
                .setMarginBottom(16));

            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f))
                .setMarginBottom(16));

            String[] lines = safe(h.getAnswer()).split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    doc.add(new Paragraph(" ").setFont(regular).setFontSize(6));
                    continue;
                }
                if (trimmed.startsWith("# ")) {
                    doc.add(new Paragraph(trimmed.substring(2))
                        .setFont(bold).setFontSize(16).setFontColor(BRAND_DARK)
                        .setMarginTop(10).setMarginBottom(6));
                } else if (trimmed.startsWith("## ")) {
                    doc.add(new Paragraph(trimmed.substring(3))
                        .setFont(bold).setFontSize(13).setFontColor(BRAND_DARK)
                        .setMarginTop(8).setMarginBottom(4));
                } else if (trimmed.startsWith("### ")) {
                    doc.add(new Paragraph(trimmed.substring(4))
                        .setFont(bold).setFontSize(12).setFontColor(BRAND_PRIMARY)
                        .setMarginTop(6).setMarginBottom(3));
                } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    doc.add(new Paragraph("  •  " + stripMd(trimmed.substring(2)))
                        .setFont(regular).setFontSize(10).setFontColor(BRAND_DARK)
                        .setMarginBottom(2));
                } else {
                    doc.add(new Paragraph(stripMd(trimmed))
                        .setFont(regular).setFontSize(10).setFontColor(BRAND_DARK)
                        .setMarginBottom(4));
                }
            }

            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f))
                .setMarginTop(20).setMarginBottom(6));

            String footer = String.format("Powered by Claude %s  •  Tokens: %d in / %d out  •  Κόστος: €%.4f",
                    safe(h.getModelUsed()), h.getInputTokens(), h.getOutputTokens(),
                    h.getCostEur() == null ? 0.0 : h.getCostEur().setScale(4, RoundingMode.HALF_UP).doubleValue());
            doc.add(new Paragraph(footer)
                .setFont(regular).setFontSize(8).setFontColor(BRAND_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        }

        return baos.toByteArray();
    }

    public byte[] generateWord(AiQueryHistory h) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = title.createRun();
            tr.setText("NEXT2ME GROUP");
            tr.setBold(true);
            tr.setFontSize(24);
            tr.setColor("1A2F45");

            XWPFParagraph sub = doc.createParagraph();
            sub.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun sr = sub.createRun();
            sr.setText("Next2Cash — AI Financial Analysis");
            sr.setFontSize(12);
            sr.setColor("4FC3A1");

            addDivider(doc);

            XWPFParagraph ah = doc.createParagraph();
            XWPFRun ahr = ah.createRun();
            ahr.setText(safe(h.getAnalysisType()));
            ahr.setBold(true);
            ahr.setFontSize(18);
            ahr.setColor("1A2F45");

            XWPFParagraph metaP = doc.createParagraph();
            XWPFRun metaRun = metaP.createRun();
            StringBuilder meta = new StringBuilder();
            meta.append("Περίοδος: ");
            if (h.getDateFrom() != null && h.getDateTo() != null) {
                meta.append(h.getDateFrom().format(DATE_FMT)).append(" — ").append(h.getDateTo().format(DATE_FMT));
            } else { meta.append(safe(h.getDateRangeLabel())); }
            meta.append("  •  Εταιρία: ").append(entityLabel(h.getEntityScope()));
            meta.append("  •  Εγγραφές: ").append(h.getRowsAnalyzed());
            metaRun.setText(meta.toString());
            metaRun.setFontSize(10);
            metaRun.setColor("8899AA");

            XWPFParagraph dateP = doc.createParagraph();
            XWPFRun dateRun = dateP.createRun();
            dateRun.setText("Δημιουργήθηκε: " +
                    (h.getCreatedAt() != null ? h.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : ""));
            dateRun.setFontSize(9);
            dateRun.setColor("8899AA");

            XWPFParagraph qLabel = doc.createParagraph();
            XWPFRun ql = qLabel.createRun();
            ql.setText("Ερώτηση:");
            ql.setBold(true);
            ql.setFontSize(11);
            ql.setColor("4FC3A1");

            XWPFParagraph qp = doc.createParagraph();
            XWPFRun qpr = qp.createRun();
            qpr.setText(safe(h.getQuestion()));
            qpr.setFontSize(11);

            addDivider(doc);

            String[] lines = safe(h.getAnswer()).split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) { doc.createParagraph(); continue; }
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                if (trimmed.startsWith("# ")) {
                    r.setText(trimmed.substring(2)); r.setBold(true); r.setFontSize(16); r.setColor("1A2F45");
                } else if (trimmed.startsWith("## ")) {
                    r.setText(trimmed.substring(3)); r.setBold(true); r.setFontSize(13); r.setColor("1A2F45");
                } else if (trimmed.startsWith("### ")) {
                    r.setText(trimmed.substring(4)); r.setBold(true); r.setFontSize(12); r.setColor("4FC3A1");
                } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    r.setText("  •  " + stripMd(trimmed.substring(2))); r.setFontSize(10);
                } else {
                    r.setText(stripMd(trimmed)); r.setFontSize(10);
                }
            }

            addDivider(doc);
            XWPFParagraph footer = doc.createParagraph();
            footer.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun fr = footer.createRun();
            fr.setText(String.format("Powered by Claude %s  •  Tokens: %d in / %d out  •  Κόστος: €%.4f",
                    safe(h.getModelUsed()), h.getInputTokens(), h.getOutputTokens(),
                    h.getCostEur() == null ? 0.0 : h.getCostEur().setScale(4, RoundingMode.HALF_UP).doubleValue()));
            fr.setFontSize(8);
            fr.setColor("8899AA");

            doc.write(baos);
        }
        return baos.toByteArray();
    }

    private void addDivider(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setBorderBottom(Borders.SINGLE);
    }

    private String entityLabel(String scope) {
        if (scope == null) return "Όλες";
        if ("next2me".equals(scope)) return "Next2me";
        if ("house".equals(scope)) return "House";
        if ("all".equals(scope)) return "Όλες";
        return scope;
    }

    private String stripMd(String s) {
        if (s == null) return "";
        String out = s;
        // Strip **bold**
        out = out.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        // Strip *italic*
        out = out.replaceAll("\\*([^*]+)\\*", "$1");
        // Strip `code`
        out = out.replaceAll("`([^`]+)`", "$1");
        return out;
    }

    private String safe(String s) { return s == null ? "" : s; }
}
