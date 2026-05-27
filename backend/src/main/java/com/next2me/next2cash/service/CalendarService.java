package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.CalendarResponse;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.CompanyEntityRepository;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CalendarService — builds daily-grouped cash flow data for the Calendar view.
 *
 * S96. Two modes:
 *   - buildEntityCalendar(entityId, year, month): per-transaction analytical
 *   - buildGroupCalendar(year, month):           aggregated across all entities
 *
 * Both use the existing TransactionRepository to gather records, then
 * group by day. Project names are resolved once and cached locally.
 */
@Service
public class CalendarService {

    private final TransactionRepository transactionRepository;
    private final CompanyEntityRepository companyEntityRepository;
    private final ProjectRepository projectRepository;
    private final BankBalanceService bankBalanceService;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    public CalendarService(TransactionRepository transactionRepository,
                           CompanyEntityRepository companyEntityRepository,
                           ProjectRepository projectRepository,
                           BankBalanceService bankBalanceService) {
        this.transactionRepository = transactionRepository;
        this.companyEntityRepository = companyEntityRepository;
        this.projectRepository = projectRepository;
        this.bankBalanceService = bankBalanceService;
    }

    /**
     * Build calendar for a single entity (analytical view).
     */
    public CalendarResponse buildEntityCalendar(UUID entityId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay  = ym.atEndOfMonth();

        List<Transaction> txns = transactionRepository
                .findByEntityIdAndRecordStatusAndDocDateBetweenOrderByDocDateDesc(
                        entityId, "active", firstDay, lastDay,
                        org.springframework.data.domain.PageRequest.of(0, 10000))
                .getContent();

        Map<UUID, String> projectNames = loadProjectNames();

        CalendarResponse resp = new CalendarResponse();
        resp.setYear(year);
        resp.setMonth(month);
        resp.setEntityScope("single");

        Map<LocalDate, CalendarResponse.DayEntry> dayMap = buildDaySkeleton(ym);

        BigDecimal totalIncome   = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (Transaction t : txns) {
            LocalDate d = t.getDocDate();
            if (d == null) continue;
            CalendarResponse.DayEntry entry = dayMap.get(d);
            if (entry == null) continue;

            CalendarResponse.DayTransaction dt = new CalendarResponse.DayTransaction();
            dt.setId(t.getId());
            dt.setType(t.getType());
            dt.setEntryMode(safeStr(t.getEntryMode(), "ACTUAL"));
            dt.setAmount(t.getAmount() == null ? BigDecimal.ZERO : t.getAmount());
            dt.setCategory(safeStr(t.getCategory(), ""));
            dt.setDescription(safeStr(t.getDescription(), ""));
            if (t.getProjectId() != null) {
                dt.setProjectName(projectNames.getOrDefault(t.getProjectId(), ""));
            }
            entry.getTransactions().add(dt);

            BigDecimal amt = dt.getAmount();
            if ("income".equalsIgnoreCase(t.getType())) {
                totalIncome = totalIncome.add(amt);
            } else if ("expense".equalsIgnoreCase(t.getType())) {
                totalExpenses = totalExpenses.add(amt);
            }
        }

        CalendarResponse.Kpis kpis = new CalendarResponse.Kpis();
        kpis.setTotalIncome(totalIncome);
        kpis.setTotalExpenses(totalExpenses);
        kpis.setNetFlow(totalIncome.subtract(totalExpenses));
        kpis.setEndOfMonthCash(estimateEndOfMonthCash(entityId, ym));
        resp.setKpis(kpis);

        List<CalendarResponse.DayEntry> orderedDays = new ArrayList<>(dayMap.values());
        orderedDays.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        resp.setDays(orderedDays);

        return resp;
    }

