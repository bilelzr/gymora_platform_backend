package com.gymora.access.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gymora.access.domain.StaffRepository;
import com.gymora.common.tenant.TenantContext;
import com.gymora.security.capability.CapabilityProvider;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Resolves a staff member's capabilities on every request, from their profiles. Cached for a few
 * minutes; any change to profiles or staff clears the cache when its transaction commits.
 * (With several API instances, move this cache to Redis so all instances see changes at once.)
 */
@Component
public class StaffCapabilityProvider implements CapabilityProvider {

    private final StaffRepository staff;
    private final Cache<Key, Set<String>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(50_000)
            .build();

    public StaffCapabilityProvider(StaffRepository staff) {
        this.staff = staff;
    }

    @Override
    public Set<String> capabilitiesOf(String userId, UUID tenantId) {
        // Runs during authentication, before any transaction: the tenant is set for the query's own session.
        return cache.get(new Key(userId, tenantId),
                key -> Set.copyOf(TenantContext.callAs(tenantId, () -> staff.activeCapabilitiesOf(userId))));
    }

    /** Clears cached capabilities once the current transaction commits. */
    public void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.invalidateAll();
                }
            });
        } else {
            cache.invalidateAll();
        }
    }

    private record Key(String userId, UUID tenantId) {
    }
}
