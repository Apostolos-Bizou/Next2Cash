package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.PayrollPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PayrollPerson (table payroll_people).
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Every lookup is entity-scoped so
 * a row id from another entity resolves to "not found".
 */
@Repository
public interface PayrollPersonRepository extends JpaRepository<PayrollPerson, UUID> {

    List<PayrollPerson> findByEntityIdOrderByOfficeAscNameAsc(UUID entityId);

    Optional<PayrollPerson> findByIdAndEntityId(UUID id, UUID entityId);
}
