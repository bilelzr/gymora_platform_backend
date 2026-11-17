package com.gymora.common.persistence;

import com.gymora.common.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base class for every table that belongs to one gym.
 *
 * <p>Hibernate fills {@code tenant_id} from the current tenant on insert and adds
 * {@code where tenant_id = ?} to every query, so module code never handles the tenant id itself.
 * The column can't be updated: a record never moves to another gym.
 *
 * <p>Each table must also declare, in its migration:
 * <pre>
 *   tenant_id uuid not null check (tenant_id &lt;&gt; '00000000-0000-0000-0000-000000000000')
 * </pre>
 */
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    @PrePersist
    void requireTenant() {
        // Refuse to save gym data outside a tenant instead of writing it under the "no tenant" id.
        TenantContext.require();
    }
}
