package com.gymora.subscription.domain;

import com.gymora.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** A pause in a subscription, both dates included. */
@Entity
@Table(name = "subscription_freeze")
public class Freeze extends TenantScopedEntity {

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "days", nullable = false)
    private int days;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Column(name = "ended_early", nullable = false)
    private boolean endedEarly;

    protected Freeze() {
    }

    public Freeze(UUID subscriptionId, LocalDate startDate, LocalDate endDate, String reason) {
        this.subscriptionId = subscriptionId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = daysBetween(startDate, endDate);
        this.reason = reason;
    }

    /** The member is back: the freeze now ends on {@code lastFrozenDay}. Returns the days given back. */
    public int endOn(LocalDate lastFrozenDay) {
        int before = days;
        this.endDate = lastFrozenDay;
        this.days = daysBetween(startDate, lastFrozenDay);
        this.endedEarly = true;
        return before - days;
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    public static int daysBetween(LocalDate first, LocalDate last) {
        return (int) ChronoUnit.DAYS.between(first, last) + 1;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getDays() {
        return days;
    }

    public String getReason() {
        return reason;
    }

    public boolean isEndedEarly() {
        return endedEarly;
    }
}
