package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.PayrollPersonRaise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PayrollPersonRaise (table payroll_person_raises).
 *
 * Rows have no entity_id of their own; the entity scope comes through the
 * owning payroll_people row, so every lookup joins through it.
 */
@Repository
public interface PayrollPersonRaiseRepository extends JpaRepository<PayrollPersonRaise, UUID> {

    List<PayrollPersonRaise> findByPayrollPersonIdOrderByEffectiveDateAsc(UUID payrollPersonId);

    @Query("select r from PayrollPersonRaise r where r.payrollPersonId in "
         + "(select p.id from PayrollPerson p where p.entityId = :entityId) "
         + "order by r.payrollPersonId asc, r.effectiveDate asc")
    List<PayrollPersonRaise> findAllForEntity(@Param("entityId") UUID entityId);

    @Query("select r from PayrollPersonRaise r where r.id = :id and r.payrollPersonId in "
         + "(select p.id from PayrollPerson p where p.entityId = :entityId)")
    Optional<PayrollPersonRaise> findByIdForEntity(@Param("id") UUID id, @Param("entityId") UUID entityId);
}
