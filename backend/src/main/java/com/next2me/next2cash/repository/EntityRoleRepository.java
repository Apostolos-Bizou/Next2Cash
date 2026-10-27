package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.EntityRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EntityRole (table entity_roles).
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Read by UserAccessService for the
 * Polaris entity only; rows are managed via SQL for now (no write endpoint).
 */
@Repository
public interface EntityRoleRepository extends JpaRepository<EntityRole, UUID> {

    /** The single role row of a user on an entity (UNIQUE (user_id, entity_id)). */
    Optional<EntityRole> findByUserIdAndEntityId(UUID userId, UUID entityId);

    /** All role rows of an entity. */
    List<EntityRole> findByEntityId(UUID entityId);
}
