package com.next2me.next2cash.dto;

import com.next2me.next2cash.model.Project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class ProjectDTO {
    public UUID id;
    public String name;
    public String description;
    public UUID ownerEntityId;
    public String status;
    public LocalDate startDate;
    public LocalDate targetCompletionDate;
    public LocalDate actualCompletionDate;
    public BigDecimal totalBudget;
    public BigDecimal expectedMonthlyRevenue;
    public String color;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public Boolean isActive;

    public ProjectDTO() {}

    public static ProjectDTO fromEntity(Project p) {
        ProjectDTO d = new ProjectDTO();
        d.id = p.getId();
        d.name = p.getName();
        d.description = p.getDescription();
        d.ownerEntityId = p.getOwnerEntityId();
        d.status = p.getStatus();
        d.startDate = p.getStartDate();
        d.targetCompletionDate = p.getTargetCompletionDate();
        d.actualCompletionDate = p.getActualCompletionDate();
        d.totalBudget = p.getTotalBudget();
        d.expectedMonthlyRevenue = p.getExpectedMonthlyRevenue();
        d.color = p.getColor();
        d.createdAt = p.getCreatedAt();
        d.updatedAt = p.getUpdatedAt();
        d.isActive = p.getIsActive();
        return d;
    }
}
