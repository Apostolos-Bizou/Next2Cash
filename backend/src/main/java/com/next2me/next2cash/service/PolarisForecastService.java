package com.next2me.next2cash.service;

import com.next2me.next2cash.model.ActualsRow;
import com.next2me.next2cash.model.CostMilestone;
import com.next2me.next2cash.model.ForecastAssumption;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ActualsRowRepository;
import com.next2me.next2cash.repository.CostMilestoneRepository;
import com.next2me.next2cash.repository.ForecastAssumptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.next2me.next2cash.service.PolarisPayrollService.requireText;

/**
 * Polaris P&L -- forecast assumptions + actuals rows (Phase 1, Oct 2026).
 *
 * Access control is NOT done here: PolarisForecastController runs the
 * per-endpoint entity_roles guard before calling in (same as
 * PolarisPayrollService). Rows are looked up by (id, entityId) so foreign
 * ids are 404.
 *
 * forecast_assumptions is UNIQUE (entity_id, key): a duplicate key on create
 * or rename is rejected with 409 before it reaches the database.
 */
@Service
@RequiredArgsConstructor
public class PolarisForecastService {

    private final ForecastAssumptionRepository forecastAssumptionRepository;
    private final ActualsRowRepository actualsRowRepository;
    private final CostMilestoneRepository costMilestoneRepository;
    private final AuditLogService auditLogService;

    // -------------------------------------------------------------------
    // cost_milestones (OPEX forecast, Oct 2026)
    // -------------------------------------------------------------------

    public List<CostMilestone> listCostMilestones(UUID entityId) {
        return costMilestoneRepository.findByEntityIdOrderByCategoryAscTargetDateAsc(entityId);
    }

    public CostMilestone getCostMilestone(UUID entityId, UUID id) {
        return findCostMilestone(entityId, id);
    }

    @Transactional
    public CostMilestone createCostMilestone(User user, UUID entityId, CostMilestone body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        if (body.getTargetDate() == null) throw new IllegalArgumentException("targetDate is required");
        if (body.getTargetValue() == null) throw new IllegalArgumentException("targetValue is required");

        CostMilestone c = new CostMilestone();
        c.setEntityId(entityId);
        c.setCategory(validCategory(body.getCategory()));
        c.setTargetDate(body.getTargetDate().withDayOfMonth(1));
        c.setTargetValue(PolarisPayrollService.nonNegative(body.getTargetValue(), "targetValue"));

        CostMilestone saved = costMilestoneRepository.save(c);
        audit(user, entityId, "POLARIS_COST_MILESTONE_CREATE", "cost_milestones", saved.getId(),
            "category=" + saved.getCategory() + ", targetDate=" + saved.getTargetDate());
        return saved;
    }

    /** Partial update: null fields in the body leave the stored value unchanged. */
    @Transactional
    public CostMilestone updateCostMilestone(User user, UUID entityId, UUID id, CostMilestone body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        CostMilestone c = findCostMilestone(entityId, id);

        if (body.getCategory() != null) c.setCategory(validCategory(body.getCategory()));
        if (body.getTargetDate() != null) c.setTargetDate(body.getTargetDate().withDayOfMonth(1));
        if (body.getTargetValue() != null) c.setTargetValue(PolarisPayrollService.nonNegative(body.getTargetValue(), "targetValue"));

        CostMilestone saved = costMilestoneRepository.save(c);
        audit(user, entityId, "POLARIS_COST_MILESTONE_UPDATE", "cost_milestones", saved.getId(),
            "category=" + saved.getCategory() + ", targetDate=" + saved.getTargetDate());
        return saved;
    }

    @Transactional
    public void deleteCostMilestone(User user, UUID entityId, UUID id) {
        CostMilestone c = findCostMilestone(entityId, id);
        costMilestoneRepository.delete(c);
        audit(user, entityId, "POLARIS_COST_MILESTONE_DELETE", "cost_milestones", id, "Hard delete");
    }

