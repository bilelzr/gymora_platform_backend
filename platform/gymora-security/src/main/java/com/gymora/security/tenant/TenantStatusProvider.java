package com.gymora.security.tenant;

import java.util.UUID;

/**
 * Tells the security layer whether a gym may use the platform. Implemented by the tenant module.
 * Requests for suspended or unknown gyms are refused before reaching any endpoint.
 */
public interface TenantStatusProvider {

    TenantStatus statusOf(UUID tenantId);

    enum TenantStatus {
        ACTIVE,
        SUSPENDED,
        UNKNOWN
    }
}
