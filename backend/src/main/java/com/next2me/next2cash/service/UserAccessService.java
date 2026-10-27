package com.next2me.next2cash.service;

import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.EntityRole;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.CompanyEntityRepository;
import com.next2me.next2cash.repository.EntityRoleRepository;
import com.next2me.next2cash.repository.UserEntityLinkRepository;
import com.next2me.next2cash.repository.UserRepository;
import com.next2me.next2cash.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central service for user access control.
 *
 * Answers the question: "Which entities can THIS user see?"
 *
 * Rules (per Session #3 handoff):
 *   - admin                           -> ALL entity IDs (hardcoded bypass)
 *   - user   WITH    user_entities    -> only the assigned ones
 *   - user   WITHOUT user_entities    -> ALL (legacy compatibility for apostolos)
 *   - accountant / viewer             -> ONLY the assigned ones (empty set if none)
 *
 * Uses UserEntityLinkRepository (JPA-based) for read operations to ensure
 * H2/PostgreSQL compatibility. The older UserEntityRepository (native SQL) is
 * still used by UserController for write operations.
 *
 * Polaris P&L (Oct 2026): a second, additive layer for the Polaris entity only.
 * See getPolarisRole / assertPolarisAdminOnly / assertPolarisAdminOrEditor below. The
 * methods above (getAccessibleEntityIds, canAccessEntity, assertCanAccessEntity)
 * are unchanged and never consult entity_roles.
 */
@Service
@RequiredArgsConstructor
public class UserAccessService {

    /** entities.code of the Polaris entity. */
    public static final String POLARIS_ENTITY_CODE = "polaris";

    public static final String POLARIS_ROLE_ADMIN  = "admin";
    public static final String POLARIS_ROLE_EDITOR = "editor";
    public static final String POLARIS_ROLE_VIEWER = "viewer";

    private static final Set<String> POLARIS_ROLES =
        Set.of(POLARIS_ROLE_ADMIN, POLARIS_ROLE_EDITOR, POLARIS_ROLE_VIEWER);

    private final UserRepository userRepository;
    private final UserEntityLinkRepository userEntityLinkRepository;
    private final CompanyEntityRepository companyEntityRepository;
    private final EntityRoleRepository entityRoleRepository;
    private final JwtUtil jwtUtil;

    /**
     * Extract the authenticated User from the Authorization header.
     * Throws 401 if the token is missing, invalid, or the user no longer exists.
     */
    public User getCurrentUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        String userIdStr;
        try {
            userIdStr = jwtUtil.getUserIdFromToken(token);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user id in token");
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    /**
     * Return the set of entity UUIDs that the given user is allowed to access.
     */
    public Set<UUID> getAccessibleEntityIds(User user) {
        if (user == null) {
            return new HashSet<>();
        }

        String role = user.getRole() == null ? "" : user.getRole().toLowerCase();

        // admin -> all entities, no matter what
        if ("admin".equals(role)) {
            return allEntityIds();
        }

        // Load explicit assignments (from user_entities join table)
        List<UUID> assigned = userEntityLinkRepository.findEntityIdsByUserId(user.getId());

        // user role: if no explicit assignment, default to ALL (legacy apostolos scenario)
        // This preserves backwards compatibility until all users get explicit assignments.
        if ("user".equals(role)) {
            if (assigned == null || assigned.isEmpty()) {
                return allEntityIds();
            }
            return new HashSet<>(assigned);
        }

        // accountant / viewer: strict. Empty assignment -> empty set -> sees nothing.
        if ("accountant".equals(role) || "viewer".equals(role)) {
            return assigned == null ? new HashSet<>() : new HashSet<>(assigned);
        }

        // Unknown role -> empty (deny by default)
        return new HashSet<>();
    }

    /**
     * Convenience: does this user have access to a specific entity?
     */
    public boolean canAccessEntity(User user, UUID entityId) {
        if (entityId == null) return false;
        return getAccessibleEntityIds(user).contains(entityId);
    }

    /**
     * Guard method. Throws 403 if the user cannot access the given entity.
     * Use at the start of any endpoint that receives entity_id from the client.
     */
    public void assertCanAccessEntity(User user, UUID entityId) {
        if (entityId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityId is required");
        }
        if (!canAccessEntity(user, entityId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied for entity " + entityId);
        }
    }

    // -------------------------------------------------------------------
    // Polaris entity_roles layer (Polaris P&L, Oct 2026)
    // -------------------------------------------------------------------

    /**
     * Is this the Polaris entity? Resolved by entities.code (not a hardcoded
     * UUID) so H2 tests and production behave the same.
     */
    public boolean isPolarisEntity(UUID entityId) {
        if (entityId == null) return false;
        return companyEntityRepository.findById(entityId)
            .map(e -> POLARIS_ENTITY_CODE.equalsIgnoreCase(e.getCode()))
            .orElse(false);
    }

    /**
     * The user's entity_roles role (admin / editor / viewer) on the Polaris entity.
     *
     * entity_roles is read ONLY when entityId is the Polaris entity; for any
     * other entity this returns empty without touching the table. A role is
     * granted only when ALL hold:
     *   - the user is active,
     *   - the existing scope check passes (canAccessEntity -- user_entities /
     *     admin bypass, unchanged), and
     *   - an entity_roles row exists with a known role.
     * There is no global-admin bypass: no row -> no Polaris access.
     */
    public Optional<String> getPolarisRole(User user, UUID entityId) {
        if (user == null || entityId == null) return Optional.empty();
        if (!isPolarisEntity(entityId)) return Optional.empty();
        if (!Boolean.TRUE.equals(user.getIsActive())) return Optional.empty();
        if (!canAccessEntity(user, entityId)) return Optional.empty();

        return entityRoleRepository.findByUserIdAndEntityId(user.getId(), entityId)
            .map(EntityRole::getRole)
            .map(r -> r.trim().toLowerCase())
            .filter(POLARIS_ROLES::contains);
    }

    /**
     * Guard: entity_roles role must be admin. Used per endpoint by the Polaris
     * controllers (see the access matrix in PolarisPayrollController /
     * PolarisForecastController).
     * 400 if entityId is missing or not Polaris, 403 for any other role or none.
     */
    public void assertPolarisAdminOnly(User user, UUID entityId) {
        assertPolarisRole(user, entityId, Set.of(POLARIS_ROLE_ADMIN));
    }

    /**
     * Guard: entity_roles role must be admin or editor. viewer -> 403.
     * 400 if entityId is missing or not Polaris.
     */
    public void assertPolarisAdminOrEditor(User user, UUID entityId) {
        assertPolarisRole(user, entityId, Set.of(POLARIS_ROLE_ADMIN, POLARIS_ROLE_EDITOR));
    }

    private void assertPolarisRole(User user, UUID entityId, Set<String> allowed) {
        assertPolarisEntity(entityId);
        String role = getPolarisRole(user, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied for entity " + entityId));
        if (!allowed.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Polaris role '" + role + "' is not allowed here");
        }
    }

    private void assertPolarisEntity(UUID entityId) {
        if (entityId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityId is required");
        }
        if (!isPolarisEntity(entityId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This endpoint is only available for the Polaris entity");
        }
    }

    /**
     * Internal: fetch all entity IDs (used for admin bypass + legacy user default).
     * We load only active entities; soft-deleted entities are excluded.
     */
    private Set<UUID> allEntityIds() {
        return companyEntityRepository.findAll().stream()
            .map(CompanyEntity::getId)
            .collect(Collectors.toSet());
    }
}