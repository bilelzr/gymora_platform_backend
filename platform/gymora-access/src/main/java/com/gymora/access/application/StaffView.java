package com.gymora.access.application;

import com.gymora.access.domain.StaffMember;
import com.gymora.access.domain.StaffStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record StaffView(UUID id, String userId, String email, String firstName, String lastName, String phone,
                        StaffStatus status, List<ProfileRef> profiles, Set<String> capabilities,
                        long version, Instant createdAt) {

    static StaffView of(StaffMember member) {
        List<ProfileRef> profiles = member.getProfiles().stream()
                .map(profile -> new ProfileRef(profile.getId(), profile.getName()))
                .sorted(Comparator.comparing(ProfileRef::name))
                .toList();
        return new StaffView(member.getId(), member.getUserId(), member.getEmail(), member.getFirstName(),
                member.getLastName(), member.getPhone(), member.getStatus(), profiles,
                member.getStatus() == StaffStatus.ACTIVE ? member.grantedCapabilities() : Set.of(),
                member.getVersion(), member.getCreatedAt());
    }

    public record ProfileRef(UUID id, String name) {
    }
}
