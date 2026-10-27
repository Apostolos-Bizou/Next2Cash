package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-entity role assignment (admin / editor / viewer).
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Backed by table `entity_roles`
 * (created by the Polaris Phase 0 migration, applied manually via psql).
 *
 * Scope: read ONLY for the Polaris entity (see UserAccessService#getPolarisRole).
 * The other entities keep the existing users.role + user_entities model; rows
 * in this table for any other entity are ignored.
 *
 * role is constrained by a CHECK (role IN ('admin','editor','viewer')) in the
 * database; one row per (user_id, entity_id).
 */
@Entity
@Table(name = "entity_roles",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "entity_id"}))
@Data
@NoArgsConstructor
public class EntityRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** admin / editor / viewer. */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
