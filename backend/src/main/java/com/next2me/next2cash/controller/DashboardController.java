package com.next2me.next2cash.controller;

import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final TransactionRepository transactionRepository;

    @GetMapping
    public ResponseEntity<?> getDashboard(
            @RequestParam UUID entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDate dateFrom = from != null ? LocalDate.parse(from)
            : LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate dateTo = to != null ? LocalDate.parse(to)
            : LocalDate.of(LocalDate.now().getYear(), 12, 31);

        BigDecimal totalIncome  = transactionRepository.sumIncomeByEntityAndPeriod(entityId, dateFrom, dateTo);
        BigDecimal totalExpense = transactionRepository.sumExpenseByEntityAndPeriod(entityId, dateFrom, dateTo);
        BigDecimal netBalance   = totalIncome.subtract(totalExpense);
        BigDecimal urgentTotal  = transactionRepository.sumUrgentRemaining(entityId);
        BigDecimal unpaidTotal  = transactionRepository.sumUnpaidRemaining(entityId);
        BigDecimal cashAvailable = netBalance.subtract(urgentTotal);

        var recent = transactionRepository.findRecentByEntity(entityId, PageRequest.of(0, 10));
        var monthlyData = transactionRepository.getMonthlyReport(entityId, LocalDate.now().getYear());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "kpis", Map.of(
                "totalIncome",   totalIncome,
                "totalExpense",  totalExpense,
                "netBalance",    netBalance,
                "urgentTotal",   urgentTotal,
                "unpaidTotal",   unpaidTotal,
                "cashAvailable", cashAvailable
            ),
            "recent",      recent,
            "monthlyData", monthlyData,
            "period", Map.of("from", dateFrom.toString(), "to", dateTo.toString())
        ));
    }

    // GET /api/dashboard/balance-trend?entityId=X&from=YYYY-MM-DD&to=YYYY-MM-DD
    @GetMapping("/balance-trend")
    public ResponseEntity<?> getBalanceTrend(
            @RequestParam UUID entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDate dateFrom = from != null ? LocalDate.parse(from)
            : LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate dateTo = to != null ? LocalDate.parse(to)
            : LocalDate.now();

        var raw = transactionRepository.getBalanceTrend(entityId, dateFrom, dateTo);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", row[0].toString());
            point.put("balance", row[1]);
            result.add(point);
        }

        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    // GET /api/dashboard/yearly?entityId=X
    @GetMapping("/yearly")
    public ResponseEntity<?> getYearly(@RequestParam UUID entityId) {

        var raw = transactionRepository.getYearlyReport(entityId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("year", row[0]);
            item.put("category", row[1]);
            item.put("total", row[2]);
            result.add(item);
        }

        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    // GET /api/dashboard/category-breakdown?entityId=X&from=YYYY-MM-DD&to=YYYY-MM-DD
    @GetMapping("/category-breakdown")
    public ResponseEntity<?> getCategoryBreakdown(
            @RequestParam UUID entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDate dateFrom = from != null ? LocalDate.parse(from)
            : LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate dateTo = to != null ? LocalDate.parse(to)
            : LocalDate.of(LocalDate.now().getYear(), 12, 31);

        var raw = transactionRepository.getCategoryBreakdown(entityId, dateFrom, dateTo);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", row[0]);
            item.put("subcategory", row[1]);
            item.put("income", row[2]);
            item.put("expense", row[3]);
            result.add(item);
        }

        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }
}