package com.gymora.access.application;

import com.gymora.security.capability.DefaultProfileGrants;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Capabilities of the access module, and what the starting profiles get. */
@Component
public class AccessCapabilities implements DefaultProfileGrants {

    public static final String PROFILE_READ = "access.profile.read";
    public static final String PROFILE_MANAGE = "access.profile.manage";
    public static final String STAFF_READ = "access.staff.read";
    public static final String STAFF_MANAGE = "access.staff.manage";

    @Override
    public Map<DefaultProfile, Set<String>> grants() {
        return Map.of(DefaultProfile.MANAGER, Set.of(PROFILE_READ, STAFF_READ, STAFF_MANAGE));
    }
}