    private CostMilestone findCostMilestone(UUID entityId, UUID id) {
        return costMilestoneRepository.findByIdAndEntityId(id, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cost milestone not found"));
    }

    /** 400 unless the category is one of the agreed "Λοιπά OPEX Διοίκησης" labels. */
    private static String validCategory(String category) {
        String c = requireText(category, "category", 200);
        if (!PolarisForecastMath.OPEX_CATEGORIES.contains(c)) {
            throw new IllegalArgumentException("category must be one of " + PolarisForecastMath.OPEX_CATEGORIES);
        }
        return c;
    }

    // -------------------------------------------------------------------
    // forecast_assumptions
    // -------------------------------------------------------------------

    public List<ForecastAssumption> listAssumptions(UUID entityId) {
        return forecastAssumptionRepository.findByEntityIdOrderByKeyAsc(entityId);
    }

    public ForecastAssumption getAssumption(UUID entityId, UUID id) {
        return findAssumption(entityId, id);
    }

    @Transactional
    public ForecastAssumption createAssumption(User user, UUID entityId, ForecastAssumption body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        String key = requireText(body.getKey(), "key", 50);
        if (body.getValue() == null) throw new IllegalArgumentException("value is required");
        assertKeyFree(entityId, key, null);

        ForecastAssumption a = new ForecastAssumption();
        a.setEntityId(entityId);
        a.setKey(key);
        a.setValue(body.getValue());

        ForecastAssumption saved = forecastAssumptionRepository.save(a);
        audit(user, entityId, "POLARIS_ASSUMPTION_CREATE", "forecast_assumptions", saved.getId(),
            "key=" + saved.getKey() + ", value=" + saved.getValue());
        return saved;
    }

    /** Partial update: null fields in the body leave the stored value unchanged. */
    @Transactional
    public ForecastAssumption updateAssumption(User user, UUID entityId, UUID id, ForecastAssumption body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        ForecastAssumption a = findAssumption(entityId, id);

        if (body.getKey() != null) {
            String key = requireText(body.getKey(), "key", 50);
            assertKeyFree(entityId, key, a.getId());
            a.setKey(key);
        }
        if (body.getValue() != null) a.setValue(body.getValue());

        ForecastAssumption saved = forecastAssumptionRepository.save(a);
        audit(user, entityId, "POLARIS_ASSUMPTION_UPDATE", "forecast_assumptions", saved.getId(),
            "key=" + saved.getKey() + ", value=" + saved.getValue());
        return saved;
    }

    @Transactional
    public void deleteAssumption(User user, UUID entityId, UUID id) {
        ForecastAssumption a = findAssumption(entityId, id);
        forecastAssumptionRepository.delete(a);
        audit(user, entityId, "POLARIS_ASSUMPTION_DELETE", "forecast_assumptions", id, "key=" + a.getKey());
    }

    // -------------------------------------------------------------------
    // actuals_rows
    // -------------------------------------------------------------------

    public List<ActualsRow> listActuals(UUID entityId) {
        return actualsRowRepository.findByEntityIdOrderByPeriodStartDescGroupKeyAscLabelAsc(entityId);
    }

    public ActualsRow getActual(UUID entityId, UUID id) {
        return findActual(entityId, id);
    }

    @Transactional
    public ActualsRow createActual(User user, UUID entityId, ActualsRow body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        if (body.getPeriodStart() == null) throw new IllegalArgumentException("periodStart is required");
        if (body.getPeriodEnd() == null) throw new IllegalArgumentException("periodEnd is required");

        ActualsRow r = new ActualsRow();
        r.setEntityId(entityId);
        r.setGroupKey(requireText(body.getGroupKey(), "groupKey", 50));
        r.setLabel(requireText(body.getLabel(), "label", 200));
        r.setBeginAmount(body.getBeginAmount() != null ? body.getBeginAmount() : BigDecimal.ZERO);
        r.setChangeAmount(body.getChangeAmount() != null ? body.getChangeAmount() : BigDecimal.ZERO);
        r.setPeriodStart(body.getPeriodStart());
        r.setPeriodEnd(body.getPeriodEnd());
        assertPeriod(r);

        ActualsRow saved = actualsRowRepository.save(r);
        audit(user, entityId, "POLARIS_ACTUALS_CREATE", "actuals_rows", saved.getId(),
            "groupKey=" + saved.getGroupKey() + ", period=" + saved.getPeriodStart() + ".." + saved.getPeriodEnd());
        return saved;
    }

    /** Partial update: null fields in the body leave the stored value unchanged. */
    @Transactional
    public ActualsRow updateActual(User user, UUID entityId, UUID id, ActualsRow body) {
        if (body == null) throw new IllegalArgumentException("Request body is required");
        ActualsRow r = findActual(entityId, id);

        if (body.getGroupKey() != null) r.setGroupKey(requireText(body.getGroupKey(), "groupKey", 50));
        if (body.getLabel() != null) r.setLabel(requireText(body.getLabel(), "label", 200));
        if (body.getBeginAmount() != null) r.setBeginAmount(body.getBeginAmount());
        if (body.getChangeAmount() != null) r.setChangeAmount(body.getChangeAmount());
        if (body.getPeriodStart() != null) r.setPeriodStart(body.getPeriodStart());
        if (body.getPeriodEnd() != null) r.setPeriodEnd(body.getPeriodEnd());
        assertPeriod(r);

        ActualsRow saved = actualsRowRepository.save(r);
        audit(user, entityId, "POLARIS_ACTUALS_UPDATE", "actuals_rows", saved.getId(), null);
        return saved;
    }

    @Transactional
    public void deleteActual(User user, UUID entityId, UUID id) {
        ActualsRow r = findActual(entityId, id);
        actualsRowRepository.delete(r);
        audit(user, entityId, "POLARIS_ACTUALS_DELETE", "actuals_rows", id, "Hard delete");
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private ForecastAssumption findAssumption(UUID entityId, UUID id) {
        return forecastAssumptionRepository.findByIdAndEntityId(id, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Forecast assumption not found"));
    }

    private ActualsRow findActual(UUID entityId, UUID id) {
        return actualsRowRepository.findByIdAndEntityId(id, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Actuals row not found"));
    }

    /** 409 if another row of this entity already uses the key. */
    private void assertKeyFree(UUID entityId, String key, UUID selfId) {
        forecastAssumptionRepository.findByEntityIdAndKey(entityId, key)
            .filter(existing -> !existing.getId().equals(selfId))
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assumption key '" + key + "' already exists");
            });
    }

    private static void assertPeriod(ActualsRow r) {
        if (r.getPeriodEnd().isBefore(r.getPeriodStart())) {
            throw new IllegalArgumentException("periodEnd must be on or after periodStart");
        }
    }

    private void audit(User user, UUID entityId, String action, String table, UUID targetId, String details) {
        auditLogService.log(entityId, user.getId(), user.getUsername(), action, table,
            targetId.toString(), details);
    }
}
