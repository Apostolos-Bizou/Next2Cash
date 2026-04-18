package com.next2me.next2cash.service;

import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.ConfigRepository;
import com.next2me.next2cash.repository.PaymentRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CardService — user-defined "karteles" with matching rules.
 *
 * Phase H v2 architecture:
 *   - Cards are rows in the existing config table (configType='card').
 *   - Each card has a parent_key in format "<filterType>:<filterValue>".
 *   - Filter types: search, category, subcategory, counterparty.
 *   - Rule engine filters transactions in pure Java (no custom SQL).
 *
 * Phase K additions:
 *   - Payments are joined in via transaction_id and rendered alongside the
 *     matched transactions as "CardRow" entries (flat shape, tagged with
 *     recordSource=TRANSACTION or PAYMENT).
 *   - Summary KPIs now include payments count + sum (6th tile).
 *   - Orphan payments (transaction_id IS NULL) are NOT included per scope.
 */
@Service
@RequiredArgsConstructor
public class CardService {

    private static final String CARD_TYPE = "card";
    private static final String RECORD_STATUS_ACTIVE = "active";

    // Accepts: search:..., category:..., subcategory:..., counterparty:...
    private static final Pattern RULE_PATTERN =
        Pattern.compile("^(search|category|subcategory|counterparty):.+$");

    private final ConfigRepository configRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    // ─── CRUD ───

    public List<Config> listCards(UUID entityId) {
        return configRepository
            .findByEntityIdAndConfigTypeAndIsActiveTrueOrderBySortOrderAsc(entityId, CARD_TYPE);
    }

    public Config getCard(UUID cardId, UUID entityId) {
        Config card = configRepository.findByIdAndEntityId(cardId, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        if (!CARD_TYPE.equals(card.getConfigType()) || !Boolean.TRUE.equals(card.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
        }
        return card;
    }

    public Config createCard(UUID entityId, Map<String, Object> payload) {
        String configKey   = requireString(payload, "configKey");
        String configValue = requireString(payload, "configValue");
        String parentKey   = requireString(payload, "parentKey");
        validateRule(parentKey);

        Config c = new Config();
        c.setEntityId(entityId);
        c.setConfigType(CARD_TYPE);
        c.setConfigKey(configKey.trim());
        c.setConfigValue(configValue.trim());
        c.setParentKey(parentKey.trim());
        c.setIcon(optString(payload, "icon"));
        c.setSortOrder(optInt(payload, "sortOrder", 0));
        c.setIsActive(Boolean.TRUE);
        return configRepository.save(c);
    }

    public Config updateCard(UUID cardId, UUID entityId, Map<String, Object> payload) {
        Config card = getCard(cardId, entityId);

        if (payload.containsKey("configKey")) {
            String v = requireString(payload, "configKey");
            card.setConfigKey(v.trim());
        }
        if (payload.containsKey("configValue")) {
            String v = requireString(payload, "configValue");
            card.setConfigValue(v.trim());
        }
        if (payload.containsKey("parentKey")) {
            String v = requireString(payload, "parentKey");
            validateRule(v);
            card.setParentKey(v.trim());
        }
        if (payload.containsKey("icon")) {
            card.setIcon(optString(payload, "icon"));
        }
        if (payload.containsKey("sortOrder")) {
            card.setSortOrder(optInt(payload, "sortOrder", 0));
        }

        return configRepository.save(card);
    }

    public void softDeleteCard(UUID cardId, UUID entityId) {
        Config card = getCard(cardId, entityId);
        card.setIsActive(Boolean.FALSE);
        configRepository.save(card);
    }

    // ─── Rule engine ───

    /**
     * Phase H v2 (legacy): transactions-only. Kept for CardExportService
     * which still uses the simple Transaction-based shape.
     * Will be migrated to getRowsForCard in Phase K.3.
     */
    public CardTransactions getTransactionsForCard(
            UUID cardId, UUID entityId, int limit, int offset) {

        Config card = getCard(cardId, entityId);
        RuleContext ctx = parseRule(card);
        List<Transaction> matched = filterTransactions(entityId, ctx);

        int total = matched.size();
        int safeLimit  = Math.max(1, Math.min(limit, 10000));
        int safeOffset = Math.max(0, Math.min(offset, total));
        int end        = Math.min(safeOffset + safeLimit, total);

        List<Transaction> page = matched.subList(safeOffset, end);
        return new CardTransactions(card, page, total, safeLimit, safeOffset);
    }

    /**
     * Phase K: unified timeline — transactions + payments merged into one list.
     *
     * Flow:
     *   1. Parse card rule (filterType, values).
     *   2. Load all active transactions for entity.
     *   3. Filter transactions by rule → matched transactions.
     *   4. Load payments linked to those transaction IDs.
     *   5. Merge into CardRow list, sorted by date DESC (then id DESC).
     *   6. Paginate.
     *
     * Each CardRow tags itself as TRANSACTION or PAYMENT so the frontend
     * can style payment rows distinctly (grey background, ↓ prefix).
     */
    public CardRows getRowsForCard(
            UUID cardId, UUID entityId, int limit, int offset) {

        Config card = getCard(cardId, entityId);
        RuleContext ctx = parseRule(card);

        List<Transaction> matchedTxns = filterTransactions(entityId, ctx);
        List<Integer> matchedIds = matchedTxns.stream().map(Transaction::getId).toList();

        List<Payment> linkedPayments = matchedIds.isEmpty()
            ? Collections.emptyList()
            : paymentRepository.findByEntityIdAndTransactionIdIn(entityId, matchedIds);

        List<CardRow> rows = new ArrayList<>(matchedTxns.size() + linkedPayments.size());
        for (Transaction t : matchedTxns) rows.add(CardRow.ofTransaction(t));
        for (Payment p : linkedPayments)  rows.add(CardRow.ofPayment(p));

        // Sort by date DESC, then id DESC (newer rows first).
        rows.sort((a, b) -> {
            int byDate = b.docDate().compareTo(a.docDate());
            if (byDate != 0) return byDate;
            return Integer.compare(b.id(), a.id());
        });

        int total = rows.size();
        int safeLimit  = Math.max(1, Math.min(limit, 10000));
        int safeOffset = Math.max(0, Math.min(offset, total));
        int end        = Math.min(safeOffset + safeLimit, total);

        List<CardRow> page = rows.subList(safeOffset, end);
        return new CardRows(card, page, total, safeLimit, safeOffset);
    }

    private List<Transaction> filterTransactions(UUID entityId, RuleContext ctx) {
        List<Transaction> all = transactionRepository
            .findByEntityIdAndRecordStatusOrderByDocDateDesc(entityId, RECORD_STATUS_ACTIVE);
        return all.stream()
            .filter(t -> matches(t, ctx.filterType(), ctx.values()))
            .toList();
    }

    private RuleContext parseRule(Config card) {
        String[] parts = card.getParentKey().split(":", 2);
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Card has malformed rule: " + card.getParentKey());
        }
        String filterType = parts[0];
        List<String> values = Arrays.stream(parts[1].split(","))
            .map(s -> s.trim().toUpperCase())
            .filter(s -> !s.isEmpty())
            .toList();
        return new RuleContext(filterType, values);
    }

    private boolean matches(Transaction t, String filterType, List<String> values) {
        switch (filterType) {
            case "search":
                String desc = safeUpper(t.getDescription());
                if (desc.isEmpty()) return false;
                return values.stream().anyMatch(v -> {
                    String[] words = v.split("\\s+");
                    for (String w : words) {
                        if (w.isEmpty()) continue;
                        if (!desc.contains(w)) return false;
                    }
                    return true;
                });
            case "category":     return values.contains(safeUpper(t.getCategory()));
            case "subcategory":  return values.contains(safeUpper(t.getSubcategory()));
            case "counterparty": return values.contains(safeUpper(t.getCounterparty()));
            default:             return false;
        }
    }

    // ─── Helpers ───

    private String safeUpper(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return "";
        String decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD);
        String stripped   = decomposed.replaceAll("\\p{M}+", "");
        return stripped.toUpperCase();
    }

