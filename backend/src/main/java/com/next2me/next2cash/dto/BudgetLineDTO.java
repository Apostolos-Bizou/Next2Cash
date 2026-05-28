package com.next2me.next2cash.dto;

import com.next2me.next2cash.model.Budget;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One budget cell: (category, subcategory, direction, month) -> amount.
 * Used both for reading existing budget and for bulk save.
 *
 * Session: S98.1
 */
public class BudgetLineDTO {
    public UUID id;
    public String category;
    public String subcategory = "";
    public String direction;   // income / expense
    public Integer month;      // 1..12
    public BigDecimal amount = BigDecimal.ZERO;

    public BudgetLineDTO() {}

    public static BudgetLineDTO fromEntity(Budget b) {
        BudgetLineDTO d = new BudgetLineDTO();
        d.id = b.getId();
        d.category = b.getCategory();
        d.subcategory = b.getSubcategory() == null ? "" : b.getSubcategory();
        d.direction = b.getDirection();
        d.month = b.getMonth();
        d.amount = b.getAmount();
        return d;
    }
}
