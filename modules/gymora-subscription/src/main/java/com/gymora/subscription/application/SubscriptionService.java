package com.gymora.subscription.application;

import com.gymora.common.error.BusinessRuleException;
import com.gymora.common.error.NotFoundException;
import com.gymora.membership.application.MemberDirectory;
import com.gymora.membership.application.MemberDirectory.MemberSummary;
import com.gymora.security.capability.CapabilityGuard;
import com.gymora.subscription.SubscriptionCapabilities;
import com.gymora.subscription.application.SubscriptionViews.MemberRef;
import com.gymora.subscription.application.SubscriptionViews.PaymentView;
import com.gymora.subscription.application.SubscriptionViews.SaleView;
import com.gymora.subscription.application.SubscriptionViews.SubscriptionCounts;
import com.gymora.subscription.application.SubscriptionViews.SubscriptionRow;
import com.gymora.subscription.application.SubscriptionViews.SubscriptionView;
import com.gymora.subscription.domain.Freeze;
import com.gymora.subscription.domain.FreezeRepository;
import com.gymora.subscription.domain.Plan;
import com.gymora.subscription.domain.Subscription;
import com.gymora.subscription.domain.SubscriptionRepository;
import com.gymora.tenant.application.GymCalendar;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    /** "Expiring soon" means ending within this many days. */
    static final int EXPIRING_WITHIN_DAYS = 7;
    /** "Expired recently" means ended within this many days. */
    static final int EXPIRED_WITHIN_DAYS = 30;

    private final SubscriptionRepository subscriptions;
    private final FreezeRepository freezes;
    private final PlanService plans;
    private final PaymentService payments;
    private final MemberDirectory members;
    private final GymCalendar calendar;
    private final CapabilityGuard guard;

    public SubscriptionService(SubscriptionRepository subscriptions, FreezeRepository freezes, PlanService plans,
                               PaymentService payments, MemberDirectory members, GymCalendar calendar,
                               CapabilityGuard guard) {
        this.subscriptions = subscriptions;
        this.freezes = freezes;
        this.plans = plans;
        this.payments = payments;
        this.members = members;
        this.calendar = calendar;
        this.guard = guard;
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<SubscriptionView> forMember(UUID memberId) {
        members.require(memberId);
        return views(subscriptions.findByMemberIdOrderByStartDateDesc(memberId));
    }

    @Transactional(readOnly = true)
    public SubscriptionView get(UUID id) {
        return views(List.of(find(id))).get(0);
    }

    /** The subscriptions page: one of the follow-up lists, optionally narrowed to members matching {@code query}. */
    @Transactional(readOnly = true)
    public Page<SubscriptionRow> search(SubscriptionFilter filter, String query, int page, int size) {
        if (filter == SubscriptionFilter.UNPAID && !showMoney()) {
            throw new AccessDeniedException("subscription.payment.read required");
        }
        List<UUID> memberIds = null;
        if (query != null && !query.isBlank()) {
            memberIds = members.idsMatching(query);
            if (memberIds.isEmpty()) {
                return Page.empty(PageRequest.of(page, size));
            }
        }
        LocalDate today = calendar.today();
        Sort sort = filter == SubscriptionFilter.EXPIRED
                ? Sort.by(Sort.Direction.DESC, "endDate")
                : Sort.by(Sort.Direction.ASC, "endDate");
        Page<Subscription> found = subscriptions.findAll(spec(filter, today, memberIds), PageRequest.of(page, size, sort));

        List<SubscriptionView> views = views(found.getContent());
        Map<UUID, MemberSummary> people = members.findAll(found.getContent().stream().map(Subscription::getMemberId).toList());
        List<SubscriptionRow> rows = new ArrayList<>();
        for (int i = 0; i < views.size(); i++) {
            Subscription s = found.getContent().get(i);
            boolean renewed = subscriptions.existsByMemberIdAndCancelledAtIsNullAndStartDateAfter(s.getMemberId(), s.getEndDate());
            rows.add(new SubscriptionRow(views.get(i), MemberRef.of(people.get(s.getMemberId())), renewed));
        }
        return new PageImpl<>(rows, found.getPageable(), found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public SubscriptionCounts counts() {
        LocalDate today = calendar.today();
        boolean money = showMoney();
        return new SubscriptionCounts(
                subscriptions.count(spec(SubscriptionFilter.ACTIVE, today, null)),
                subscriptions.count(spec(SubscriptionFilter.EXPIRING, today, null)),
                subscriptions.count(spec(SubscriptionFilter.EXPIRED, today, null)),
                subscriptions.count(spec(SubscriptionFilter.FROZEN, today, null)),
                money ? subscriptions.count(spec(SubscriptionFilter.UNPAID, today, null)) : null,
                money ? subscriptions.unpaidTotalMillimes() : null);
    }

    // ---------- selling ----------

    /**
     * Sells a plan to a member. Without a start date, it starts the day after the member's current
     * subscription ends (a renewal), or today. Optionally takes a first cash payment.
     */
    @Transactional
    public SaleView sell(UUID memberId, Sale sale) {
        MemberSummary member = members.require(memberId);
        if (member.archived()) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.subscription_member_archived");
        }
        Plan plan = plans.sellable(sale.planId());
        long discount = sale.discountMillimes() == null ? 0 : sale.discountMillimes();
        if (discount > plan.getPriceMillimes()) {
            throw new BusinessRuleException("error.discount_too_high", Formats.amount(plan.getPriceMillimes()));
        }
        LocalDate start = sale.startDate() != null ? sale.startDate() : nextStart(memberId);
        Subscription subscription = new Subscription(memberId, plan, start, discount);

        List<Subscription> overlapping = subscriptions.findOverlapping(memberId, start, subscription.getEndDate());
        if (!overlapping.isEmpty()) {
            Subscription other = overlapping.get(0);
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.subscription_overlap",
                    Formats.date(other.getStartDate()), Formats.date(other.getEndDate()));
        }
        subscriptions.save(subscription);

        PaymentView payment = null;
        long payNow = sale.paymentMillimes() == null ? 0 : sale.paymentMillimes();
        if (payNow > 0) {
            payment = payments.record(subscription, payNow, sale.note());
        }
        subscriptions.flush();
        return new SaleView(views(List.of(subscription)).get(0), payment);
    }

    /** Where a new subscription for this member starts by default. */
    @Transactional(readOnly = true)
    public LocalDate nextStart(UUID memberId) {
        LocalDate today = calendar.today();
        return subscriptions.findFirstByMemberIdAndCancelledAtIsNullOrderByEndDateDesc(memberId)
                .map(Subscription::getEndDate)
                .filter(end -> !end.isBefore(today))
                .map(end -> end.plusDays(1))
                .orElse(today);
    }

    // ---------- freezing ----------

    /**
     * Pauses a subscription between two days (both included). Its end moves by as many days, and
     * renewals queued right after it move too, so they never overlap.
     */
    @Transactional
    public SubscriptionView freeze(UUID id, LocalDate firstDay, LocalDate lastDay, String reason) {
        Subscription s = find(id);
        LocalDate today = calendar.today();
        requireRunningOrUpcoming(s, today);
        if (s.getMaxFreezeDays() == 0) {
            throw new BusinessRuleException("error.freeze_not_allowed", s.getPlanName());
        }
        if (lastDay.isBefore(firstDay)) {
            throw new BusinessRuleException("error.freeze_dates");
        }
        if (firstDay.isBefore(today)) {
            throw new BusinessRuleException("error.freeze_in_past");
        }
        if (firstDay.isBefore(s.getStartDate()) || firstDay.isAfter(s.getEndDate())) {
            throw new BusinessRuleException("error.freeze_outside",
                    Formats.date(s.getStartDate()), Formats.date(s.getEndDate()));
        }
        int days = Freeze.daysBetween(firstDay, lastDay);
        if (days > s.freezeDaysLeft()) {
            throw new BusinessRuleException("error.freeze_too_long", s.freezeDaysLeft());
        }
        if (freezes.findFirstBySubscriptionIdAndEndDateGreaterThanEqualOrderByStartDateAsc(id, today).isPresent()) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.freeze_already");
        }
        freezes.save(new Freeze(id, firstDay, lastDay, reason));
        LocalDate oldEnd = s.getEndDate();
        s.addFrozenDays(days);
        pushRenewalsAfter(s, oldEnd);
        subscriptions.flush();
        return get(id);
    }

    /**
     * Ends the current or planned freeze: the member is back today. Unused days are given back
     * and renewals queued right after move back with the end date.
     */
    @Transactional
    public SubscriptionView unfreeze(UUID id) {
        Subscription s = find(id);
        LocalDate today = calendar.today();
        Freeze freeze = freezes.findFirstBySubscriptionIdAndEndDateGreaterThanEqualOrderByStartDateAsc(id, today)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.CONFLICT, "error.freeze_none"));
        int givenBack;
        if (!freeze.getStartDate().isBefore(today)) {
            givenBack = freeze.getDays();
            freezes.delete(freeze);
        } else {
            givenBack = freeze.endOn(today.minusDays(1));
        }
        LocalDate oldEnd = s.getEndDate();
        s.addFrozenDays(-givenBack);
        pullRenewalsBack(s, oldEnd, givenBack);
        subscriptions.flush();
        return get(id);
    }

    // ---------- cancelling ----------

    /** Stops a subscription today. Payments stay in the history; refunds happen outside GYMORA. */
    @Transactional
    public SubscriptionView cancel(UUID id, String reason) {
        Subscription s = find(id);
        requireRunningOrUpcoming(s, calendar.today());
        s.cancel(calendar.now(), calendar.today(), reason, Formats.currentUserName());
        subscriptions.flush();
        return get(id);
    }

    // ---------- helpers ----------

    Subscription find(UUID id) {
        return subscriptions.findById(id).orElseThrow(() -> new NotFoundException("subscription", id));
    }

    private void requireRunningOrUpcoming(Subscription s, LocalDate today) {
        if (s.isCancelled()) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.subscription_cancelled");
        }
        if (s.getEndDate().isBefore(today)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.subscription_expired");
        }
    }

    /** After an end date moved later: move queued renewals just enough that none overlaps. */
    private void pushRenewalsAfter(Subscription s, LocalDate oldEnd) {
        LocalDate previousEnd = s.getEndDate();
        for (Subscription next : subscriptions
                .findByMemberIdAndCancelledAtIsNullAndStartDateAfterOrderByStartDateAsc(s.getMemberId(), oldEnd)) {
            if (next.getStartDate().isAfter(previousEnd)) {
                break;
            }
            next.shift((int) (previousEnd.toEpochDay() - next.getStartDate().toEpochDay()) + 1);
            previousEnd = next.getEndDate();
        }
    }

    /** After an end date moved earlier: renewals that followed it directly follow it again. */
    private void pullRenewalsBack(Subscription s, LocalDate oldEnd, int days) {
        LocalDate expectedStart = oldEnd.plusDays(1);
        for (Subscription next : subscriptions
                .findByMemberIdAndCancelledAtIsNullAndStartDateAfterOrderByStartDateAsc(s.getMemberId(), oldEnd)) {
            if (!next.getStartDate().equals(expectedStart)) {
                break;
            }
            expectedStart = next.getEndDate().plusDays(1);
            next.shift(-days);
        }
    }

    private boolean showMoney() {
        return guard.has(SubscriptionCapabilities.PAYMENT_READ);
    }

    private List<SubscriptionView> views(List<Subscription> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        LocalDate today = calendar.today();
        boolean money = showMoney();
        Map<UUID, List<Freeze>> bySubscription = freezes
                .findBySubscriptionIdInOrderByStartDateAsc(list.stream().map(Subscription::getId).toList()).stream()
                .collect(Collectors.groupingBy(Freeze::getSubscriptionId));
        return list.stream()
                .map(s -> SubscriptionView.of(s, bySubscription.getOrDefault(s.getId(), List.of()), today, money))
                .toList();
    }

    private static Specification<Subscription> spec(SubscriptionFilter filter, LocalDate today,
                                                    Collection<UUID> memberIds) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isNull(root.get("cancelledAt")));
            switch (filter) {
                case ACTIVE -> where.add(running(root, cb, today));
                case EXPIRING -> {
                    where.add(running(root, cb, today));
                    where.add(cb.lessThanOrEqualTo(root.get("endDate"), today.plusDays(EXPIRING_WITHIN_DAYS)));
                }
                case EXPIRED -> {
                    where.add(cb.lessThan(root.get("endDate"), today));
                    where.add(cb.greaterThanOrEqualTo(root.get("endDate"), today.minusDays(EXPIRED_WITHIN_DAYS)));
                }
                case FROZEN -> {
                    Subquery<UUID> frozen = query.subquery(UUID.class);
                    Root<Freeze> freeze = frozen.from(Freeze.class);
                    frozen.select(freeze.get("subscriptionId")).where(
                            cb.lessThanOrEqualTo(freeze.get("startDate"), today),
                            cb.greaterThanOrEqualTo(freeze.get("endDate"), today));
                    where.add(root.get("id").in(frozen));
                }
                case UNPAID -> where.add(cb.lessThan(root.get("paidMillimes"), root.get("amountDueMillimes")));
                case UPCOMING -> where.add(cb.greaterThan(root.get("startDate"), today));
            }
            if (memberIds != null) {
                where.add(root.get("memberId").in(memberIds));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static Predicate running(Root<Subscription> root, jakarta.persistence.criteria.CriteriaBuilder cb,
                                     LocalDate today) {
        return cb.and(cb.lessThanOrEqualTo(root.get("startDate"), today),
                cb.greaterThanOrEqualTo(root.get("endDate"), today));
    }

    public enum SubscriptionFilter {
        /** Running today (frozen ones included). */
        ACTIVE,
        /** Running and ending within 7 days. */
        EXPIRING,
        /** Ended within the last 30 days. */
        EXPIRED,
        /** Paused today. */
        FROZEN,
        /** Not fully paid. */
        UNPAID,
        /** Starting later. */
        UPCOMING
    }

    /** A sale request: amounts in millimes; no start date means "right after the current one". */
    public record Sale(UUID planId, LocalDate startDate, Long discountMillimes, Long paymentMillimes, String note) {
    }
}
