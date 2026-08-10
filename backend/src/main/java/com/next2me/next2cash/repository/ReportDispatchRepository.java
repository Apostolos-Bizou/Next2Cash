package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.ReportDispatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportDispatchRepository extends JpaRepository<ReportDispatch, UUID> {

    /** Archive listing for an entity, newest sent first. */
    List<ReportDispatch> findByEntityIdOrderBySentDateDescCreatedAtDesc(UUID entityId);

    /** Archive listing filtered by sent_date range (inclusive), newest first. */
    List<ReportDispatch> findByEntityIdAndSentDateBetweenOrderBySentDateDescCreatedAtDesc(
        UUID entityId, LocalDate from, LocalDate to);

    /** Entity-scoped single lookup — S77 guard against cross-entity access. */
    Optional<ReportDispatch> findByIdAndEntityId(UUID id, UUID entityId);
}
