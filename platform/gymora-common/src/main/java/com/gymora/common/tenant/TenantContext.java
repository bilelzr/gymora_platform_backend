package com.gymora.common.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The gym (tenant) the current request or job works for.
 *
 * <p>Set by the security layer from the {@code tenant_id} claim of the Keycloak token and cleared
 * at the end of the request. Never set it from request parameters or bodies: a client must not be
 * able to choose its tenant.
 *
 * <p><b>Set it before a transaction starts.</b> Hibernate fixes a session's tenant when the
 * session opens, at the beginning of the transaction. Changing the tenant inside a running
 * transaction has no effect on that transaction. To work for another gym, call
 * {@link #callAs}/{@link #runAs} around a method that opens its own transaction
 * ({@code @Transactional(propagation = REQUIRES_NEW)} when called from inside one).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The current tenant, or a {@link MissingTenantException} when there is none. */
    public static UUID require() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new MissingTenantException();
        }
        return tenantId;
    }

    public static void set(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null; use clear()");
        }
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs work on behalf of a tenant outside a web request (scheduled jobs, tests), then restores
     * whatever tenant was set before.
     */
    public static <T> T callAs(UUID tenantId, Supplier<T> work) {
        UUID previous = CURRENT.get();
        set(tenantId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(UUID tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
