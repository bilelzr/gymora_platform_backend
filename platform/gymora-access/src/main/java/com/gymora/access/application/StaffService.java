package com.gymora.access.application;

import com.gymora.access.domain.Profile;
import com.gymora.access.domain.ProfileRepository;
import com.gymora.access.domain.StaffMember;
import com.gymora.access.domain.StaffRepository;
import com.gymora.access.domain.StaffStatus;
import com.gymora.common.error.BusinessRuleException;
import com.gymora.common.error.NotFoundException;
import com.gymora.common.tenant.TenantContext;
import com.gymora.security.identity.IdentityAdmin;
import com.gymora.security.identity.IdentityAdmin.NewIdentity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    private final StaffRepository staff;
    private final ProfileRepository profiles;
    private final IdentityAdmin identity;
    private final AccessPolicy policy;
    private final StaffCapabilityProvider capabilityCache;

    public StaffService(StaffRepository staff, ProfileRepository profiles, IdentityAdmin identity,
                        AccessPolicy policy, StaffCapabilityProvider capabilityCache) {
        this.staff = staff;
        this.profiles = profiles;
        this.identity = identity;
        this.policy = policy;
        this.capabilityCache = capabilityCache;
    }

    @Transactional(readOnly = true)
    public Page<StaffView> search(String query, StaffStatus status, UUID profileId, Pageable pageable) {
        Specification<StaffMember> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(root.get("phone"), like)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (profileId != null) {
                Join<StaffMember, Profile> profile = root.join("profiles");
                predicates.add(cb.equal(profile.get("id"), profileId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return staff.findAll(spec, pageable).map(StaffView::of);
    }

    @Transactional(readOnly = true)
    public StaffView get(UUID id) {
        return StaffView.of(find(id));
    }

    /**
     * Creates the staff member's login account (no role, linked to this gym) and their record.
     * They receive an email to set their password once the record is saved.
     */
    @Transactional
    public StaffView create(NewStaff request) {
        String email = request.email().trim().toLowerCase();
        if (staff.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.staff_email_taken", email);
        }
        List<Profile> assigned = loadProfiles(request.profileIds());
        policy.requireHoldsAll(capabilitiesOf(assigned));

        String userId = identity.createUser(
                new NewIdentity(email, request.firstName(), request.lastName(), TenantContext.require()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    sendSetupEmailQuietly(userId);
                } else {
                    deleteAccountQuietly(userId);
                }
            }
        });

        StaffMember member = staff.save(new StaffMember(userId, email, request.firstName(), request.lastName(),
                request.phone(), assigned));
        capabilityCache.evictAfterCommit();
        return StaffView.of(member);
    }

    @Transactional
    public StaffView updateDetails(UUID id, String firstName, String lastName, String phone) {
        StaffMember member = find(id);
        policy.requireHoldsAll(member.grantedCapabilities());
        member.updateDetails(firstName, lastName, phone);
        identity.updateNames(member.getUserId(), firstName, lastName);
        return StaffView.of(member);
    }

    @Transactional
    public StaffView replaceProfiles(UUID id, Set<UUID> profileIds) {
        StaffMember member = find(id);
        policy.requireNotSelf(member);
        List<Profile> assigned = loadProfiles(profileIds);
        Set<String> touched = new HashSet<>(member.grantedCapabilities());
        touched.addAll(capabilitiesOf(assigned));
        policy.requireHoldsAll(touched);
        member.replaceProfiles(assigned);
        capabilityCache.evictAfterCommit();
        return StaffView.of(member);
    }

    @Transactional
    public StaffView disable(UUID id) {
        StaffMember member = find(id);
        policy.requireNotSelf(member);
        policy.requireHoldsAll(member.grantedCapabilities());
        member.disable();
        capabilityCache.evictAfterCommit();
        identity.setEnabled(member.getUserId(), false);
        return StaffView.of(member);
    }

    @Transactional
    public StaffView enable(UUID id) {
        StaffMember member = find(id);
        policy.requireNotSelf(member);
        policy.requireHoldsAll(member.grantedCapabilities());
        member.enable();
        capabilityCache.evictAfterCommit();
        identity.setEnabled(member.getUserId(), true);
        return StaffView.of(member);
    }

    @Transactional(readOnly = true)
    public void resendInvitation(UUID id) {
        StaffMember member = find(id);
        policy.requireHoldsAll(member.grantedCapabilities());
        identity.sendSetupEmail(member.getUserId());
    }

    private StaffMember find(UUID id) {
        return staff.findById(id).orElseThrow(() -> new NotFoundException("staff member", id));
    }

    /** Profiles of the current gym; an id from another gym is reported as not found. */
    private List<Profile> loadProfiles(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Profile> found = profiles.findAllByIdIn(ids);
        if (found.size() != ids.size()) {
            Set<UUID> missing = new HashSet<>(ids);
            found.forEach(profile -> missing.remove(profile.getId()));
            throw new NotFoundException("profile", missing.iterator().next());
        }
        return found;
    }

    private static Set<String> capabilitiesOf(List<Profile> assigned) {
        Set<String> result = new HashSet<>();
        assigned.forEach(profile -> result.addAll(profile.getCapabilities()));
        return result;
    }

    private void sendSetupEmailQuietly(String userId) {
        try {
            identity.sendSetupEmail(userId);
        } catch (RuntimeException e) {
            log.warn("Could not send the setup email to new staff member {}: {}", userId, e.getMessage());
        }
    }

    private void deleteAccountQuietly(String userId) {
        try {
            identity.deleteUser(userId);
        } catch (RuntimeException e) {
            log.error("Staff creation failed and account {} could not be removed from Keycloak; remove it manually",
                    userId, e);
        }
    }

    public record NewStaff(String email, String firstName, String lastName, String phone, Set<UUID> profileIds) {
    }
}
