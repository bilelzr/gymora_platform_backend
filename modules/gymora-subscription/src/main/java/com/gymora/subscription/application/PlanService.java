package com.gymora.subscription.application;

import com.gymora.common.error.BusinessRuleException;
import com.gymora.common.error.NotFoundException;
import com.gymora.subscription.application.SubscriptionViews.PlanView;
import com.gymora.subscription.domain.Plan;
import com.gymora.subscription.domain.Plan.PlanDetails;
import com.gymora.subscription.domain.PlanRepository;
import com.gymora.subscription.domain.PlanStatus;
import com.gymora.subscription.domain.SubscriptionRepository;
import com.gymora.tenant.application.GymCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final GymCalendar calendar;

    public PlanService(PlanRepository plans, SubscriptionRepository subscriptions, GymCalendar calendar) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public List<PlanView> list(PlanStatus status) {
        Map<UUID, Long> running = runningCounts();
        return plans.findByStatusOrderByNameAsc(status == null ? PlanStatus.ACTIVE : status).stream()
                .map(plan -> PlanView.of(plan, running.getOrDefault(plan.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanView get(UUID id) {
        return PlanView.of(find(id), runningCounts().getOrDefault(id, 0L));
    }

    @Transactional
    public PlanView create(PlanDetails details) {
        if (plans.existsByNameIgnoreCase(details.name())) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.plan_name_taken", details.name());
        }
        return PlanView.of(plans.save(new Plan(details)), 0L);
    }

    /** Changes apply to future sales only: subscriptions already sold keep their copy of the plan. */
    @Transactional
    public PlanView update(UUID id, PlanDetails details) {
        Plan plan = find(id);
        if (plans.existsByNameIgnoreCaseAndIdNot(details.name(), id)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.plan_name_taken", details.name());
        }
        plan.apply(details);
        plans.flush();
        return get(id);
    }

    @Transactional
    public PlanView archive(UUID id) {
        Plan plan = find(id);
        if (!plan.isArchived()) {
            plan.archive(calendar.now());
        }
        plans.flush();
        return get(id);
    }

    @Transactional
    public PlanView reactivate(UUID id) {
        find(id).reactivate();
        plans.flush();
        return get(id);
    }

    /** A plan that can be sold, or 409 error.plan_archived. */
    Plan sellable(UUID id) {
        Plan plan = find(id);
        if (plan.isArchived()) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.plan_archived", plan.getName());
        }
        return plan;
    }

    private Plan find(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("plan", id));
    }

    private Map<UUID, Long> runningCounts() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : subscriptions.runningCountsByPlan(calendar.today())) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }
}
