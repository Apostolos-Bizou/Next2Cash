package com.next2me.next2cash.service;

import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.CompanyEntityRepository;
import com.next2me.next2cash.repository.UserEntityRepository;
import com.next2me.next2cash.repository.UserRepository;
import com.next2me.next2cash.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
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
 * Controllers should call either:
 *   - getAccessibleEntityIds(user)      -> for list queries (WHERE entity_id IN (...))
 *   - assertCanAccessEntity(user, id)   -> for single-resource access (throws 403)
 */
@Service
@RequiredArgsConstructor
public class UserAccessService {

    private final UserRepository userRepository;
    private final UserEntityRepository userEntityRepository;
    private final CompanyEntityRepository companyEntityRepository;
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
        List<UUID> assigned = userEntityRepository.findEntityIdsByUserId(user.getId());

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
