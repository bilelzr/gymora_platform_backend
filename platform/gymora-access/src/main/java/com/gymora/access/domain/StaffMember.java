package com.gymora.access.domain;

import com.gymora.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A gym employee with a login account. Has no Keycloak role: everything they may do comes from
 * the profiles assigned to them here.
 */
@Entity
@Table(name = "access_staff_member")
public class StaffMember extends TenantScopedEntity {

    /** Keycloak user id (the token's {@code sub}). */
    @Column(name = "user_id", nullable = false, updatable = false, length = 64)
    private String userId;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StaffStatus status = StaffStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "access_staff_profile",
            joinColumns = @JoinColumn(name = "staff_id"),
            inverseJoinColumns = @JoinColumn(name = "profile_id"))
    private Set<Profile> profiles = new HashSet<>();

    protected StaffMember() {
    }

    public StaffMember(String userId, String email, String firstName, String lastName, String phone,
                       Collection<Profile> profiles) {
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.profiles.addAll(profiles);
    }

    public void updateDetails(String firstName, String lastName, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public void replaceProfiles(Collection<Profile> profiles) {
        this.profiles.clear();
        this.profiles.addAll(profiles);
    }

    public void disable() {
        this.status = StaffStatus.DISABLED;
    }

    public void enable() {
        this.status = StaffStatus.ACTIVE;
    }

    /** Union of the capabilities of all assigned profiles (regardless of status). */
    public Set<String> grantedCapabilities() {
        Set<String> result = new TreeSet<>();
        profiles.forEach(profile -> result.addAll(profile.getCapabilities()));
        return result;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public StaffStatus getStatus() {
        return status;
    }

    public Set<Profile> getProfiles() {
        return Collections.unmodifiableSet(profiles);
    }
}
