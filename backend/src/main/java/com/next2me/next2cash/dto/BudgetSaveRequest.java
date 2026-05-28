package com.next2me.next2cash.dto;

import java.util.List;
import java.util.UUID;

/**
 * Bulk save payload: replaces ALL budget lines for (entity, year).
 * Frontend sends the full grid; backend wipes + reinserts (replace-on-save).
 *
 * Session: S98.1
 */
public class BudgetSaveRequest {
    public UUID entityId;
    public Integer year;
    public List<BudgetLineDTO> lines;
}
