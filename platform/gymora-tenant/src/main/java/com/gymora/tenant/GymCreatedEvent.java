package com.gymora.tenant;

import java.util.UUID;

/**
 * Published when a gym is created. Modules prepare their per-gym data here (the access module
 * seeds the default profiles).
 *
 * <p>Listen with {@code @TransactionalEventListener} (after commit) and do the work in a new
 * transaction opened under {@code TenantContext.runAs(gymId, ...)}: the creating transaction
 * belongs to the platform admin and has no tenant. Make the work idempotent so it can be re-run.
 */
public record GymCreatedEvent(UUID gymId) {
}
