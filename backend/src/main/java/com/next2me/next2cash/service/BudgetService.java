package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.BudgetSeedLineDTO;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Budget auto-seed logic.
 *
 * <p>Given an entity and a target budget year, looks at the prior year's ACTUAL
 * transactions and proposes a per-category monthly average (total of that
 * category over the source year / 12). The CEO can then edit before saving.
 *
 * <p>Category-level only: subcategory breakdown is entered manually (per the
 * confirmed S98.1 design). Both income and expense categories are seeded.
 *
 * Session: S98.1
 */
@Service
public class BudgetService {

    private final TransactionRepository transactionRepository;

    public BudgetService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Build auto-seed suggestions for {@code targetYear} from {@code sourceYear}
     * ACTUAL transactions of the given entity.
     *
     * @return one line per (category, direction) with the monthly average.
     */
    public List<BudgetSeedLineDTO> seedFromActual(UUID entityId, int sourceYear) {
        LocalDate from = LocalDate.of(sourceYear, 1, 1);
        LocalDate to = LocalDate.of(sourceYear, 12, 31);

        // Reuse existing date-range fetch (active transactions in window).
        List<Transaction> txns = transactionRepository
                .findByEntityIdAndRecordStatusAndDocDateBetween(entityId, "active", from, to);

        // Accumulate totals per (direction|category). Use a stable key.
        // Only ACTUAL (entryMode null or ACTUAL); skip PLANNED and zero-amount.
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, String> catOf = new LinkedHashMap<>();
        Map<String, String> dirOf = new LinkedHashMap<>();

        for (Transaction t : txns) {
            String mode = t.getEntryMode() == null ? "ACTUAL" : t.getEntryMode().toUpperCase();
            if (!"ACTUAL".equals(mode)) continue;

            BigDecimal amt = t.getAmount();
            if (amt == null || amt.signum() == 0) continue;

            String direction = t.getType() == null ? "expense" : t.getType().toLowerCase();
            String category = (t.getCategory() == null || t.getCategory().isBlank())
                    ? "(χωρίς κατηγορία)" : t.getCategory();

            String key = direction + "|" + category;
            totals.merge(key, amt, BigDecimal::add);
            catOf.putIfAbsent(key, category);
            dirOf.putIfAbsent(key, direction);
        }

        List<BudgetSeedLineDTO> out = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : totals.entrySet()) {
            String key = e.getKey();
            BigDecimal yearTotal = e.getValue();
            BigDecimal monthlyAvg = yearTotal.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            out.add(new BudgetSeedLineDTO(catOf.get(key), dirOf.get(key), monthlyAvg));
        }
        return out;
    }
}
