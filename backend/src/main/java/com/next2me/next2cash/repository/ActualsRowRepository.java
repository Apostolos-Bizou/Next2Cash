package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.ActualsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ActualsRow (table actuals_rows).
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Entity-scoped lookups only.
 */
@Repository
public interface ActualsRowRepository extends JpaRepository<ActualsRow, UUID> {

    List<ActualsRow> findByEntityIdOrderByPeriodStartDescGroupKeyAscLabelAsc(UUID entityId);

    Optional<ActualsRow> findByIdAndEntityId(UUID id, UUID entityId);
}
