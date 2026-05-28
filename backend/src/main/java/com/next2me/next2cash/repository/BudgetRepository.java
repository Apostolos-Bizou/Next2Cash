package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Repository for budget lines.
 *
 * Query patterns mirror ScenarioRepository (entity-scoped listing).
 *
 * Session: S98.1
 */
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    /** All budget lines for an entity + year, ordered for stable display. */
    List<Budget> findByOwnerEntityIdAndBudgetYearOrderByDirectionAscCategoryAscSubcategoryAscMonthAsc(
            UUID ownerEntityId, Integer budgetYear);

    /** Replace-on-save helper: wipe an entity's budget for a year before bulk insert. */
    @Transactional
    void deleteByOwnerEntityIdAndBudgetYear(UUID ownerEntityId, Integer budgetYear);
}
