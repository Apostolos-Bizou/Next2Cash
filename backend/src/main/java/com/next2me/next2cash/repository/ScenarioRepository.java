package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for forecast scenarios.
 *
 * Query method patterns mirror ProjectRepository (entity-scoped listing,
 * active-only filter, name lookup per entity).
 *
 * Session: S97
 */
public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    /** All scenarios owned by any of the given entities. */
    @Query("SELECT s FROM Scenario s WHERE s.ownerEntityId IN :entityIds ORDER BY s.scenarioType, s.name")
    List<Scenario> findByOwnerEntityIdIn(@Param("entityIds") Set<UUID> entityIds);

    /** Active scenarios owned by any of the given entities. */
    @Query("SELECT s FROM Scenario s WHERE s.isActive = true AND s.ownerEntityId IN :entityIds ORDER BY s.scenarioType, s.name")
    List<Scenario> findActiveByOwnerEntityIdIn(@Param("entityIds") Set<UUID> entityIds);

    /** Scenarios of a single entity (all). */
    List<Scenario> findByOwnerEntityIdOrderByScenarioTypeAscNameAsc(UUID ownerEntityId);

    /** Name uniqueness check within an entity. */
    Optional<Scenario> findByOwnerEntityIdAndName(UUID ownerEntityId, String name);

    /** The default (Baseline) scenario for an entity, if any. */
    Optional<Scenario> findFirstByOwnerEntityIdAndIsDefaultTrue(UUID ownerEntityId);
}
