package com.gymora.tenant.domain;

import com.gymora.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/** A gym subscribed to GYMORA. Its id is the tenant id of all its data. */
@Entity
@Table(name = "tenant_gym")
public class Gym extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GymStatus status = GymStatus.ACTIVE;

    @Column(name = "suspended_reason", length = 500)
    private String suspendedReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "owner_user_id", length = 64)
    private String ownerUserId;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "owner_first_name", nullable = false, length = 100)
    private String ownerFirstName;

    @Column(name = "owner_last_name", nullable = false, length = 100)
    private String ownerLastName;

    protected Gym() {
    }

    public Gym(GymDetails details, String ownerEmail, String ownerFirstName, String ownerLastName) {
        apply(details);
        this.ownerEmail = ownerEmail;
        this.ownerFirstName = ownerFirstName;
        this.ownerLastName = ownerLastName;
    }

    public void apply(GymDetails details) {
        this.name = details.name();
        this.city = details.city();
        this.countryCode = details.countryCode();
        this.phone = details.phone();
        this.email = details.email();
        this.currency = details.currency();
        this.timeZone = details.timeZone();
    }

    public void linkOwnerAccount(String userId) {
        this.ownerUserId = userId;
    }

    public void suspend(String reason, Instant at) {
        this.status = GymStatus.SUSPENDED;
        this.suspendedReason = reason;
        this.suspendedAt = at;
    }

    public void activate() {
        this.status = GymStatus.ACTIVE;
        this.suspendedReason = null;
        this.suspendedAt = null;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public GymStatus getStatus() {
        return status;
    }

    public String getSuspendedReason() {
        return suspendedReason;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public String getOwnerFirstName() {
        return ownerFirstName;
    }

    public String getOwnerLastName() {
        return ownerLastName;
    }

    /** Editable details of a gym. */
    public record GymDetails(String name, String city, String countryCode, String phone, String email,
                             String currency, String timeZone) {
    }
}
