package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.UserEntityLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA-based repository for the user_entities junction table.
 *
 * Why this exists alongside UserEntityRepository:
 *   - UserEntityRepository uses native SQL (works in PostgreSQL but breaks on H2
 *     when returning List<UUID> due to a UUID-to-byte[] conversion quirk).
 *   - This repository uses JPQL, which Hibernate translates correctly for BOTH
 *     H2 and PostgreSQL.
 *
 * Used by UserAccessService for read operations (which tests exercise).
 * UserController still uses UserEntityRepository for write operations
 * (insertUserEntity / deleteAllForUser) because those are stable in production.
 */
@Repository
public interface UserEntityLinkRepository extends JpaRepository<UserEntityLink, UserEntityLink.UserEntityLinkId> {

    /**
     * JPA-standard query for the entity IDs assigned to a user.
     * Equivalent to:
     *   SELECT entity_id FROM user_entities WHERE user_id = :userId
     */
    @Query("SELECT l.entityId FROM UserEntityLink l WHERE l.userId = :userId")
    List<UUID> findEntityIdsByUserId(@Param("userId") UUID userId);
}