package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.Config;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConfigRepository extends JpaRepository<Config, UUID> {

    List<Config> findByEntityIdAndIsActiveTrueOrderBySortOrderAsc(UUID entityId);

    List<Config> findByEntityIdAndConfigTypeAndIsActiveTrueOrderBySortOrderAsc(UUID entityId, String configType);

    // M.7 — Admin panel queries
    List<Config> findByEntityIdOrderBySortOrderAsc(UUID entityId);

    List<Config> findByEntityIdAndConfigTypeOrderBySortOrderAsc(UUID entityId, String configType);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(c.sortOrder) FROM Config c WHERE c.entityId = :entityId AND c.configType = :configType")
    Integer findMaxSortOrder(@org.springframework.data.repository.query.Param("entityId") UUID entityId, @org.springframework.data.repository.query.Param("configType") String configType);

    // Phase H v2 — entity-scoped single-card lookup.
    Optional<Config> findByIdAndEntityId(UUID id, UUID entityId);
}