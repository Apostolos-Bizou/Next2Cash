package com.next2me.next2cash.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA @Entity for the user_entities junction table.
 *
 * Purpose of this class:
 *   1. Makes Hibernate aware of the user_entities table so it can auto-create it
 *      in H2 during tests (fixes the Session #3 schema blocker).
 *   2. In production (ddl-auto=none), Hibernate won't touch the existing table.
 *   3. Coexists with UserEntityRepository's native SQL methods - no runtime conflict
 *      because both map to the same physical table with the same columns.
 *
 * Why columnDefinition = "UUID":
 *   H2 defaults to BINARY(16) for java.util.UUID fields, which breaks native
 *   SQL queries that return UUIDs (the JDBC driver returns byte[], not UUID).
 *   Forcing columnDefinition = "UUID" uses H2's native UUID type, which
 *   matches PostgreSQL behavior exactly. Safe for both DBs.
 *
 * Intentionally minimal:
 *   - We do NOT define @ManyToMany relationships on User or CompanyEntity.
 *   - All existing business logic keeps going through UserEntityRepository's
 *     native SQL queries (findEntitiesByUserId, insertUserEntity, etc.).
 *   - This class exists solely to inform Hibernate about the table's structure.
 */
@Entity
@Table(name = "user_entities")
@IdClass(UserEntityLink.UserEntityLinkId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntityLink {

    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Id
    @Column(name = "entity_id", nullable = false, columnDefinition = "UUID")
    private UUID entityId;

    /**
     * Composite key class required by @IdClass.
     * Must implement Serializable and override equals/hashCode.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserEntityLinkId implements Serializable {
        private UUID userId;
        private UUID entityId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserEntityLinkId)) return false;
            UserEntityLinkId that = (UserEntityLinkId) o;
            return Objects.equals(userId, that.userId)
                && Objects.equals(entityId, that.entityId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, entityId);
        }
    }
}