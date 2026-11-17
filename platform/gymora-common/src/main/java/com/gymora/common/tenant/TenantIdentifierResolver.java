package com.gymora.common.tenant;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which tenant the current work belongs to.
 *
 * <p>Every entity extending {@link com.gymora.common.persistence.TenantScopedEntity} has a
 * {@code @TenantId} column, so Hibernate adds {@code where tenant_id = ?} to its queries and fills
 * the column on insert. With no tenant in the context we return {@link #NO_TENANT}, a value that
 * matches no rows: tenant data is simply invisible rather than visible to everyone.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /** Matches no tenant. Tables also reject it with a CHECK constraint. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.current().orElse(NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        // No "see every tenant" mode. Cross-tenant platform features must use their own queries.
        return false;
    }
}
