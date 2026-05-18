package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p WHERE p.isActive = true ORDER BY p.name")
    List<Project> findAllActive();

    @Query("SELECT p FROM Project p ORDER BY p.name")
    List<Project> findAllOrdered();

    Optional<Project> findByName(String name);

    @Query("SELECT p FROM Project p WHERE p.status = :status ORDER BY p.name")
    List<Project> findByStatus(String status);

    /**
     * S77: Entity-scoped project list (active only).
     * Returns projects whose ownerEntityId is in the given set.
     */
    @Query("SELECT p FROM Project p WHERE p.isActive = true AND p.ownerEntityId IN :entityIds ORDER BY p.name")
    List<Project> findActiveByOwnerEntityIdIn(Set<java.util.UUID> entityIds);

    /**
     * S77: Entity-scoped project list (all, including inactive).
     */
    @Query("SELECT p FROM Project p WHERE p.ownerEntityId IN :entityIds ORDER BY p.name")
    List<Project> findByOwnerEntityIdIn(Set<java.util.UUID> entityIds);

    /**
     * S77: Entity-scoped + status filter.
     */
    @Query("SELECT p FROM Project p WHERE p.status = :status AND p.ownerEntityId IN :entityIds ORDER BY p.name")
    List<Project> findByStatusAndOwnerEntityIdIn(String status, Set<java.util.UUID> entityIds);
}
