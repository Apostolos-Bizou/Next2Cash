package com.next2me.next2cash.service;

import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.ConfigRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
 *   - Rule engine filters transactions in pure Java (no custom SQL)
 *     to guarantee H2/PostgreSQL parity.
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
     * Returns the transactions matching a card's rule, paginated in Java.
     * Always applies: recordStatus='active', entityId scope.
     *
     * @param cardId   card UUID
     * @param entityId entity scope
     * @param limit    max items (default 2000, max 10000)
     * @param offset   skip first N items (default 0)
     * @return pagination result with matched transactions and total count
     */
    public CardTransactions getTransactionsForCard(
            UUID cardId, UUID entityId, int limit, int offset) {

        Config card = getCard(cardId, entityId);

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

        List<Transaction> all = transactionRepository
            .findByEntityIdAndRecordStatusOrderByDocDateDesc(entityId, RECORD_STATUS_ACTIVE);

        List<Transaction> matched = all.stream()
            .filter(t -> matches(t, filterType, values))
            .toList();

        int total = matched.size();
        int safeLimit  = Math.max(1, Math.min(limit, 10000));
        int safeOffset = Math.max(0, Math.min(offset, total));
        int end        = Math.min(safeOffset + safeLimit, total);

        List<Transaction> page = matched.subList(safeOffset, end);
        return new CardTransactions(card, page, total, safeLimit, safeOffset);
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

    /**
     * Normalize a string for case- AND accent-insensitive matching.
     * Critical for Greek: "Παροχή".toUpperCase() yields "ΠΑΡΟΧΎ" (with accent),
     * not "ΠΑΡΟΧΗ", so a plain uppercase compare would fail.
     * NFD decomposition + removal of combining marks strips accents reliably.
     */
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

    // ─── DTO ───

    public record CardTransactions(
        Config card,
        List<Transaction> transactions,
        int total,
        int limit,
        int offset
    ) {}
}
