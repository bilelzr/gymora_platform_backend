package com.gymora.app.probe;

import com.gymora.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Test-only tenant-scoped entity used to prove isolation between gyms. */
@Entity
@Table(name = "test_probe")
public class ProbeEntity extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected ProbeEntity() {
    }

    public ProbeEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
