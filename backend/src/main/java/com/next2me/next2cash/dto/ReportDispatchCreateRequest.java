package com.next2me.next2cash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for POST /api/report-dispatches (and /preview).
 *
 * Each item carries a declared {@code section} ("INCOME" | "EXPENSE"). The
 * service enforces that the declared section matches the transaction's sign
 * (income → INCOME, expense → EXPENSE) — SPEC §2.3 invariant #6 ("πρόσημο
 * καθορίζει ενότητα, επιβολή στο backend"). transactionId is Integer because
 * transactions.id is a serial INTEGER.
 */
@Data
@NoArgsConstructor
public class ReportDispatchCreateRequest {

    private String title;
    private String recipient;
    private LocalDate sentDate;
    private String note;
    private List<Item> items;

    /** Whether to attach the dispatched transactions' documents as a ZIP.
     *  Default true (null treated as true). Level 4.5. */
    private Boolean includeDocs;

    @Data
    @NoArgsConstructor
    public static class Item {
        private Integer transactionId;
        private String section; // "INCOME" | "EXPENSE"
    }
}
