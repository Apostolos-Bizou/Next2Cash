package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.RecurrencePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for RecurrencePattern.
 *
 * Created in Phase 1 (Session 3, May 2026) as part of the Cash Planning Module.
 * Master data with UUID primary key.
 *
 * Minimal surface for now: full CRUD via JpaRepository plus a couple of
 * convenience queries for the recurrence engine. Will grow in Session 4/5.
 */
@Repository
public interface RecurrencePatternRepository extends JpaRepository<RecurrencePattern, UUID> {

    /**
     * All patterns whose start_date is on or before the given date. Used by
     * the recurrence engine to find patterns that have already begun firing.
     */
    @Query("SELECT r FROM RecurrencePattern r WHERE r.endDate IS NULL OR r.endDate >= :asOf")
    List<RecurrencePattern> findActiveAsOf(@Param("asOf") LocalDate asOf);
}
