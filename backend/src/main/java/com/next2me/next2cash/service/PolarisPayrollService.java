package com.next2me.next2cash.service;

import com.next2me.next2cash.model.PayrollMilestone;
import com.next2me.next2cash.model.PayrollPerson;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.PayrollMilestoneRepository;
import com.next2me.next2cash.repository.PayrollPersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Polaris P&L -- payroll people + member milestones (Phase 1, Oct 2026).
 *
 * Access control is NOT done here: PolarisPayrollController runs the
 * per-endpoint entity_roles guard (UserAccessService#assertPolarisAdminOnly /
 * #assertPolarisAdminOrEditor) before calling in. Callers must do the same.
 * Rows are always looked up by (id, entityId), so an id that belongs to a
 * different entity is reported as 404.
 *
 * Validation failures throw IllegalArgumentException -> HTTP 400 via
 * GlobalExceptionHandler. Audit details never include salary amounts.
 */
@Service
@RequiredArgsConstructor
public class PolarisPayrollService {

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final PayrollPersonRepository payrollPersonRepository;
    private final PayrollMilestoneRepository payrollMilestoneRepository;
    private final AuditLogService auditLogService;

    // -------------------------------------------------------------------
    // payroll_people
    // -------------------------------------------------------------------

    public List<PayrollPerson> listPeople(UUID entityId) {
        return payrollPersonRepository.findByEntityIdOrderByOfficeAscNameAsc(entityId);
    }

    public PayrollPerson getPerson(UUID entityId, UUID id) {
        return findPerson(entityId, id);
    }

    @Transactional
    public PayrollPerson createPerson(User user, UUID entityId, PayrollPerson body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");

        PayrollPerson p = new PayrollPerson();
        p.setEntityId(entityId);
        p.setOffice(requireText(body.getOffice(), "office", 20).toUpperCase());
        p.setName(requireText(body.getName(), "name", 150));
        p.setRoleTitle(optionalText(body.getRoleTitle(), "roleTitle", 150));
        p.setSalary(nonNegative(body.getSalary() != null ? body.getSalary() : BigDecimal.ZERO, "salary"));
        p.setRaisePct(validRaisePct(body.getRaisePct() != null ? body.getRaisePct() : BigDecimal.ZERO));
        if (body.getEffectiveFromYear() == null) {
            throw new IllegalArgumentException("effectiveFromYear is required");
        }
        p.setEffectiveFromYear(validYear(body.getEffectiveFromYear()));
        p.setIsOfficeCost(Boolean.TRUE.equals(body.getIsOfficeCost()));

        PayrollPerson saved = payrollPersonRepository.save(p);
        audit(user, entityId, "POLARIS_PAYROLL_PERSON_CREATE", "payroll_people", saved.getId(),
            "office=" + saved.getOffice());
        return saved;
    }

    /** Partial update: null fields in the body leave the stored value unchanged. */
    @Transactional
    public PayrollPerson updatePerson(User user, UUID entityId, UUID id, PayrollPerson body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        PayrollPerson p = findPerson(entityId, id);

        if (body.getOffice() != null) p.setOffice(requireText(body.getOffice(), "office", 20).toUpperCase());
        if (body.getName() != null) p.setName(requireText(body.getName(), "name", 150));
        if (body.getRoleTitle() != null) p.setRoleTitle(optionalText(body.getRoleTitle(), "roleTitle", 150));
        if (body.getSalary() != null) p.setSalary(nonNegative(body.getSalary(), "salary"));
        if (body.getRaisePct() != null) p.setRaisePct(validRaisePct(body.getRaisePct()));
        if (body.getEffectiveFromYear() != null) p.setEffectiveFromYear(validYear(body.getEffectiveFromYear()));
        if (body.getIsOfficeCost() != null) p.setIsOfficeCost(body.getIsOfficeCost());

        PayrollPerson saved = payrollPersonRepository.save(p);
        audit(user, entityId, "POLARIS_PAYROLL_PERSON_UPDATE", "payroll_people", saved.getId(), null);
        return saved;
    }

    @Transactional
    public void deletePerson(User user, UUID entityId, UUID id) {
        PayrollPerson p = findPerson(entityId, id);
        payrollPersonRepository.delete(p);
        audit(user, entityId, "POLARIS_PAYROLL_PERSON_DELETE", "payroll_people", id, "Hard delete");
    }

    // -------------------------------------------------------------------
    // payroll_milestones
    // -------------------------------------------------------------------

    public List<PayrollMilestone> listMilestones(UUID entityId) {
        return payrollMilestoneRepository.findByEntityIdOrderByTargetMonthAsc(entityId);
    }

    public PayrollMilestone getMilestone(UUID entityId, UUID id) {
        return findMilestone(entityId, id);
    }

    @Transactional
    public PayrollMilestone createMilestone(User user, UUID entityId, PayrollMilestone body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        if (body.getTargetMonth() == null) throw new IllegalArgumentException("targetMonth is required");
        if (body.getMembersTarget() == null) throw new IllegalArgumentException("membersTarget is required");

        PayrollMilestone m = new PayrollMilestone();
        m.setEntityId(entityId);
        m.setTargetMonth(body.getTargetMonth().withDayOfMonth(1));
        m.setMembersTarget(nonNegative(body.getMembersTarget(), "membersTarget"));

        PayrollMilestone saved = payrollMilestoneRepository.save(m);
        audit(user, entityId, "POLARIS_PAYROLL_MILESTONE_CREATE", "payroll_milestones", saved.getId(),
            "targetMonth=" + saved.getTargetMonth() + ", membersTarget=" + saved.getMembersTarget());
        return saved;
    }

    /** Partial update: null fields in the body leave the stored value unchanged. */
    @Transactional
    public PayrollMilestone updateMilestone(User user, UUID entityId, UUID id, PayrollMilestone body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        PayrollMilestone m = findMilestone(entityId, id);

        if (body.getTargetMonth() != null) m.setTargetMonth(body.getTargetMonth().withDayOfMonth(1));
        if (body.getMembersTarget() != null) m.setMembersTarget(nonNegative(body.getMembersTarget(), "membersTarget"));

        PayrollMilestone saved = payrollMilestoneRepository.save(m);
        audit(user, entityId, "POLARIS_PAYROLL_MILESTONE_UPDATE", "payroll_milestones", saved.getId(),
            "targetMonth=" + saved.getTargetMonth() + ", membersTarget=" + saved.getMembersTarget());
        return saved;
    }

    @Transactional
    public void deleteMilestone(User user, UUID entityId, UUID id) {
        PayrollMilestone m = findMilestone(entityId, id);
        payrollMilestoneRepository.delete(m);
        audit(user, entityId, "POLARIS_PAYROLL_MILESTONE_DELETE", "payroll_milestones", id, "Hard delete");
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private PayrollPerson findPerson(UUID entityId, UUID id) {
        return payrollPersonRepository.findByIdAndEntityId(id, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll person not found"));
    }

    private PayrollMilestone findMilestone(UUID entityId, UUID id) {
        return payrollMilestoneRepository.findByIdAndEntityId(id, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll milestone not found"));
    }

    private void audit(User user, UUID entityId, String action, String table, UUID targetId, String details) {
        auditLogService.log(entityId, user.getId(), user.getUsername(), action, table,
            targetId.toString(), details);
    }

    static String requireText(String value, String field, int maxLen) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String v = value.trim();
        if (v.length() > maxLen) {
            throw new IllegalArgumentException(field + " must be at most " + maxLen + " characters");
        }
        return v;
    }

    static String optionalText(String value, String field, int maxLen) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, field, maxLen);
    }

    static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value.signum() < 0) throw new IllegalArgumentException(field + " must be >= 0");
        return value;
    }

    private static Integer nonNegative(Integer value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " must be >= 0");
        return value;
    }

    /** numeric(6,2): a cut down to -100% is the floor; anything above 1000% is a typo. */
    private static BigDecimal validRaisePct(BigDecimal value) {
        if (value.compareTo(BigDecimal.valueOf(-100)) < 0 || value.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new IllegalArgumentException("raisePct must be between -100 and 1000");
        }
        return value;
    }

    private static Integer validYear(Integer year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException("effectiveFromYear must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
        return year;
    }
}
