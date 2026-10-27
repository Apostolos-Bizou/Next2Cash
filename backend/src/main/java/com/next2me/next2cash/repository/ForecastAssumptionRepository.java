package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.ForecastAssumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ForecastAssumption (table forecast_assumptions).
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Derived queries only -- `key` is
 * a reserved word in JPQL, so hand-written @Query strings on it are avoided.
 */
@Repository
public interface ForecastAssumptionRepository extends JpaRepository<ForecastAssumption, UUID> {

    List<ForecastAssumption> findByEntityIdOrderByKeyAsc(UUID entityId);

    Optional<ForecastAssumption> findByEntityIdAndKey(UUID entityId, String key);

    Optional<ForecastAssumption> findByIdAndEntityId(UUID id, UUID entityId);
}
