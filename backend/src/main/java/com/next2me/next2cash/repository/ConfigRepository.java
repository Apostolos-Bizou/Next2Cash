package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.Config;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConfigRepository extends JpaRepository<Config, UUID> {

    List<Config> findByEntityIdAndIsActiveTrueOrderBySortOrderAsc(UUID entityId);

    List<Config> findByEntityIdAndConfigTypeAndIsActiveTrueOrderBySortOrderAsc(UUID entityId, String configType);
}