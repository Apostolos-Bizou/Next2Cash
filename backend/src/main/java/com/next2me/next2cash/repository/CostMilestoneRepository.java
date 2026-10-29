package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.CostMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for CostMilestone (table cost_milestones). Entity-scoped lookups only. */
@Repository
public interface CostMilestoneRepository extends JpaRepository<CostMilestone, UUID> {

    List<CostMilestone> findByEntityIdOrderByCategoryAscTargetDateAsc(UUID entityId);

    Optional<CostMilestone> findByIdAndEntityId(UUID id, UUID entityId);
}
