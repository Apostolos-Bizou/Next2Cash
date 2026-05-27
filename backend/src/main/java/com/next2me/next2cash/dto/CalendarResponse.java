package com.next2me.next2cash.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CalendarResponse — response shape for GET /api/calendar.
 *
 * Contains monthly KPIs plus a list of daily entries (one per day of the
 * requested month). Each DayEntry may carry either analytical transactions
 * (single-entity view) or aggregated totals (group view).
 */
public class CalendarResponse {

    private int year;
    private int month;
    private String entityScope; // "single" | "group"
    private Kpis kpis;
    private List<DayEntry> days = new ArrayList<>();

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public String getEntityScope() { return entityScope; }
    public void setEntityScope(String entityScope) { this.entityScope = entityScope; }

    public Kpis getKpis() { return kpis; }
    public void setKpis(Kpis kpis) { this.kpis = kpis; }

    public List<DayEntry> getDays() { return days; }
    public void setDays(List<DayEntry> days) { this.days = days; }

    public static class Kpis {
        private BigDecimal totalIncome = BigDecimal.ZERO;
        private BigDecimal totalExpenses = BigDecimal.ZERO;
        private BigDecimal netFlow = BigDecimal.ZERO;
        private BigDecimal endOfMonthCash = BigDecimal.ZERO;

        public BigDecimal getTotalIncome() { return totalIncome; }
        public void setTotalIncome(BigDecimal totalIncome) { this.totalIncome = totalIncome; }

        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }

        public BigDecimal getNetFlow() { return netFlow; }
        public void setNetFlow(BigDecimal netFlow) { this.netFlow = netFlow; }

        public BigDecimal getEndOfMonthCash() { return endOfMonthCash; }
        public void setEndOfMonthCash(BigDecimal endOfMonthCash) { this.endOfMonthCash = endOfMonthCash; }
    }

    public static class DayEntry {
        private String date;       // "YYYY-MM-DD"
        private int dayOfMonth;
        private int dayOfWeek;     // 1=Mon ... 7=Sun
        private boolean isWeekend;
        private boolean isToday;
        private List<DayTransaction> transactions = new ArrayList<>();
        private Aggregates aggregates;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public int getDayOfMonth() { return dayOfMonth; }
        public void setDayOfMonth(int dayOfMonth) { this.dayOfMonth = dayOfMonth; }

        public int getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

        public boolean getIsWeekend() { return isWeekend; }
        public void setIsWeekend(boolean isWeekend) { this.isWeekend = isWeekend; }

        public boolean getIsToday() { return isToday; }
        public void setIsToday(boolean isToday) { this.isToday = isToday; }

        public List<DayTransaction> getTransactions() { return transactions; }
        public void setTransactions(List<DayTransaction> transactions) { this.transactions = transactions; }

        public Aggregates getAggregates() { return aggregates; }
        public void setAggregates(Aggregates aggregates) { this.aggregates = aggregates; }
    }

    public static class DayTransaction {
        private Integer id;
        private String type;        // "income" | "expense"
        private String entryMode;   // "ACTUAL" | "PLANNED" | "VIRTUAL"
        private BigDecimal amount;
        private String category;
        private String description;
        private String projectName;
        private String entityName;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getEntryMode() { return entryMode; }
        public void setEntryMode(String entryMode) { this.entryMode = entryMode; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }

        public String getEntityName() { return entityName; }
        public void setEntityName(String entityName) { this.entityName = entityName; }
    }

    public static class Aggregates {
        private BigDecimal incomeTotal = BigDecimal.ZERO;
        private BigDecimal expenseTotal = BigDecimal.ZERO;
        private int incomeCount;
        private int expenseCount;
        // Split actual vs planned for visual cue (solid vs dashed in UI)
        private BigDecimal actualIncomeTotal = BigDecimal.ZERO;
        private BigDecimal plannedIncomeTotal = BigDecimal.ZERO;
        private BigDecimal actualExpenseTotal = BigDecimal.ZERO;
        private BigDecimal plannedExpenseTotal = BigDecimal.ZERO;

        public BigDecimal getIncomeTotal() { return incomeTotal; }
        public void setIncomeTotal(BigDecimal incomeTotal) { this.incomeTotal = incomeTotal; }

        public BigDecimal getExpenseTotal() { return expenseTotal; }
        public void setExpenseTotal(BigDecimal expenseTotal) { this.expenseTotal = expenseTotal; }

        public int getIncomeCount() { return incomeCount; }
        public void setIncomeCount(int incomeCount) { this.incomeCount = incomeCount; }

        public int getExpenseCount() { return expenseCount; }
        public void setExpenseCount(int expenseCount) { this.expenseCount = expenseCount; }

        public BigDecimal getActualIncomeTotal() { return actualIncomeTotal; }
        public void setActualIncomeTotal(BigDecimal v) { this.actualIncomeTotal = v; }

        public BigDecimal getPlannedIncomeTotal() { return plannedIncomeTotal; }
        public void setPlannedIncomeTotal(BigDecimal v) { this.plannedIncomeTotal = v; }

        public BigDecimal getActualExpenseTotal() { return actualExpenseTotal; }
        public void setActualExpenseTotal(BigDecimal v) { this.actualExpenseTotal = v; }

        public BigDecimal getPlannedExpenseTotal() { return plannedExpenseTotal; }
        public void setPlannedExpenseTotal(BigDecimal v) { this.plannedExpenseTotal = v; }
    }
}
