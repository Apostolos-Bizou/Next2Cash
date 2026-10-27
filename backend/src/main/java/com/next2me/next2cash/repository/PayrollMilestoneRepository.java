package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.PayrollMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PayrollMilestone (table payroll_milestones).
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Entity-scoped lookups only.
 */
@Repository
public interface PayrollMilestoneRepository extends JpaRepository<PayrollMilestone, UUID> {

    List<PayrollMilestone> findByEntityIdOrderByTargetMonthAsc(UUID entityId);

    Optional<PayrollMilestone> findByIdAndEntityId(UUID id, UUID entityId);
}
