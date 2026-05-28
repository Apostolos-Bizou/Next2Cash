package com.next2me.next2cash.dto;

import java.math.BigDecimal;

/**
 * One auto-seed suggestion: category-level monthly average from prior-year
 * ACTUAL transactions. Frontend pre-fills the grid with these (editable).
 *
 * Session: S98.1
 */
public class BudgetSeedLineDTO {
    public String category;
    public String direction;          // income / expense
    public BigDecimal monthlyAvg = BigDecimal.ZERO;  // avg per month from source year(s)

    public BudgetSeedLineDTO() {}
    public BudgetSeedLineDTO(String category, String direction, BigDecimal monthlyAvg) {
        this.category = category;
        this.direction = direction;
        this.monthlyAvg = monthlyAvg;
    }
}
