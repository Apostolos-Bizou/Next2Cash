package com.next2me.next2cash.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.next2me.next2cash.model.CompanyEntity;

import java.util.List;
import java.util.UUID;

/**
 * Manages the user_entities junction table.
 * Table schema: (user_id UUID, entity_id UUID) — composite primary key, no own id.
 *
 * We use native SQL because there's no JPA @ManyToMany mapping between User and
 * CompanyEntity (intentional: explicit control, no lazy-load surprises).
 *
 * Note: flushAutomatically + clearAutomatically ensure the JPA persistence context
 * reflects the changes immediately, so subsequent reads return fresh data.
 */
@Repository
public interface UserEntityRepository extends JpaRepository<CompanyEntity, UUID> {

    /**
     * Return all CompanyEntity rows that are assigned to the given user.
     */
    @Query(value = """
        SELECT e.*
        FROM entities e
        INNER JOIN user_entities ue ON ue.entity_id = e.id
        WHERE ue.user_id = :userId
        ORDER BY e.sort_order, e.name
        """, nativeQuery = true)
    List<CompanyEntity> findEntitiesByUserId(@Param("userId") UUID userId);

    /**
     * Return just the entity UUIDs assigned to a user (lightweight).
     */
    @Query(value = "SELECT entity_id FROM user_entities WHERE user_id = :userId", nativeQuery = true)
    List<UUID> findEntityIdsByUserId(@Param("userId") UUID userId);

    /**
     * Delete all user_entities rows for a given user.
     * Use before inserting a new set (replace-all semantics).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM user_entities WHERE user_id = :userId", nativeQuery = true)
    int deleteAllForUser(@Param("userId") UUID userId);

    /**
     * Insert a single (user_id, entity_id) row.
     * Called in a loop after deleteAllForUser.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = "INSERT INTO user_entities (user_id, entity_id) VALUES (:userId, :entityId)",
           nativeQuery = true)
    int insertUserEntity(@Param("userId") UUID userId, @Param("entityId") UUID entityId);
}