    private void validateRule(String rule) {
        if (rule == null || !RULE_PATTERN.matcher(rule.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid rule format. Expected '<search|category|subcategory|counterparty>:<value>', got: " + rule);
        }
    }

    private String requireString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null || !(v instanceof String) || ((String) v).trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Field '" + key + "' is required");
        }
        return (String) v;
    }

    private String optString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private int optInt(Map<String, Object> payload, String key, int defaultValue) {
        Object v = payload.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // ─── Summary (KPIs) ───

    /**
     * Phase K: KPI aggregates now include payments.
     *
     * Transaction-based (unchanged):
     *   - total   : SUM(amount) where type='expense'
     *   - paid    : SUM(amountPaid) where type='expense'
     *   - unpaid  : SUM(amountRemaining) where type='expense'
     *   - income  : SUM(amount) where type='income'
     *   - urgent  : SUM(amountRemaining) where type='expense' AND paymentStatus='urgent'
     *
     * Phase K new (6th KPI):
     *   - paymentsTotal : SUM(amount) over payments linked to matched transactions
     *   - countPayments : count of those payments
     */
    public CardSummary getCardSummary(UUID cardId, UUID entityId) {
        Config card = getCard(cardId, entityId);
        RuleContext ctx = parseRule(card);

        List<Transaction> all = transactionRepository
            .findByEntityIdAndRecordStatusOrderByDocDateDesc(entityId, RECORD_STATUS_ACTIVE);

        // Init with scale=2 so empty cards still return "0.00" not "0"
        BigDecimal totalExpense  = new BigDecimal("0.00");
        BigDecimal totalPaid     = new BigDecimal("0.00");
        BigDecimal totalUnpaid   = new BigDecimal("0.00");
        BigDecimal totalIncome   = new BigDecimal("0.00");
        BigDecimal totalUrgent   = new BigDecimal("0.00");
        int countExpense = 0, countPaid = 0, countUnpaid = 0, countIncome = 0, countUrgent = 0;

        List<Integer> matchedIds = new ArrayList<>();

        for (Transaction t : all) {
            if (!matches(t, ctx.filterType(), ctx.values())) continue;
            matchedIds.add(t.getId());

            BigDecimal amount    = t.getAmount()          != null ? t.getAmount()          : BigDecimal.ZERO;
            BigDecimal paid      = t.getAmountPaid()      != null ? t.getAmountPaid()      : BigDecimal.ZERO;
            BigDecimal remaining = t.getAmountRemaining() != null ? t.getAmountRemaining() : BigDecimal.ZERO;

            if ("expense".equals(t.getType())) {
                totalExpense = totalExpense.add(amount);
                countExpense++;

                totalPaid = totalPaid.add(paid);
                if (paid.signum() > 0) countPaid++;

                totalUnpaid = totalUnpaid.add(remaining);
                if (remaining.signum() > 0) countUnpaid++;

                if ("urgent".equals(t.getPaymentStatus())) {
                    totalUrgent = totalUrgent.add(remaining);
                    countUrgent++;
                }
            } else if ("income".equals(t.getType())) {
                totalIncome = totalIncome.add(amount);
                countIncome++;
            }
        }

        // Phase K: aggregate payments linked to matched transactions
        BigDecimal paymentsTotal = new BigDecimal("0.00");
        int countPayments = 0;
        if (!matchedIds.isEmpty()) {
            List<Payment> payments = paymentRepository
                .findByEntityIdAndTransactionIdIn(entityId, matchedIds);
            for (Payment p : payments) {
                BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                paymentsTotal = paymentsTotal.add(amt);
                countPayments++;
            }
        }

        return new CardSummary(
            card,
            totalExpense, totalPaid, totalUnpaid, totalIncome, totalUrgent, paymentsTotal,
            countExpense, countPaid, countUnpaid, countIncome, countUrgent, countPayments
        );
    }

    // ─── DTOs ───

    /** Internal rule parse result. */
    private record RuleContext(String filterType, List<String> values) {}

    /**
     * Unified row shape — represents either a Transaction or a Payment.
     * Frontend uses recordSource to choose styling (grey background + ↓ for PAYMENT).
     *
     * Fields map to the legacy UI columns:
     *   id, docDate, description, category/subcategory, paymentMethod,
     *   amount, amountPaid, amountRemaining, paymentStatus, paymentDate.
     *
     * For payments, most "transaction-only" fields are derived:
     *   - type          : "payment-in" / "payment-out" (from payment_type)
     *   - amountPaid    : equals amount (the payment IS the settlement)
     *   - amountRemaining : zero
     *   - paymentStatus : "paid"
     *   - category/subcategory/counterparty : copied from parent if available,
     *                    otherwise null (controller layer handles fallback)
     */
    public record CardRow(
        String recordSource,     // "TRANSACTION" | "PAYMENT"
        Integer id,
        Integer entityNumber,
        java.time.LocalDate docDate,
        String type,
        String counterparty,
        String category,
        String subcategory,
        String description,
        BigDecimal amount,
        BigDecimal amountPaid,
        BigDecimal amountRemaining,
        String paymentStatus,
        String paymentMethod,
        java.time.LocalDate paymentDate,
        String recordStatus,
        Integer parentTransactionId  // only set for PAYMENT rows
    ) {
        public static CardRow ofTransaction(Transaction t) {
            return new CardRow(
                "TRANSACTION",
                t.getId(),
                t.getEntityNumber(),
                t.getDocDate(),
                t.getType(),
                t.getCounterparty(),
                t.getCategory(),
                t.getSubcategory(),
                t.getDescription(),
                t.getAmount(),
                t.getAmountPaid(),
                t.getAmountRemaining(),
                t.getPaymentStatus(),
                t.getPaymentMethod(),
                t.getPaymentDate(),
                t.getRecordStatus(),
                null
            );
        }

        public static CardRow ofPayment(Payment p) {
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            String pseudoType = "incoming".equals(p.getPaymentType()) ? "payment-in" : "payment-out";
            return new CardRow(
                "PAYMENT",
                p.getId(),
                null,
                p.getPaymentDate(),
                pseudoType,
                p.getCounterparty(),
                null,  // category — not meaningful on payment rows
                null,  // subcategory
                p.getDescription(),
                amt,
                amt,                        // amountPaid = amount (it IS the settlement)
                BigDecimal.ZERO,            // amountRemaining
                "paid",                      // always "paid" for payment rows
                p.getPaymentMethod(),
                p.getPaymentDate(),         // paymentDate = docDate for payment rows
                "active",
                p.getTransactionId()
            );
        }
    }

    public record CardSummary(
        Config card,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal unpaid,
        BigDecimal income,
        BigDecimal urgent,
        BigDecimal paymentsTotal,
        int countTotal,
        int countPaid,
        int countUnpaid,
        int countIncome,
        int countUrgent,
        int countPayments
    ) {}

    public record CardTransactions(
        Config card,
        List<Transaction> transactions,
        int total,
        int limit,
        int offset
    ) {}

    public record CardRows(
        Config card,
        List<CardRow> rows,
        int total,
        int limit,
        int offset
    ) {}
}