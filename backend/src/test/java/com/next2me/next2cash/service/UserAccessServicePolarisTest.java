package com.next2me.next2cash.service;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.UserRepository;
import com.next2me.next2cash.support.PolarisTestSupport;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Polaris P&L Phase 1 -- UserAccessService entity_roles layer.
 *
 * Two groups:
 *   1. The existing path (getAccessibleEntityIds / canAccessEntity) behaves
 *      exactly as before, and never looks at entity_roles.
 *   2. getPolarisRole / assertPolarisRead / assertPolarisAdmin for Polaris.
 */
class UserAccessServicePolarisTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;
    @Autowired private PolarisTestSupport pts;
    @Autowired private UserAccessService userAccessService;
    @Autowired private UserRepository userRepository;

    private CompanyEntity next2me;
    private CompanyEntity house;
    private CompanyEntity group;
    private CompanyEntity polaris;

    @BeforeEach
    void setup() {
        next2me = tdb.createEntity("next2me", "Next2Me");
        house   = tdb.createEntity("house", "House");
        group   = tdb.createEntity("next2megroup", "Next2Me Group");
        polaris = pts.createPolaris();
    }

    // ─── 1. Existing path unchanged ──────────────────────────────────────

    @Test
    @DisplayName("existing: admin still sees ALL entities (incl. Polaris)")
    void existing_adminSeesAll() {
        User admin = tdb.createAdmin("apostolos");
        assertTrue(userAccessService.getAccessibleEntityIds(admin).containsAll(
            Set.of(next2me.getId(), house.getId(), group.getId(), polaris.getId())));
    }

    @Test
    @DisplayName("existing: viewer sees only assigned entities -- entity_roles rows are ignored")
    void existing_viewerScopeIgnoresEntityRoles() {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, group);
        // A Polaris role row WITHOUT user_entities must not widen the existing scope.
        pts.grantRole(simos, polaris, "viewer");

        assertEquals(Set.of(group.getId()), userAccessService.getAccessibleEntityIds(simos));
        assertFalse(userAccessService.canAccessEntity(simos, polaris.getId()));
    }

    @Test
    @DisplayName("existing: an entity_roles 'viewer' row on House does not block a House user")
    void existing_entityRoleOnNonPolarisHasNoEffect() {
        User sissy = tdb.createUser("sissy");
        tdb.assignEntities(sissy, house);
        pts.grantRole(sissy, house, "viewer");

        assertTrue(userAccessService.canAccessEntity(sissy, house.getId()));
        assertDoesNotThrow(() -> userAccessService.assertCanAccessEntity(sissy, house.getId()));
    }

    // ─── 2. Polaris layer ────────────────────────────────────────────────

    @Test
    @DisplayName("isPolarisEntity: true only for code 'polaris'")
    void isPolarisEntity() {
        assertTrue(userAccessService.isPolarisEntity(polaris.getId()));
        assertFalse(userAccessService.isPolarisEntity(group.getId()));
        assertFalse(userAccessService.isPolarisEntity(UUID.randomUUID()));
        assertFalse(userAccessService.isPolarisEntity(null));
    }

    @Test
    @DisplayName("getPolarisRole: admin row -> 'admin'")
    void polarisRole_adminRow() {
        User admin = tdb.createAdmin("apostolos");
        pts.assignWithRole(admin, polaris, "admin");
        assertEquals(Optional.of("admin"), userAccessService.getPolarisRole(admin, polaris.getId()));
    }

    @Test
    @DisplayName("getPolarisRole: role value is normalized (case/whitespace)")
    void polarisRole_normalized() {
        User admin = tdb.createAdmin("apostolos");
        pts.assignWithRole(admin, polaris, " ADMIN ");
        assertEquals(Optional.of("admin"), userAccessService.getPolarisRole(admin, polaris.getId()));
    }

    @Test
    @DisplayName("getPolarisRole: global admin WITHOUT a row -> empty (no bypass)")
    void polarisRole_globalAdminWithoutRow() {
        User admin = tdb.createAdmin("other-admin");
        assertTrue(userAccessService.getPolarisRole(admin, polaris.getId()).isEmpty());
    }

    @Test
    @DisplayName("getPolarisRole: row on a non-Polaris entity -> empty")
    void polarisRole_nonPolarisEntity() {
        User admin = tdb.createAdmin("apostolos");
        pts.grantRole(admin, house, "admin");
        assertTrue(userAccessService.getPolarisRole(admin, house.getId()).isEmpty());
    }

    @Test
    @DisplayName("getPolarisRole: viewer row but no user_entities assignment -> empty")
    void polarisRole_viewerWithoutAssignment() {
        User simos = tdb.createViewer("simos");
        tdb.assignEntities(simos, group);
        pts.grantRole(simos, polaris, "viewer");
        assertTrue(userAccessService.getPolarisRole(simos, polaris.getId()).isEmpty());
    }

    @Test
    @DisplayName("getPolarisRole: unknown role value -> empty")
    void polarisRole_unknownRole() {
        User admin = tdb.createAdmin("apostolos");
        pts.assignWithRole(admin, polaris, "owner");
        assertTrue(userAccessService.getPolarisRole(admin, polaris.getId()).isEmpty());
    }

    @Test
    @DisplayName("getPolarisRole: inactive user -> empty")
    void polarisRole_inactiveUser() {
        User admin = tdb.createAdmin("apostolos");
        pts.assignWithRole(admin, polaris, "admin");
        admin.setIsActive(false);
        userRepository.save(admin);
        assertTrue(userAccessService.getPolarisRole(admin, polaris.getId()).isEmpty());
    }

    @Test
    @DisplayName("guards: null entity -> 400, non-Polaris entity -> 400, no role -> 403 (both guards)")
    void guards_errors() {
        User admin = tdb.createAdmin("apostolos");

        assertStatus(HttpStatus.BAD_REQUEST, () -> userAccessService.assertPolarisAdminOnly(admin, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> userAccessService.assertPolarisAdminOrEditor(admin, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> userAccessService.assertPolarisAdminOnly(admin, house.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> userAccessService.assertPolarisAdminOrEditor(admin, house.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> userAccessService.assertPolarisAdminOnly(admin, polaris.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> userAccessService.assertPolarisAdminOrEditor(admin, polaris.getId()));
    }

    @Test
    @DisplayName("assertPolarisAdminOnly: admin passes; editor and viewer -> 403")
    void assertPolarisAdminOnly_matrix() {
        User admin  = tdb.createUser("polaris-admin");
        User editor = tdb.createUser("polaris-editor");
        User viewer = tdb.createUser("polaris-viewer");
        pts.assignWithRole(admin, polaris, "admin");
        pts.assignWithRole(editor, polaris, "editor");
        pts.assignWithRole(viewer, polaris, "viewer");

        assertDoesNotThrow(() -> userAccessService.assertPolarisAdminOnly(admin, polaris.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> userAccessService.assertPolarisAdminOnly(editor, polaris.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> userAccessService.assertPolarisAdminOnly(viewer, polaris.getId()));
    }

    @Test
    @DisplayName("assertPolarisAdminOrEditor: admin and editor pass; viewer -> 403")
    void assertPolarisAdminOrEditor_matrix() {
        User admin  = tdb.createUser("polaris-admin");
        User editor = tdb.createUser("polaris-editor");
        User viewer = tdb.createUser("polaris-viewer");
        pts.assignWithRole(admin, polaris, "admin");
        pts.assignWithRole(editor, polaris, "editor");
        pts.assignWithRole(viewer, polaris, "viewer");

        assertDoesNotThrow(() -> userAccessService.assertPolarisAdminOrEditor(admin, polaris.getId()));
        assertDoesNotThrow(() -> userAccessService.assertPolarisAdminOrEditor(editor, polaris.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> userAccessService.assertPolarisAdminOrEditor(viewer, polaris.getId()));
    }

    @Test
    @DisplayName("assertPolarisRead: admin, editor and viewer pass; no role -> 403, non-Polaris -> 400")
    void assertPolarisRead_anyRole() {
        User admin  = tdb.createUser("polaris-admin");
        User editor = tdb.createUser("polaris-editor");
        User viewer = tdb.createUser("polaris-viewer");
        User none   = tdb.createUser("polaris-none");
        pts.assignWithRole(admin, polaris, "admin");
        pts.assignWithRole(editor, polaris, "editor");
        pts.assignWithRole(viewer, polaris, "viewer");
        tdb.assignEntities(none, polaris);

        assertDoesNotThrow(() -> userAccessService.assertPolarisRead(admin, polaris.getId()));
        assertDoesNotThrow(() -> userAccessService.assertPolarisRead(editor, polaris.getId()));
        assertDoesNotThrow(() -> userAccessService.assertPolarisRead(viewer, polaris.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> userAccessService.assertPolarisRead(none, polaris.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> userAccessService.assertPolarisRead(viewer, house.getId()));
    }

    private static void assertStatus(HttpStatus expected, Runnable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(expected, ex.getStatusCode());
    }
}
