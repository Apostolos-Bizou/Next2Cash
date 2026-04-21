package com.next2me.next2cash.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.next2me.next2cash.dto.AiAnalysisRequest;
import com.next2me.next2cash.dto.AiAnalysisResponse;
import com.next2me.next2cash.model.AiQueryHistory;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.AiQueryHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final AnthropicClient anthropicClient;
    private final AiQueryHistoryRepository historyRepo;

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Value("${next2cash.ai.usd-to-eur:0.92}")
    private BigDecimal usdToEur;

    @Value("${next2cash.ai.pricing.input-per-million:3.0}")
    private BigDecimal inputPricePerMillionUsd;

    @Value("${next2cash.ai.pricing.output-per-million:15.0}")
    private BigDecimal outputPricePerMillionUsd;

    @Value("${next2cash.ai.max-tokens:4000}")
    private int maxTokens;

    @Transactional
    public AiAnalysisResponse analyze(AiAnalysisRequest req, User user) {
        long startMs = System.currentTimeMillis();

        DateRange range = resolveDateRange(req);
        List<UUID> entityIds = resolveEntityIds(req.getEntityScope());
        String tier = decideTier(range);

        String contextJson;
        int rowsAnalyzed;
        if (tier.equals("tier1_summary")) {
            Map<String, Object> summary = fetchSummary(entityIds, range);
            contextJson = toJson(summary);
            Object totalObj = summary.get("total_rows");
            rowsAnalyzed = totalObj == null ? 0 : ((Number) totalObj).intValue();
        } else {
            List<Map<String, Object>> rows = fetchRawRows(entityIds, range);
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("transactions", rows);
            ctx.put("total_rows", rows.size());
            ctx.put("date_from", range.from.toString());
            ctx.put("date_to", range.to.toString());
            if (tier.equals("tier3_full")) {
                ctx.put("note", "FULL DATASET — all years, all transactions");
            }
            contextJson = toJson(ctx);
            rowsAnalyzed = rows.size();
        }

        String systemPrompt = buildSystemPrompt(req.getAnalysisType(), range, req.getEntityScope());
        String userMessage = buildUserMessage(req.getQuestion(), contextJson);

        log.info("AI analysis: tier={}, rows={}, promptLength={}",
                tier, rowsAnalyzed, userMessage.length());

        AnthropicClient.ClaudeResult claude = anthropicClient.createMessage(
                systemPrompt, userMessage, maxTokens);

        BigDecimal costUsd = calculateCost(claude.getInputTokens(), claude.getOutputTokens());
        BigDecimal costEur = costUsd.multiply(usdToEur).setScale(6, RoundingMode.HALF_UP);

        int processingMs = (int) (System.currentTimeMillis() - startMs);

        AiQueryHistory h = new AiQueryHistory();
        h.setUserId(user.getId());
        h.setEntityScope(req.getEntityScope());
        h.setAnalysisType(req.getAnalysisType());
        h.setDateRangeLabel(req.getDateRange());
        h.setDateFrom(range.from);
        h.setDateTo(range.to);
        h.setQuestion(req.getQuestion());
        h.setAnswer(claude.getAnswer());
        h.setModelUsed(claude.getModelUsed());
        h.setTier(tier);
        h.setRowsAnalyzed(rowsAnalyzed);
        h.setInputTokens(claude.getInputTokens());
        h.setOutputTokens(claude.getOutputTokens());
        h.setCostUsd(costUsd);
        h.setCostEur(costEur);
        h.setProcessingTimeMs(processingMs);
        AiQueryHistory saved = historyRepo.save(h);

        return AiAnalysisResponse.builder()
                .success(true)
                .historyId(saved.getId())
                .answer(claude.getAnswer())
                .tier(tier)
                .rowsAnalyzed(rowsAnalyzed)
                .inputTokens(claude.getInputTokens())
                .outputTokens(claude.getOutputTokens())
                .costUsd(costUsd)
                .costEur(costEur)
                .processingTimeMs(processingMs)
                .entityScope(req.getEntityScope())
                .dateRangeLabel(req.getDateRange())
                .dateFrom(range.from)
                .dateTo(range.to)
                .analysisType(req.getAnalysisType())
                .question(req.getQuestion())
                .modelUsed(claude.getModelUsed())
                .build();
    }

    private DateRange resolveDateRange(AiAnalysisRequest req) {
        LocalDate today = LocalDate.now();
        String label = req.getDateRange() == null ? "ytd" : req.getDateRange();

        switch (label) {
            case "last_30_days":    return new DateRange(today.minusDays(30), today);
            case "last_3_months":   return new DateRange(today.minusMonths(3), today);
            case "last_12_months":  return new DateRange(today.minusMonths(12), today);
            case "ytd":             return new DateRange(today.withDayOfYear(1), today);
            case "last_year":       return new DateRange(today.minusYears(1).withDayOfYear(1),
                                                          today.minusYears(1).withMonth(12).withDayOfMonth(31));
            case "all_data":        return new DateRange(LocalDate.of(2017, 1, 1), today);
            case "custom":
                LocalDate from = LocalDate.parse(req.getCustomFrom(), DateTimeFormatter.ISO_DATE);
                LocalDate to = LocalDate.parse(req.getCustomTo(), DateTimeFormatter.ISO_DATE);
                return new DateRange(from, to);
            default:
                if (label.startsWith("year_")) {
                    int year = Integer.parseInt(label.substring(5));
                    return new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
                }
                return new DateRange(today.withDayOfYear(1), today);
        }
    }

    private List<UUID> resolveEntityIds(String scope) {
        if (scope == null) scope = "all";
        switch (scope) {
            case "next2me": return List.of(UUID.fromString("58202b71-4ddb-45c9-8e3c-39e816bde972"));
            case "house":   return List.of(UUID.fromString("dea1f32c-7b30-4981-b625-633da9dbe71e"));
            case "all":
            default:        return List.of(
                    UUID.fromString("58202b71-4ddb-45c9-8e3c-39e816bde972"),
                    UUID.fromString("dea1f32c-7b30-4981-b625-633da9dbe71e")
            );
        }
    }

    private String decideTier(DateRange range) {
        long days = range.from.until(range.to, java.time.temporal.ChronoUnit.DAYS);
        if (days > 365 * 3) return "tier3_full";
        return "tier2_range";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchRawRows(List<UUID> entityIds, DateRange range) {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < entityIds.size(); i++) {
            if (i > 0) csv.append(",");
            csv.append("'").append(entityIds.get(i)).append("'");
        }
        String sql = "SELECT t.id, t.entity_number, e.code AS entity, t.type, t.doc_date, " +
                "t.amount, t.counterparty, t.category, t.subcategory, t.description, " +
                "t.payment_method, t.payment_status, t.record_status " +
                "FROM transactions t LEFT JOIN entities e ON e.id = t.entity_id " +
                "WHERE t.entity_id IN (" + csv.toString() + ") " +
                "AND t.doc_date BETWEEN :from AND :to " +
                "AND t.record_status = 'active' " +
                "ORDER BY t.doc_date DESC, t.id DESC";

        List<Object[]> results = em.createNativeQuery(sql)
                .setParameter("from", range.from)
                .setParameter("to", range.to)
                .getResultList();

        List<Map<String, Object>> rows = new ArrayList<>(results.size());
        for (Object[] r : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("entity_number", r[1]);
            m.put("entity", r[2]);
            m.put("type", r[3]);
            m.put("date", r[4] == null ? null : r[4].toString());
            m.put("amount", r[5]);
            m.put("counterparty", r[6]);
            m.put("category", r[7]);
            m.put("subcategory", r[8]);
            m.put("description", r[9]);
            m.put("payment_method", r[10]);
            m.put("payment_status", r[11]);
            m.put("record_status", r[12]);
            rows.add(m);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSummary(List<UUID> entityIds, DateRange range) {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < entityIds.size(); i++) {
            if (i > 0) csv.append(",");
            csv.append("'").append(entityIds.get(i)).append("'");
        }
        String sql = "SELECT TO_CHAR(t.doc_date, 'YYYY-MM') AS month, " +
                "t.type, t.category, SUM(t.amount) AS total, COUNT(*) AS cnt " +
                "FROM transactions t " +
                "WHERE t.entity_id IN (" + csv.toString() + ") " +
                "AND t.doc_date BETWEEN :from AND :to " +
                "AND t.record_status = 'active' " +
                "GROUP BY month, t.type, t.category " +
                "ORDER BY month, t.type, t.category";

        List<Object[]> results = em.createNativeQuery(sql)
                .setParameter("from", range.from)
                .setParameter("to", range.to)
                .getResultList();

        List<Map<String, Object>> aggregates = new ArrayList<>();
        int totalCount = 0;
        for (Object[] r : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", r[0]);
            m.put("type", r[1]);
            m.put("category", r[2]);
            m.put("total_amount", r[3]);
            m.put("transaction_count", r[4]);
            aggregates.add(m);
            totalCount += ((Number) r[4]).intValue();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("aggregates", aggregates);
        out.put("total_rows", totalCount);
        out.put("date_from", range.from.toString());
        out.put("date_to", range.to.toString());
        return out;
    }

    private String buildSystemPrompt(String analysisType, DateRange range, String scope) {
        String scopeDesc;
        if ("all".equals(scope)) scopeDesc = "όλες τις εταιρείες (next2me + house)";
        else if ("next2me".equals(scope)) scopeDesc = "την εταιρεία next2me";
        else scopeDesc = "την οικία (house)";

        return "Είσαι ένας έμπειρος CFO financial analyst για την εταιρεία Next2me Group. " +
               "Απαντάς ΠΑΝΤΑ στα Ελληνικά με επαγγελματικό αλλά κατανοητό ύφος. " +
               "Ο τύπος ανάλυσης που σου ζητείται: " + analysisType + ". " +
               "Περίοδος: " + range.from + " έως " + range.to + ". " +
               "Δεδομένα: " + scopeDesc + ". " +
               "Ο χρήστης είναι ο CEO. Δώσε καθαρές, δομημένες, με αριθμούς απαντήσεις. " +
               "Χρησιμοποίησε Markdown για headings, λίστες και έμφαση. " +
               "Αν δεν έχεις αρκετά δεδομένα, πες το ρητά.";
    }

    private String buildUserMessage(String question, String contextJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ερώτηση: ").append(question).append("\n\n");
        sb.append("Δεδομένα σε JSON (financial transactions):\n");
        sb.append(contextJson);
        sb.append("\n\nΠαρακαλώ απάντησε στα Ελληνικά με βάση τα παραπάνω δεδομένα.");
        return sb.toString();
    }

    private BigDecimal calculateCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = inputPricePerMillionUsd
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = outputPricePerMillionUsd
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    private String toJson(Object obj) {
        try {
            return jsonMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static class DateRange {
        final LocalDate from;
        final LocalDate to;
        DateRange(LocalDate f, LocalDate t) { this.from = f; this.to = t; }
    }
}
