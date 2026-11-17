package com.gymora.security.capability;

import java.util.Map;
import java.util.Set;

/**
 * A module's contribution to the profiles every new gym starts with.
 *
 * <p>Each business module declares which of its capabilities a Manager, a Receptionist and a
 * Trainer get by default. When a gym is created, the access module merges every contribution into
 * the three starting profiles. Gyms can change them afterwards.
 */
public interface DefaultProfileGrants {

    Map<DefaultProfile, Set<String>> grants();

    enum DefaultProfile {
        MANAGER,
        RECEPTIONIST,
        TRAINER
    }
}
