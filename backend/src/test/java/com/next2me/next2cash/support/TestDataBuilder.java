package com.next2me.next2cash.support;

import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.CompanyEntityRepository;
import com.next2me.next2cash.repository.UserEntityRepository;
import com.next2me.next2cash.repository.UserRepository;
import com.next2me.next2cash.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Test data factory.
 *
 * Creates realistic User and CompanyEntity records via the real JPA repositories,
 * so fixtures go through the same code paths as production (BCrypt hashing,
 * JPA lifecycle, etc).
 *
 * Thanks to @Transactional on BaseIntegrationTest, everything this builder
 * creates is rolled back at the end of each test -> clean state between tests.
 *
 * Common passwords used in tests (so MockMvc can also log in via the real endpoint):
 *   admin -> "admin-password-123"
 *   user  -> "user-password-123"
 */
@Component
public class TestDataBuilder {

    @Autowired private UserRepository userRepository;
    @Autowired private CompanyEntityRepository companyEntityRepository;
    @Autowired private UserEntityRepository userEntityRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    public static final String ADMIN_PASSWORD = "admin-password-123";
    public static final String USER_PASSWORD  = "user-password-123";

    // β”€β”€β”€ User creation β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€

    /** Create an admin user with a known password. */
    public User createAdmin(String username) {
        return createUser(username, ADMIN_PASSWORD, "admin");
    }

    /** Create a regular user with a known password. */
    public User createUser(String username) {
        return createUser(username, USER_PASSWORD, "user");
    }

    /** Create an accountant with a known password. */
    public User createAccountant(String username) {
        return createUser(username, USER_PASSWORD, "accountant");
    }

    /** Create a viewer with a known password. */
    public User createViewer(String username) {
        return createUser(username, USER_PASSWORD, "viewer");
    }

    /** Low-level user creator. */
    public User createUser(String username, String plainPassword, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(plainPassword));
        u.setDisplayName(capitalize(username));
        u.setEmail(username + "@test.next2me.local");
        u.setRole(role);
        u.setIsActive(true);
        return userRepository.save(u);
    }

    // β”€β”€β”€ Entity creation β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€

    /**
     * Create a CompanyEntity. We set only the fields the production code relies on
     * plus whatever NOT-NULL columns exist in the schema.
     */
    public CompanyEntity createEntity(String code, String name) {
        CompanyEntity e = new CompanyEntity();
        e.setCode(code);
        e.setName(name);
        // Defensive defaults for common NOT-NULL columns.
        // If any of these setters doesn't exist, the compile will tell us and we adjust.
        trySet(e, "setCountry", "GR");
        trySet(e, "setCurrency", "EUR");
        trySet(e, "setIsActive", Boolean.TRUE);
        trySet(e, "setSortOrder", 1);
        return companyEntityRepository.save(e);
    }

    /**
     * Quick helper: create 3 typical entities (Next2Me, House, Polaris) similar to production.
     */
    public CompanyEntity[] createStandardEntities() {
        return new CompanyEntity[] {
            createEntity("NEXT2ME", "Next2Me"),
            createEntity("HOUSE",   "House"),
            createEntity("POLARIS", "Polaris")
        };
    }

    // β”€β”€β”€ Assignment (user_entities) β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€

    /** Assign the given entity IDs to the user (explicit access list). */
    public void assignEntities(User user, CompanyEntity... entities) {
        for (CompanyEntity e : entities) {
            userEntityRepository.insertUserEntity(user.getId(), e.getId());
        }
    }

    // β”€β”€β”€ JWT helpers β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€

    /**
     * Build a Bearer token string ready to be used in Authorization header.
     *   headers.set("Authorization", tdb.bearerToken(user));
     */
    public String bearerToken(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), user.getId(), null, null);
        return "Bearer " + token;
    }

    // β”€β”€β”€ Internals β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Best-effort setter invocation. If the setter doesn't exist (because the entity
     * doesn't have that column), we silently skip β€” no test-time breakage from
     * entity schema evolution.
     */
    private static void trySet(Object target, String methodName, Object value) {
        try {
            for (var m : target.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                    m.invoke(target, value);
                    return;
                }
            }
        } catch (Exception ignore) { /* best-effort only */ }
    }
}