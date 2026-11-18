package com.gymora.subscription.domain;

import com.gymora.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/** Something a gym sells: a length, a price and freeze rules. Archived instead of deleted. */
@Entity
@Table(name = "subscription_plan")
public class Plan extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "duration_value", nullable = false)
    private int durationValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", nullable = false, length = 10)
    private DurationUnit durationUnit;

    @Column(name = "price_millimes", nullable = false)
    private long priceMillimes;

    @Column(name = "session_count")
    private Integer sessionCount;

    @Column(name = "max_freeze_days", nullable = false)
    private int maxFreezeDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status = PlanStatus.ACTIVE;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Plan() {
    }

    public Plan(PlanDetails details) {
        apply(details);
    }

    public void apply(PlanDetails details) {
        this.name = details.name();
        this.description = details.description();
        this.durationValue = details.durationValue();
        this.durationUnit = details.durationUnit();
        this.priceMillimes = details.priceMillimes();
        this.sessionCount = details.sessionCount();
        this.maxFreezeDays = details.maxFreezeDays();
    }

    public void archive(Instant at) {
        this.status = PlanStatus.ARCHIVED;
        this.archivedAt = at;
    }

    public void reactivate() {
        this.status = PlanStatus.ACTIVE;
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return status == PlanStatus.ARCHIVED;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationValue() {
        return durationValue;
    }

    public DurationUnit getDurationUnit() {
        return durationUnit;
    }

    public long getPriceMillimes() {
        return priceMillimes;
    }

    public Integer getSessionCount() {
        return sessionCount;
    }

    public int getMaxFreezeDays() {
        return maxFreezeDays;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public record PlanDetails(String name, String description, int durationValue, DurationUnit durationUnit,
                              long priceMillimes, Integer sessionCount, int maxFreezeDays) {
    }
}
