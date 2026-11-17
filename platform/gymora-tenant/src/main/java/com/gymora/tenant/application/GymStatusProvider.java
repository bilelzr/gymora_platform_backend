package com.gymora.tenant.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gymora.security.tenant.TenantStatusProvider;
import com.gymora.tenant.domain.GymRepository;
import com.gymora.tenant.domain.GymStatus;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Gym status for the security filter, checked on every request. Cached briefly; suspending or
 * reactivating a gym evicts its entry immediately on this instance.
 * (With several API instances, the other instances catch up within the cache lifetime.)
 */
@Component
public class GymStatusProvider implements TenantStatusProvider {

    private final GymRepository gyms;
    private final Cache<UUID, TenantStatus> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    public GymStatusProvider(GymRepository gyms) {
        this.gyms = gyms;
    }

    @Override
    public TenantStatus statusOf(UUID tenantId) {
        return cache.get(tenantId, id -> gyms.findStatusById(id)
                .map(status -> status == GymStatus.ACTIVE ? TenantStatus.ACTIVE : TenantStatus.SUSPENDED)
                .orElse(TenantStatus.UNKNOWN));
    }

    public void evict(UUID tenantId) {
        cache.invalidate(tenantId);
    }
}
