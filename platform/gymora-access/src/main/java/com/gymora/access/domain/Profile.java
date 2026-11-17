package com.gymora.access.domain;

import com.gymora.common.persistence.TenantScopedEntity;
import com.gymora.security.capability.DefaultProfileGrants.DefaultProfile;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A named set of capabilities in one gym, assigned to staff members. */
@Entity
@Table(name = "access_profile")
public class Profile extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    /** Set when the profile was created from one of the starting templates. */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_key", length = 20)
    private DefaultProfile defaultKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "access_profile_capability", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "capability", nullable = false, length = 120)
    private Set<String> capabilities = new HashSet<>();

    protected Profile() {
    }

    public Profile(String name, String description, Set<String> capabilities, DefaultProfile defaultKey) {
        this.name = name;
        this.description = description;
        this.defaultKey = defaultKey;
        this.capabilities.addAll(capabilities);
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void replaceCapabilities(Set<String> capabilities) {
        this.capabilities.clear();
        this.capabilities.addAll(capabilities);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public DefaultProfile getDefaultKey() {
        return defaultKey;
    }

    public Set<String> getCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }
}
