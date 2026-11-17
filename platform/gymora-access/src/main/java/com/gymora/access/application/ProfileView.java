package com.gymora.access.application;

import com.gymora.access.domain.Profile;
import com.gymora.security.capability.DefaultProfileGrants.DefaultProfile;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record ProfileView(UUID id, String name, String description, DefaultProfile defaultKey,
                          Set<String> capabilities, long assignedStaff, long version, Instant updatedAt) {

    static ProfileView of(Profile profile, long assignedStaff) {
        return new ProfileView(profile.getId(), profile.getName(), profile.getDescription(), profile.getDefaultKey(),
                new TreeSet<>(profile.getCapabilities()), assignedStaff, profile.getVersion(), profile.getUpdatedAt());
    }
}
