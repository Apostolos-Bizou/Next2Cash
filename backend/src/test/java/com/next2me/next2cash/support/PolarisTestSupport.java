package com.next2me.next2cash.support;

import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.EntityRole;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.EntityRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Polaris P&L test fixtures (Phase 1, Oct 2026).
 *
 * Builds on TestDataBuilder: creates the Polaris entity (code "polaris", as in
 * production) and entity_roles rows. Rolled back after each test like every
 * other fixture (BaseIntegrationTest is @Transactional).
 */
@Component
public class PolarisTestSupport {

    @Autowired private TestDataBuilder tdb;
    @Autowired private EntityRoleRepository entityRoleRepository;

    public CompanyEntity createPolaris() {
        return tdb.createEntity("polaris", "Polaris");
    }

    /** Insert an entity_roles row (role is stored as given, no validation). */
    public EntityRole grantRole(User user, CompanyEntity entity, String role) {
        EntityRole r = new EntityRole();
        r.setUserId(user.getId());
        r.setEntityId(entity.getId());
        r.setRole(role);
        return entityRoleRepository.save(r);
    }

    /** user_entities assignment + entity_roles row in one call. */
    public void assignWithRole(User user, CompanyEntity entity, String role) {
        tdb.assignEntities(user, entity);
        grantRole(user, entity, role);
    }
}
