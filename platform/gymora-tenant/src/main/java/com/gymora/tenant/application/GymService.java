package com.gymora.tenant.application;

import com.gymora.common.error.NotFoundException;
import com.gymora.security.GymoraRoles;
import com.gymora.security.identity.IdentityAdmin;
import com.gymora.security.identity.IdentityAdmin.NewIdentity;
import com.gymora.tenant.GymCreatedEvent;
import com.gymora.tenant.domain.Gym;
import com.gymora.tenant.domain.Gym.GymDetails;
import com.gymora.tenant.domain.GymRepository;
import com.gymora.tenant.domain.GymStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GymService {

    private static final Logger log = LoggerFactory.getLogger(GymService.class);

    private final GymRepository gyms;
    private final IdentityAdmin identity;
    private final ApplicationEventPublisher events;
    private final GymStatusProvider statusProvider;
    private final Clock clock;

    public GymService(GymRepository gyms, IdentityAdmin identity, ApplicationEventPublisher events,
                      GymStatusProvider statusProvider, Clock clock) {
        this.gyms = gyms;
        this.identity = identity;
        this.events = events;
        this.statusProvider = statusProvider;
        this.clock = clock;
    }

    /**
     * Creates the gym, its owner's login account (GYM_OWNER, linked to the gym) and lets other
     * modules seed their per-gym data. The owner receives a setup email once everything is saved;
     * if anything fails, the login account is removed again.
     */
    @Transactional
    public Gym create(GymDetails details, Owner owner) {
        Gym gym = gyms.save(new Gym(details, owner.email(), owner.firstName(), owner.lastName()));

        String ownerUserId = identity.createUser(
                new NewIdentity(owner.email(), owner.firstName(), owner.lastName(), gym.getId()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    sendSetupEmailQuietly(ownerUserId);
                } else {
                    deleteAccountQuietly(ownerUserId);
                }
            }
        });

        identity.grantRealmRole(ownerUserId, GymoraRoles.GYM_OWNER);
        gym.linkOwnerAccount(ownerUserId);
        events.publishEvent(new GymCreatedEvent(gym.getId()));
        return gym;
    }

    @Transactional(readOnly = true)
    public Gym get(UUID id) {
        return gyms.findById(id).orElseThrow(() -> new NotFoundException("gym", id));
    }

    @Transactional(readOnly = true)
    public Page<Gym> search(String query, GymStatus status, Pageable pageable) {
        Specification<Gym> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("city")), like),
                        cb.like(cb.lower(root.get("ownerEmail")), like)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return gyms.findAll(spec, pageable);
    }

    @Transactional
    public Gym update(UUID id, GymDetails details) {
        Gym gym = get(id);
        gym.apply(details);
        return gym;
    }

    @Transactional
    public Gym suspend(UUID id, String reason) {
        Gym gym = get(id);
        gym.suspend(reason, clock.instant());
        evictAfterCommit(id);
        return gym;
    }

    @Transactional
    public Gym activate(UUID id) {
        Gym gym = get(id);
        gym.activate();
        evictAfterCommit(id);
        return gym;
    }

    /** Sends the owner a new setup link, for example when the first one expired. */
    @Transactional(readOnly = true)
    public void resendOwnerInvitation(UUID id) {
        Gym gym = get(id);
        if (gym.getOwnerUserId() == null) {
            throw new NotFoundException("gym owner account", id);
        }
        identity.sendSetupEmail(gym.getOwnerUserId());
    }

    private void evictAfterCommit(UUID id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                statusProvider.evict(id);
            }
        });
    }

    private void sendSetupEmailQuietly(String userId) {
        try {
            identity.sendSetupEmail(userId);
        } catch (RuntimeException e) {
            // The gym exists; the platform admin can resend the invitation.
            log.warn("Could not send the setup email to new gym owner {}: {}", userId, e.getMessage());
        }
    }

    private void deleteAccountQuietly(String userId) {
        try {
            identity.deleteUser(userId);
        } catch (RuntimeException e) {
            log.error("Gym creation failed and owner account {} could not be removed from Keycloak; remove it manually",
                    userId, e);
        }
    }

    public record Owner(String email, String firstName, String lastName) {
    }
}
