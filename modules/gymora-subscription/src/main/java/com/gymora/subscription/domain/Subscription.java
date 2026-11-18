package com.gymora.subscription.domain;

import com.gymora.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A plan sold to a member for a period. Keeps a copy of the plan as it was when sold, so later
 * changes to the plan (price, length, freeze days) never rewrite what the member bought.
 */
@Entity
@Table(name = "subscription_subscription")
public class Subscription extends TenantScopedEntity {

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "plan_name", nullable = false, updatable = false, length = 100)
    private String planName;

    @Column(name = "duration_value", nullable = false, updatable = false)
    private int durationValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", nullable = false, updatable = false, length = 10)
    private DurationUnit durationUnit;

    @Column(name = "session_count", updatable = false)
    private Integer sessionCount;

    @Column(name = "max_freeze_days", nullable = false, updatable = false)
    private int maxFreezeDays;

    @Column(name = "price_millimes", nullable = false, updatable = false)
    private long priceMillimes;

    @Column(name = "discount_millimes", nullable = false, updatable = false)
    private long discountMillimes;

    @Column(name = "amount_due_millimes", nullable = false, updatable = false)
    private long amountDueMillimes;

    @Column(name = "paid_millimes", nullable = false)
    private long paidMillimes;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "base_end_date", nullable = false)
    private LocalDate baseEndDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "frozen_days", nullable = false)
    private int frozenDays;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_on")
    private LocalDate cancelledOn;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by_name", length = 150)
    private String cancelledByName;

    protected Subscription() {
    }

    public Subscription(UUID memberId, Plan plan, LocalDate startDate, long discountMillimes) {
        this.memberId = memberId;
        this.planId = plan.getId();
        this.planName = plan.getName();
        this.durationValue = plan.getDurationValue();
        this.durationUnit = plan.getDurationUnit();
        this.sessionCount = plan.getSessionCount();
        this.maxFreezeDays = plan.getMaxFreezeDays();
        this.priceMillimes = plan.getPriceMillimes();
        this.discountMillimes = discountMillimes;
        this.amountDueMillimes = plan.getPriceMillimes() - discountMillimes;
        this.startDate = startDate;
        this.baseEndDate = plan.getDurationUnit().lastDay(startDate, plan.getDurationValue());
        this.endDate = baseEndDate;
    }

    /** Status on a gym day. {@code frozenThatDay}: a freeze covers that day. */
    public SubscriptionStatus statusOn(LocalDate day, boolean frozenThatDay) {
        if (isCancelled()) {
            return SubscriptionStatus.CANCELLED;
        }
        if (day.isBefore(startDate)) {
            return SubscriptionStatus.UPCOMING;
        }
        if (day.isAfter(endDate)) {
            return SubscriptionStatus.EXPIRED;
        }
        return frozenThatDay ? SubscriptionStatus.FROZEN : SubscriptionStatus.ACTIVE;
    }

    /** Freeze days added (positive) or given back (negative): the end date moves with them. */
    public void addFrozenDays(int days) {
        this.frozenDays += days;
        this.endDate = baseEndDate.plusDays(frozenDays);
    }

    /** Moves the whole period, for a renewal queued after a subscription that was frozen. */
    public void shift(int days) {
        this.startDate = startDate.plusDays(days);
        this.baseEndDate = baseEndDate.plusDays(days);
        this.endDate = endDate.plusDays(days);
    }

    public void cancel(Instant at, LocalDate on, String reason, String byName) {
        this.cancelledAt = at;
        this.cancelledOn = on;
        this.cancelReason = reason;
        this.cancelledByName = byName;
    }

    public void paymentReceived(long millimes) {
        this.paidMillimes += millimes;
    }

    public void paymentVoided(long millimes) {
        this.paidMillimes -= millimes;
    }

    public long remainingMillimes() {
        return amountDueMillimes - paidMillimes;
    }

    public int freezeDaysLeft() {
        return Math.max(0, maxFreezeDays - frozenDays);
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public int getDurationValue() {
        return durationValue;
    }

    public DurationUnit getDurationUnit() {
        return durationUnit;
    }

    public Integer getSessionCount() {
        return sessionCount;
    }

    public int getMaxFreezeDays() {
        return maxFreezeDays;
    }

    public long getPriceMillimes() {
        return priceMillimes;
    }

    public long getDiscountMillimes() {
        return discountMillimes;
    }

    public long getAmountDueMillimes() {
        return amountDueMillimes;
    }

    public long getPaidMillimes() {
        return paidMillimes;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getFrozenDays() {
        return frozenDays;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public LocalDate getCancelledOn() {
        return cancelledOn;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public String getCancelledByName() {
        return cancelledByName;
    }
}