    /**
     * Build calendar for ALL entities (group view, aggregated).
     */
    public CalendarResponse buildGroupCalendar(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay  = ym.atEndOfMonth();

        CalendarResponse resp = new CalendarResponse();
        resp.setYear(year);
        resp.setMonth(month);
        resp.setEntityScope("group");

        Map<LocalDate, CalendarResponse.DayEntry> dayMap = buildDaySkeleton(ym);
        for (CalendarResponse.DayEntry e : dayMap.values()) {
            e.setAggregates(new CalendarResponse.Aggregates());
        }

        BigDecimal totalIncome   = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal endOfMonthCashSum = BigDecimal.ZERO;

        List<CompanyEntity> entities = companyEntityRepository.findAll();
        for (CompanyEntity ent : entities) {
            UUID eid = ent.getId();
            List<Transaction> txns = transactionRepository
                    .findByEntityIdAndRecordStatusAndDocDateBetweenOrderByDocDateDesc(
                            eid, "active", firstDay, lastDay,
                            org.springframework.data.domain.PageRequest.of(0, 10000))
                    .getContent();

            for (Transaction t : txns) {
                LocalDate d = t.getDocDate();
                if (d == null) continue;
                CalendarResponse.DayEntry entry = dayMap.get(d);
                if (entry == null) continue;

                CalendarResponse.Aggregates ag = entry.getAggregates();
                BigDecimal amt = t.getAmount() == null ? BigDecimal.ZERO : t.getAmount();
                boolean planned = "PLANNED".equalsIgnoreCase(safeStr(t.getEntryMode(), "ACTUAL"));

                if ("income".equalsIgnoreCase(t.getType())) {
                    ag.setIncomeTotal(ag.getIncomeTotal().add(amt));
                    ag.setIncomeCount(ag.getIncomeCount() + 1);
                    if (planned) {
                        ag.setPlannedIncomeTotal(ag.getPlannedIncomeTotal().add(amt));
                    } else {
                        ag.setActualIncomeTotal(ag.getActualIncomeTotal().add(amt));
                    }
                    totalIncome = totalIncome.add(amt);
                } else if ("expense".equalsIgnoreCase(t.getType())) {
                    ag.setExpenseTotal(ag.getExpenseTotal().add(amt));
                    ag.setExpenseCount(ag.getExpenseCount() + 1);
                    if (planned) {
                        ag.setPlannedExpenseTotal(ag.getPlannedExpenseTotal().add(amt));
                    } else {
                        ag.setActualExpenseTotal(ag.getActualExpenseTotal().add(amt));
                    }
                    totalExpenses = totalExpenses.add(amt);
                }
            }

            endOfMonthCashSum = endOfMonthCashSum.add(estimateEndOfMonthCash(eid, ym));
        }

        CalendarResponse.Kpis kpis = new CalendarResponse.Kpis();
        kpis.setTotalIncome(totalIncome);
        kpis.setTotalExpenses(totalExpenses);
        kpis.setNetFlow(totalIncome.subtract(totalExpenses));
        kpis.setEndOfMonthCash(endOfMonthCashSum);
        resp.setKpis(kpis);

        List<CalendarResponse.DayEntry> orderedDays = new ArrayList<>(dayMap.values());
        orderedDays.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        resp.setDays(orderedDays);
        return resp;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<UUID, String> loadProjectNames() {
        Map<UUID, String> map = new HashMap<>();
        try {
            for (Project p : projectRepository.findAll()) {
                if (p.getId() != null) {
                    map.put(p.getId(), p.getName());
                }
            }
        } catch (Exception ignored) {
            // graceful fallback
        }
        return map;
    }

    private Map<LocalDate, CalendarResponse.DayEntry> buildDaySkeleton(YearMonth ym) {
        Map<LocalDate, CalendarResponse.DayEntry> map = new HashMap<>();
        LocalDate today = LocalDate.now();
        int days = ym.lengthOfMonth();
        for (int dom = 1; dom <= days; dom++) {
            LocalDate d = ym.atDay(dom);
            CalendarResponse.DayEntry e = new CalendarResponse.DayEntry();
            e.setDate(d.format(ISO));
            e.setDayOfMonth(dom);
            DayOfWeek dow = d.getDayOfWeek();
            e.setDayOfWeek(dow.getValue());
            e.setIsWeekend(dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
            e.setIsToday(d.equals(today));
            map.put(d, e);
        }
        return map;
    }

    /**
     * Estimate end-of-month cash for given entity:
     *  current bank balance (sum of all accounts).
     *  This is a heuristic. For more accurate projection use the Forecast Engine.
     */
    private BigDecimal estimateEndOfMonthCash(UUID entityId, YearMonth ym) {
        try {
            List<com.next2me.next2cash.model.BankAccount> accounts =
                    bankBalanceService.recomputeForEntity(entityId);
            BigDecimal sum = BigDecimal.ZERO;
            if (accounts != null) {
                for (com.next2me.next2cash.model.BankAccount a : accounts) {
                    if (a.getCurrentBalance() != null) {
                        sum = sum.add(a.getCurrentBalance());
                    }
                }
            }
            return sum;
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String safeStr(String s, String def) {
        return (s == null || s.isEmpty()) ? def : s;
    }
}
