package com.next2me.next2cash.dto;

import com.next2me.next2cash.model.Scenario;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data transfer object for forecast scenarios.
 * Mirrors ProjectDTO conventions (public fields + static fromEntity factory).
 *
 * Session: S97
 */
public class ScenarioDTO {

    public UUID id;
    public String name;
    public String scenarioType;
    public UUID ownerEntityId;
    public String description;
    public BigDecimal revenueAdjustPct;
    public BigDecimal expenseAdjustPct;
    public String color;
    public Boolean isDefault;
    public Boolean isActive;

    public static ScenarioDTO fromEntity(Scenario s) {
        ScenarioDTO dto = new ScenarioDTO();
        dto.id = s.getId();
        dto.name = s.getName();
        dto.scenarioType = s.getScenarioType();
        dto.ownerEntityId = s.getOwnerEntityId();
        dto.description = s.getDescription();
        dto.revenueAdjustPct = s.getRevenueAdjustPct();
        dto.expenseAdjustPct = s.getExpenseAdjustPct();
        dto.color = s.getColor();
        dto.isDefault = s.getIsDefault();
        dto.isActive = s.getIsActive();
        return dto;
    }
}
