package com.gymora.access.application;

import com.gymora.access.domain.Profile;
import com.gymora.access.domain.ProfileRepository;
import com.gymora.access.domain.StaffRepository;
import com.gymora.common.error.BusinessRuleException;
import com.gymora.common.error.NotFoundException;
import com.gymora.security.capability.CapabilityCatalog;
import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final StaffRepository staff;
    private final CapabilityCatalog catalog;
    private final AccessPolicy policy;
    private final StaffCapabilityProvider capabilityCache;

    public ProfileService(ProfileRepository profiles, StaffRepository staff, CapabilityCatalog catalog,
                          AccessPolicy policy, StaffCapabilityProvider capabilityCache) {
        this.profiles = profiles;
        this.staff = staff;
        this.catalog = catalog;
        this.policy = policy;
        this.capabilityCache = capabilityCache;
    }

    @Transactional(readOnly = true)
    public List<ProfileView> list() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : profiles.countStaffPerProfile()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        // Sorted in Java with the caller's language rules: the database orders by character code,
        // which puts "Réceptionniste" after "Responsable".
        Collator collator = Collator.getInstance(LocaleContextHolder.getLocale());
        collator.setStrength(Collator.SECONDARY);
        return profiles.findAll().stream()
                .sorted(Comparator.comparing(Profile::getName, collator))
                .map(profile -> ProfileView.of(profile, counts.getOrDefault(profile.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileView get(UUID id) {
        Profile profile = find(id);
        long assigned = profiles.countStaffPerProfile().stream()
                .filter(row -> id.equals(row[0])).mapToLong(row -> (Long) row[1]).sum();
        return ProfileView.of(profile, assigned);
    }

    @Transactional
    public ProfileView create(String name, String description, Set<String> capabilities) {
        requireKnown(capabilities);
        policy.requireHoldsAll(capabilities);
        if (profiles.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.profile_name_taken", name);
        }
        Profile profile = profiles.save(new Profile(name, description, capabilities, null));
        return ProfileView.of(profile, 0);
    }

    @Transactional
    public ProfileView rename(UUID id, String name, String description) {
        Profile profile = find(id);
        policy.requireHoldsAll(profile.getCapabilities());
        if (profiles.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.profile_name_taken", name);
        }
        profile.rename(name, description);
        return get(id);
    }

    @Transactional
    public ProfileView replaceCapabilities(UUID id, Set<String> capabilities) {
        Profile profile = find(id);
        requireKnown(capabilities);
        Set<String> touched = new HashSet<>(profile.getCapabilities());
        touched.addAll(capabilities);
        policy.requireHoldsAll(touched);
        profile.replaceCapabilities(capabilities);
        capabilityCache.evictAfterCommit();
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        Profile profile = find(id);
        policy.requireHoldsAll(profile.getCapabilities());
        if (staff.isProfileAssigned(id)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "error.profile_in_use");
        }
        profiles.delete(profile);
    }

    Profile find(UUID id) {
        return profiles.findById(id).orElseThrow(() -> new NotFoundException("profile", id));
    }

    private void requireKnown(Set<String> capabilities) {
        Set<String> unknown = catalog.unknown(capabilities);
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "error.capability_unknown", String.join(", ", unknown));
        }
    }
}
