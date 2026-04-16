package com.next2me.next2cash.controller;

import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final TransactionRepository transactionRepository;

    // GET /api/dashboard?entity_id=X&from=YYYY-MM-DD&to=YYYY-MM-DD
    @GetMapping
    public ResponseEntity<?> getDashboard(
            @RequestParam UUID entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        // Default: current year
        LocalDate dateFrom = from != null ? LocalDate.parse(from)
            : LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate dateTo = to != null ? LocalDate.parse(to)
            : LocalDate.of(LocalDate.now().getYear(), 12, 31);

        // Core KPIs
        BigDecimal totalIncome  = transactionRepository.sumIncomeByEntityAndPeriod(entityId, dateFrom, dateTo);
        BigDecimal totalExpense = transactionRepository.sumExpenseByEntityAndPeriod(entityId, dateFrom, dateTo);
        BigDecimal netBalance   = totalIncome.subtract(totalExpense);

        // Urgent (Εκκρεμείς) — used for Ταμειακά Διαθέσιμα
        BigDecimal urgentTotal  = transactionRepository.sumUrgentRemaining(entityId);
        BigDecimal unpaidTotal  = transactionRepository.sumUnpaidRemaining(entityId);

        // Ταμειακά Διαθέσιμα = Net - Urgent (ακριβώς ως legacy)
        BigDecimal cashAvailable = netBalance.subtract(urgentTotal);

        // Recent transactions (last 10)
        var recent = transactionRepository.findRecentByEntity(
            entityId, PageRequest.of(0, 10));

        // Monthly data for chart
        var monthlyData = transactionRepository.getMonthlyReport(
            entityId, LocalDate.now().getYear());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "kpis", Map.of(
                "totalIncome",    totalIncome,
                "totalExpense",   totalExpense,
                "netBalance",     netBalance,
                "urgentTotal",    urgentTotal,     // Εκκρεμείς
                "unpaidTotal",    unpaidTotal,     // Απλήρωτες + Εκκρεμείς
                "cashAvailable",  cashAvailable    // Ταμειακά Διαθέσιμα
            ),
            "recent",      recent,
            "monthlyData", monthlyData,
            "period", Map.of(
                "from", dateFrom.toString(),
                "to",   dateTo.toString()
            )
        ));
    }

    // GET /api/health
    @GetMapping("/../../health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "Next2Cash Backend"));
    }
}
