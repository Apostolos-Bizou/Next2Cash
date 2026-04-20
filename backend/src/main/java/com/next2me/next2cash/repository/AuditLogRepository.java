package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityIdOrderByCreatedAtDesc(UUID entityId, Pageable pageable);

    // Native PostgreSQL query. JPQL is avoided here because Hibernate 6
    // fails to translate (CAST(:p AS string) IS NULL OR ...) combined with
    // column IS NULL comparisons on this driver version. Native SQL uses
    // explicit ::uuid / ::timestamptz casts so the PostgreSQL planner has
    // full type information for every parameter, including nulls.
    @Query(
        value =
            "SELECT * FROM audit_log " +
            "WHERE (entity_id = CAST(:entityId AS uuid) OR entity_id IS NULL) " +
            "AND (CAST(:action AS text) IS NULL OR action = CAST(:action AS text)) " +
            "AND (CAST(:username AS text) IS NULL OR username = CAST(:username AS text)) " +
            "AND (CAST(:fromTs AS timestamptz) IS NULL OR created_at >= CAST(:fromTs AS timestamptz)) " +
            "AND (CAST(:toTs AS timestamptz) IS NULL OR created_at <= CAST(:toTs AS timestamptz)) " +
            "ORDER BY created_at DESC",
        countQuery =
            "SELECT COUNT(*) FROM audit_log " +
            "WHERE (entity_id = CAST(:entityId AS uuid) OR entity_id IS NULL) " +
            "AND (CAST(:action AS text) IS NULL OR action = CAST(:action AS text)) " +
            "AND (CAST(:username AS text) IS NULL OR username = CAST(:username AS text)) " +
            "AND (CAST(:fromTs AS timestamptz) IS NULL OR created_at >= CAST(:fromTs AS timestamptz)) " +
            "AND (CAST(:toTs AS timestamptz) IS NULL OR created_at <= CAST(:toTs AS timestamptz))",
        nativeQuery = true
    )
    Page<AuditLog> findFiltered(
        @Param("entityId") UUID entityId,
        @Param("action") String action,
        @Param("username") String username,
        @Param("fromTs") OffsetDateTime from,
        @Param("toTs") OffsetDateTime to,
        Pageable pageable);
}
